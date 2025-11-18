package co.qwex.chickenapi.ai

import co.qwex.chickenapi.model.ChickenFactResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Client for calling Ollama's OpenAI-compatible API with structured output support.
 * Uses the `response_format` parameter to enforce JSON schema.
 */
class OpenAICompatibleClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val model: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * JSON schema for ChickenFactResponse
     */
    private val chickenFactSchema = buildJsonObject {
        put("type", JsonPrimitive("json_schema"))
        putJsonObject("json_schema") {
            put("name", JsonPrimitive("chicken_fact_response"))
            put("strict", JsonPrimitive(true))
            putJsonObject("schema") {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("fact") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("A single, cool, recent fact about chickens"))
                    }
                    putJsonObject("sourceUrl") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The URL of the source used for this fact"))
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("fact"))
                    add(JsonPrimitive("sourceUrl"))
                }
                put("additionalProperties", JsonPrimitive(false))
            }
        }
    }

    suspend fun chatCompletion(messages: List<Message>): ChickenFactResponse? {
        return try {
            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                responseFormat = chickenFactSchema,
            )

            log.info { "Calling OpenAI-compatible API at $baseUrl/v1/chat/completions with format schema" }

            val response = httpClient.post("$baseUrl/v1/chat/completions") {
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                log.error { "OpenAI-compatible API call failed with status ${response.status}" }
                return null
            }

            val chatResponse: ChatCompletionResponse = response.body()
            val content = chatResponse.choices.firstOrNull()?.message?.content

            if (content.isNullOrBlank()) {
                log.warn { "Empty response from OpenAI-compatible API" }
                return null
            }

            log.info { "Received structured response: $content" }

            // Parse the JSON response into ChickenFactResponse
            json.decodeFromString<ChickenFactResponse>(content)
        } catch (ex: Exception) {
            log.error(ex) { "Failed to call OpenAI-compatible API" }
            null
        }
    }
}

@Serializable
data class Message(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("response_format")
    val responseFormat: JsonObject,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val message: Message,
)
