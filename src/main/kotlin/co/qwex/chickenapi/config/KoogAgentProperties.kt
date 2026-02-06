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
    val embeddingModel: String = "nomic-embed-text",
    val prompt: String = "Find an interesting, fun, or quirky fact about chickens. Look for trivia, surprising behaviors, historical tidbits, or amusing chicken stories rather than scientific research papers. Cite your sources. Format your response as a markdown. stay on topic about chickens. only return one fact.",
    val apiKey: String? = null,
    val accessToken: String? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
    val webSearchMaxResults: Int = 3,
    val maxAgentIterations: Int = 100,
)
