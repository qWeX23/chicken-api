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
        "\${koog.agent.enabled:true} && '\${koog.agent.client-id:}' != '' && '\${koog.agent.client-secret:}' != ''",
    )
    fun koogChickenFactsHttpClient(properties: KoogAgentProperties): HttpClient =
        createAuthorizedClient(
            clientId = properties.clientId.orEmpty(),
            clientSecret = properties.clientSecret.orEmpty(),
        )

    @Bean
    @Qualifier("koogBreedResearchHttpClient")
    @ConditionalOnExpression(
        "\${koog.breed-research-agent.enabled:true} && '\${koog.breed-research-agent.client-id:}' != '' && '\${koog.breed-research-agent.client-secret:}' != ''",
    )
    fun koogBreedResearchHttpClient(properties: KoogAgentProperties): HttpClient =
        createAuthorizedClient(
            clientId = properties.clientId.orEmpty(),
            clientSecret = properties.clientSecret.orEmpty(),
        )

    private fun createAuthorizedClient(clientId: String, clientSecret: String): HttpClient =
        HttpClient(CIO) {
            defaultRequest {
                header("CF-Access-Client-Id", clientId)
                header("CF-Access-Client-Secret", clientSecret)
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
}
