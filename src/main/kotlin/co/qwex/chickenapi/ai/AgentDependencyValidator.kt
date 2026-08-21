package co.qwex.chickenapi.ai

import co.qwex.chickenapi.ai.tools.WebSearchTool
import co.qwex.chickenapi.config.KoogOllamaProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Component
class AgentDependencyValidator(
    private val ollamaProperties: KoogOllamaProperties,
) {
    suspend fun requireModels(
        client: HttpClient,
        models: Collection<String>,
        baseUrl: String = ollamaProperties.normalizedBaseUrl,
    ) {
        val response = client.get("$baseUrl/api/tags") {
            timeout {
                requestTimeoutMillis = ollamaProperties.readinessRequestTimeout.toMillis()
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = ollamaProperties.readinessRequestTimeout.toMillis()
            }
        }
        check(response.status.isSuccess()) {
            "Ollama model discovery failed with status ${response.status}"
        }
        val availableModels = response.body<OllamaTagsResponse>().models
            .map { normalizeModelName(it.name) }
            .toSet()
        val missingModels = models
            .map(::normalizeModelName)
            .filterNot(availableModels::contains)
        check(missingModels.isEmpty()) {
            "Required Ollama models are unavailable: ${missingModels.joinToString()}"
        }
    }

    suspend fun requireGeneration(client: HttpClient, model: String) {
        val response = client.post("${ollamaProperties.normalizedBaseUrl}/api/chat") {
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = ollamaProperties.readinessRequestTimeout.toMillis()
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = ollamaProperties.readinessRequestTimeout.toMillis()
            }
            setBody(
                OllamaChatProbeRequest(
                    model = model,
                    messages = listOf(OllamaChatProbeMessage(content = "Reply with OK.")),
                    stream = false,
                    options = OllamaChatProbeOptions(numPredict = 2),
                ),
            )
        }
        check(response.status.isSuccess()) {
            "Ollama generation probe failed with status ${response.status}"
        }
        val result = response.body<OllamaChatProbeResponse>()
        check(result.error.isNullOrBlank()) {
            "Ollama generation probe failed: ${result.error}"
        }
        check(result.message != null) {
            "Ollama generation probe returned no assistant message"
        }
    }

    suspend fun requireWebSearch(client: HttpClient) {
        val output = WebSearchTool(
            httpClient = client,
            baseUrl = ollamaProperties.normalizedWebToolsBaseUrl,
            defaultMaxResults = 1,
            provider = ollamaProperties.webToolsProvider,
        ).execute(WebSearchTool.Args(query = "chicken", maxResults = 1))
        check("Result 1:" in output) {
            "Web search readiness query returned no results"
        }
    }

    private fun normalizeModelName(model: String): String =
        model.trim().removeSuffix(":latest")
}

@Serializable
private data class OllamaTagsResponse(
    val models: List<OllamaModel> = emptyList(),
)

@Serializable
private data class OllamaModel(
    val name: String,
)

@Serializable
private data class OllamaChatProbeRequest(
    val model: String,
    val messages: List<OllamaChatProbeMessage>,
    val stream: Boolean,
    val options: OllamaChatProbeOptions,
)

@Serializable
private data class OllamaChatProbeMessage(
    val role: String = "user",
    val content: String,
)

@Serializable
private data class OllamaChatProbeOptions(
    @kotlinx.serialization.SerialName("num_predict")
    val numPredict: Int,
)

@Serializable
private data class OllamaChatProbeResponse(
    val message: OllamaChatProbeMessage? = null,
    val error: String? = null,
)
