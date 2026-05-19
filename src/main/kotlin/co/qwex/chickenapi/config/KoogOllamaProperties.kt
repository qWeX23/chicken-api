package co.qwex.chickenapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Shared Ollama runtime configuration used by agents, embeddings, and hosted web tools.
 */
@ConfigurationProperties(prefix = "koog.ollama")
data class KoogOllamaProperties(
    val baseUrl: String = "https://ollama.com",
    val webToolsBaseUrl: String = "https://ollama.com",
    val apiKey: String? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
    val embeddingModel: String = "nomic-embed-text",
) {
    val normalizedBaseUrl: String
        get() = normalize(baseUrl)

    val normalizedWebToolsBaseUrl: String
        get() = normalize(webToolsBaseUrl)

    private fun normalize(url: String): String =
        url.trim().removeSuffix("/").removeSuffix("/api")
}
