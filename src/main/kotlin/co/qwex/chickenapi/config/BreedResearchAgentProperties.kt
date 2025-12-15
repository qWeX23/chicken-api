package co.qwex.chickenapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration holder for the Koog breed research agent.
 *
 * Values can be overridden via `application.properties` or environment variables.
 */
@ConfigurationProperties(prefix = "koog.breed-research-agent")
data class BreedResearchAgentProperties(
    val enabled: Boolean = true,
    val baseUrl: String = "https://ollama.com",
    val model: String = "gpt-oss:120b",
    val apiKey: String? = null,
    val webSearchMaxResults: Int = 3,
    val maxAgentIterations: Int = 100,
    val maxToolCalls: Int = 8,
)
