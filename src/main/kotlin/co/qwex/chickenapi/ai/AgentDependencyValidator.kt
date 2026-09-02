package co.qwex.chickenapi.ai

import co.qwex.chickenapi.ai.tools.WebSearchTool
import co.qwex.chickenapi.config.KoogOllamaProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Component
class AgentDependencyValidator(
    private val ollamaProperties: KoogOllamaProperties,
) {
    suspend fun requireModelsAvailable(
        client: HttpClient,
        models: Collection<String>,
        baseUrl: String = ollamaProperties.normalizedBaseUrl,
    ) {
        val response = client.get("$baseUrl/v1/models") {
            ollamaProperties.resolvedGenerationApiKey?.takeIf { it.isNotBlank() }?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
            timeout {
                requestTimeoutMillis = ollamaProperties.readinessRequestTimeout.toMillis()
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = ollamaProperties.readinessRequestTimeout.toMillis()
            }
        }
        check(response.status.isSuccess()) {
            "Gateway model discovery failed with status ${response.status}"
        }
        val availableModels = response.body<OpenAiModelsResponse>().data
            .map { normalizeModelName(it.id) }
            .toSet()
        val missingModels = models
            .map(::normalizeModelName)
            .filterNot(availableModels::contains)
        check(missingModels.isEmpty()) {
            "Required gateway models are unavailable: ${missingModels.joinToString()}"
        }
    }

    suspend fun requireGeneration(client: HttpClient, model: String) {
        val response = client.post("${ollamaProperties.normalizedBaseUrl}/v1/chat/completions") {
            ollamaProperties.resolvedGenerationApiKey?.takeIf { it.isNotBlank() }?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = ollamaProperties.readinessRequestTimeout.toMillis()
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = ollamaProperties.readinessRequestTimeout.toMillis()
            }
            setBody(
                OpenAiChatProbeRequest(
                    model = model,
                    messages = listOf(OpenAiChatProbeMessage(content = "Reply with OK.")),
                    stream = false,
                    maxTokens = 2,
                ),
            )
        }
        check(response.status.isSuccess()) {
            "Gateway generation probe failed with status ${response.status}"
        }
        val result = response.body<OpenAiChatProbeResponse>()
        check(result.error == null) {
            "Gateway generation probe failed: ${result.error?.message}"
        }
        check(result.choices.firstOrNull() != null) {
            "Gateway generation probe returned no assistant message"
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
private data class OpenAiModelsResponse(
    @kotlinx.serialization.SerialName("data")
    val data: List<OpenAiModel> = emptyList(),
)

@Serializable
private data class OpenAiModel(
    val id: String,
)

@Serializable
private data class OpenAiChatProbeRequest(
    val model: String,
    val messages: List<OpenAiChatProbeMessage>,
    val stream: Boolean,
    @kotlinx.serialization.SerialName("max_tokens")
    val maxTokens: Int,
)

@Serializable
private data class OpenAiChatProbeMessage(
    val role: String = "user",
    val content: String,
)

@Serializable
private data class OpenAiChatProbeResponse(
    val choices: List<OpenAiChatProbeChoice> = emptyList(),
    val error: OpenAiChatProbeError? = null,
)

@Serializable
private data class OpenAiChatProbeChoice(
    val message: OpenAiChatProbeMessage? = null,
)

@Serializable
private data class OpenAiChatProbeError(
    val message: String? = null,
)
