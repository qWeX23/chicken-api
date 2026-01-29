package co.qwex.chickenapi.ai

import co.qwex.chickenapi.config.KoogAgentProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import jakarta.annotation.PreDestroy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class OllamaEmbeddingService(
    private val properties: KoogAgentProperties,
) {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val sanitizedBaseUrl =
        properties.baseUrl.trim().removeSuffix("/").removeSuffix("/api")
    private val httpClient = buildHttpClient()

    fun isReady(): Boolean = httpClient != null

    suspend fun embedFact(fact: String): List<Double>? {
        val client = httpClient ?: run {
            log.warn { "Embedding client unavailable; missing API key or not configured." }
            return null
        }

        val response =
            client.post("$sanitizedBaseUrl/api/embeddings") {
                setBody(
                    EmbeddingRequest(
                        model = properties.embeddingModel,
                        prompt = fact,
                    ),
                )
            }

        if (!response.status.isSuccess()) {
            log.warn { "Embedding request failed with status ${response.status}" }
            return null
        }

        val result: EmbeddingResponse = response.body()
        if (result.embedding.isEmpty()) {
            log.warn { "Embedding response returned empty vector." }
        }
        return result.embedding.takeIf { it.isNotEmpty() }
    }

    @PreDestroy
    fun shutdown() {
        httpClient?.close()
    }

    private fun buildHttpClient(): HttpClient? {
        val apiKey = properties.apiKey?.takeIf { it.isNotBlank() } ?: return null
        return HttpClient(CIO) {
            defaultRequest {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
}

@Serializable
private data class EmbeddingRequest(
    val model: String,
    val prompt: String,
)

@Serializable
private data class EmbeddingResponse(
    @SerialName("embedding")
    val embedding: List<Double> = emptyList(),
)
