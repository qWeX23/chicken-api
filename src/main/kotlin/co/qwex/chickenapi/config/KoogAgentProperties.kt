package co.qwex.chickenapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration holder for the Koog chicken facts agent.
 *
 * Values can be overridden via `application.properties` or environment variables.
 */
@ConfigurationProperties(prefix = "koog.agent")
data class KoogAgentProperties(
    val enabled: Boolean = true,
    val baseUrl: String = "https://ollama.com",
    val model: String = "gpt-oss:120b",
    val prompt: String = "Find the coolest new facts about chickens and cite your sources. Format your response as a markdown bullet list.",
    val apiKey: String? = null,
    val webSearchMaxResults: Int = 3,
    val maxAgentIterations: Int = 100,
)
