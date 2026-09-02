package co.qwex.chickenapi.ai

import co.qwex.chickenapi.config.KoogOllamaProperties
import co.qwex.chickenapi.config.WebToolsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class KoogHttpClientConfiguration {
    private val json = Json { ignoreUnknownKeys = true }

    @Bean
    @Qualifier("koogChickenFactsHttpClient")
    @ConditionalOnProperty(name = ["koog.agent.enabled"], havingValue = "true", matchIfMissing = true)
    fun koogChickenFactsHttpClient(properties: KoogOllamaProperties): HttpClient =
        createClient(
            // No default Authorization header: the Koog executor appends its
            // own Bearer from the apiKey passed to OpenAILLMClient. Adding one
            // here produces two Authorization headers, which LiteLLM hashes as
            // "key,key" and rejects with 401. The dependency validator sets
            // its own Bearer per probe.
            apiKey = null,
            extraHeaders = properties.extraHeaders.takeIf { properties.generationUsesOllamaCloud }.orEmpty(),
            requestTimeout = properties.llmRequestTimeout,
        )

    @Bean
    @Qualifier("koogChickenFactsWebToolsHttpClient")
    @ConditionalOnProperty(name = ["koog.agent.enabled"], havingValue = "true", matchIfMissing = true)
    fun koogChickenFactsWebToolsHttpClient(properties: KoogOllamaProperties): HttpClient =
        createWebToolsClient(properties)

    @Bean
    @Qualifier("koogBreedResearchHttpClient")
    @ConditionalOnProperty(name = ["koog.breed-research-agent.enabled"], havingValue = "true", matchIfMissing = true)
    fun koogBreedResearchHttpClient(
        ollamaProperties: KoogOllamaProperties,
    ): HttpClient =
        createClient(
            apiKey = null,
            extraHeaders = ollamaProperties.extraHeaders.takeIf { ollamaProperties.generationUsesOllamaCloud }.orEmpty(),
            requestTimeout = ollamaProperties.llmRequestTimeout,
        )

    @Bean
    @Qualifier("koogBreedResearchWebToolsHttpClient")
    @ConditionalOnProperty(name = ["koog.breed-research-agent.enabled"], havingValue = "true", matchIfMissing = true)
    fun koogBreedResearchWebToolsHttpClient(properties: KoogOllamaProperties): HttpClient =
        createWebToolsClient(properties)

    private fun createWebToolsClient(properties: KoogOllamaProperties): HttpClient {
        val hostedOllamaAuth =
            properties.webToolsProvider == WebToolsProvider.OLLAMA && properties.webToolsUseOllamaCloud
        return createClient(
            apiKey = properties.resolvedWebToolsApiKey.takeIf { hostedOllamaAuth },
            extraHeaders = properties.extraHeaders.takeIf { hostedOllamaAuth }.orEmpty(),
            requestTimeout = properties.webToolsRequestTimeout,
        )
    }

    private fun createClient(
        apiKey: String?,
        extraHeaders: Map<String, String>,
        requestTimeout: Duration,
    ): HttpClient =
        HttpClient(CIO) {
            defaultRequest {
                val authorizationValue =
                    apiKey?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
                val normalizedExtraHeaders = extraHeaders.filter { (key, value) ->
                    key.isNotBlank() && value.isNotBlank()
                }
                if (authorizationValue != null) {
                    header("Authorization", authorizationValue)
                }
                normalizedExtraHeaders
                    .filterNot { (key) -> authorizationValue != null && key.equals("Authorization", ignoreCase = true) }
                    .forEach { (key, value) -> header(key, value) }
                contentType(ContentType.Application.Json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = requestTimeout.toMillis()
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = requestTimeout.toMillis()
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
}
