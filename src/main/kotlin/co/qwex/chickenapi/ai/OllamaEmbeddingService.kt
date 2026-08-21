package co.qwex.chickenapi.ai

import co.qwex.chickenapi.config.KoogOllamaProperties
import co.qwex.chickenapi.config.KoogAgentProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
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
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class OllamaEmbeddingService(
    private val ollamaProperties: KoogOllamaProperties,
    private val agentProperties: KoogAgentProperties,
    private val dependencyValidator: AgentDependencyValidator,
) {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val sanitizedBaseUrl = ollamaProperties.normalizedEmbeddingBaseUrl
    private val httpClient = buildHttpClient()
    @Volatile
    private var ready = false

    @Volatile
    private var nextInitializationAttemptMillis = 0L

    @PostConstruct
    fun initialize() {
        if (!agentProperties.enabled) {
            return
        }
        initializeIfDue()
    }

    @Scheduled(fixedDelayString = "\${koog.ollama.embedding-readiness-retry-delay:PT1M}")
    fun retryInitialization() {
        if (agentProperties.enabled && !ready) {
            initializeIfDue()
        }
    }

    @Synchronized
    private fun initializeIfDue() {
        if (ready || System.currentTimeMillis() < nextInitializationAttemptMillis) {
            return
        }
        ready = try {
            runBlocking {
                dependencyValidator.requireModels(
                    client = httpClient,
                    models = listOf(ollamaProperties.embeddingModel),
                    baseUrl = sanitizedBaseUrl,
                )
            }
            true
        } catch (ex: Exception) {
            log.error(ex) { "Ollama embedding dependency is not ready" }
            nextInitializationAttemptMillis = System.currentTimeMillis() + RETRY_DELAY_MILLIS
            false
        }
    }

    fun isReady(): Boolean {
        if (agentProperties.enabled && !ready) {
            initializeIfDue()
        }
        return ready
    }

    suspend fun embedFact(fact: String): List<Double>? {
        if (!ready) {
            log.warn { "Embedding client is not ready." }
            return null
        }

        val response = try {
            httpClient.post("$sanitizedBaseUrl/api/embeddings") {
                setBody(
                    EmbeddingRequest(
                        model = ollamaProperties.embeddingModel,
                        prompt = fact,
                    ),
                )
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            markNotReady()
            log.warn(ex) { "Embedding request failed" }
            return null
        }

        if (!response.status.isSuccess()) {
            log.warn { "Embedding request failed with status ${response.status}" }
            markNotReady()
            return null
        }

        val result: EmbeddingResponse = try {
            response.body()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            markNotReady()
            log.warn(ex) { "Embedding response could not be decoded" }
            return null
        }
        if (result.embedding.isEmpty()) {
            log.warn { "Embedding response returned empty vector." }
            markNotReady()
        }
        return result.embedding.takeIf { it.isNotEmpty() }
    }

    @PreDestroy
    fun shutdown() {
        httpClient.close()
    }

    private fun buildHttpClient(): HttpClient {
        return HttpClient(CIO) {
            defaultRequest {
                ollamaProperties.resolvedEmbeddingApiKey?.let { apiKey ->
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                }
                contentType(ContentType.Application.Json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = ollamaProperties.embeddingRequestTimeout.toMillis()
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = ollamaProperties.embeddingRequestTimeout.toMillis()
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    private fun markNotReady() {
        ready = false
        nextInitializationAttemptMillis = System.currentTimeMillis() + RETRY_DELAY_MILLIS
    }

    companion object {
        private const val RETRY_DELAY_MILLIS = 60_000L
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
