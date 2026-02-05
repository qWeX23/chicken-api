package co.qwex.chickenapi.ai

import co.qwex.chickenapi.config.KoogAgentProperties
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KoogHttpClientConfiguration {
    private val json = Json { ignoreUnknownKeys = true }

    @Bean
    @Qualifier("koogChickenFactsHttpClient")
    @ConditionalOnExpression(
        "\${koog.agent.enabled:true} && (('\${koog.agent.client-id:}' != '' && '\${koog.agent.client-secret:}' != '') || '\${koog.agent.api-key:}' != '')",
    )
    fun koogChickenFactsHttpClient(properties: KoogAgentProperties): HttpClient =
        createAuthorizedClient(
            apiKey = properties.apiKey.orEmpty(),
            accessToken = properties.accessToken.orEmpty(),
            clientId = properties.clientId.orEmpty(),
            clientSecret = properties.clientSecret.orEmpty(),
            extraHeaders = properties.extraHeaders,
        )

    @Bean
    @Qualifier("koogBreedResearchHttpClient")
    @ConditionalOnExpression(
        "\${koog.breed-research-agent.enabled:true} && (('\${koog.agent.client-id:}' != '' && '\${koog.agent.client-secret:}' != '') || '\${koog.agent.api-key:}' != '')",
    )
    fun koogBreedResearchHttpClient(
        agentProperties: KoogAgentProperties,
    ): HttpClient =
        createAuthorizedClient(
            apiKey = agentProperties.apiKey.orEmpty(),
            accessToken = agentProperties.accessToken.orEmpty(),
            clientId = agentProperties.clientId.orEmpty(),
            clientSecret = agentProperties.clientSecret.orEmpty(),
            extraHeaders = agentProperties.extraHeaders,
        )

    private fun createAuthorizedClient(
        apiKey: String,
        accessToken: String,
        clientId: String,
        clientSecret: String,
        extraHeaders: Map<String, String>,
    ): HttpClient =
        HttpClient(CIO) {
            defaultRequest {
                val authorizationValue =
                    accessToken.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
                        ?: apiKey.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
                val normalizedExtraHeaders = extraHeaders.filter { (key, value) ->
                    key.isNotBlank() && value.isNotBlank()
                }
                val hasAuthorizationHeader = normalizedExtraHeaders.keys.any { key ->
                    key.equals("Authorization", ignoreCase = true)
                }
                if (authorizationValue != null && !hasAuthorizationHeader) {
                    header("Authorization", authorizationValue)
                }
                if (clientId.isNotBlank()) {
                    header("CF-Access-Client-Id", clientId)
                }
                if (clientSecret.isNotBlank()) {
                    header("CF-Access-Client-Secret", clientSecret)
                }
                normalizedExtraHeaders
                    .filterNot { (key) -> key.equals("Authorization", ignoreCase = true) && authorizationValue != null }
                    .forEach { (key, value) -> header(key, value) }
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
}
