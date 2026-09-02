package co.qwex.chickenapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

enum class WebToolsProvider {
    OLLAMA,
    SEARXNG,
}

/**
 * Shared Ollama runtime configuration used by agents, embeddings, and hosted web tools.
 */
@ConfigurationProperties(prefix = "koog.ollama")
data class KoogOllamaProperties(
    val baseUrl: String = "https://ollama.com",
    val webToolsBaseUrl: String = "https://ollama.com",
    val webToolsProvider: WebToolsProvider = WebToolsProvider.OLLAMA,
    val apiKey: String? = null,
    val apiKeyFile: String? = null,
    val webToolsApiKey: String? = null,
    val webToolsApiKeyFile: String? = null,
    val apiKeyRequired: Boolean = true,
    val extraHeaders: Map<String, String> = emptyMap(),
    val embeddingBaseUrl: String? = null,
    val embeddingApiKey: String? = null,
    val embeddingApiKeyFile: String? = null,
    val embeddingModel: String = "nomic-embed-text",
    val llmRequestTimeout: Duration = Duration.ofMinutes(20),
    val webToolsRequestTimeout: Duration = Duration.ofSeconds(45),
    val embeddingRequestTimeout: Duration = Duration.ofMinutes(2),
    val readinessRequestTimeout: Duration = Duration.ofMinutes(2),
) {
    val normalizedBaseUrl: String
        get() = normalize(baseUrl)

    val normalizedWebToolsBaseUrl: String
        get() = normalize(webToolsBaseUrl)

    val normalizedEmbeddingBaseUrl: String
        get() = normalize(embeddingBaseUrl ?: baseUrl)

    val generationUsesOllamaCloud: Boolean
        get() = isOllamaCloud(normalizedBaseUrl)

    val generationUsesAuthenticatedEndpoint: Boolean
        get() = generationUsesOllamaCloud || generationUsesGateway

    val generationUsesGateway: Boolean
        get() = isLocalGateway(normalizedBaseUrl)

    val webToolsUseOllamaCloud: Boolean
        get() = isOllamaCloud(normalizedWebToolsBaseUrl)

    val resolvedApiKey: String?
        get() = resolveSecret(apiKeyFile, apiKey)

    val resolvedGenerationApiKey: String?
        get() = resolvedApiKey.takeIf { generationUsesAuthenticatedEndpoint }

    val resolvedWebToolsApiKey: String?
        get() =
            (resolveSecret(webToolsApiKeyFile, webToolsApiKey) ?: resolvedGenerationApiKey)
                .takeIf { webToolsUseOllamaCloud }

    val resolvedEmbeddingApiKey: String?
        get() =
            resolveSecret(embeddingApiKeyFile, embeddingApiKey)
                ?: resolvedGenerationApiKey.takeIf { normalizedEmbeddingBaseUrl == normalizedBaseUrl }

    private fun normalize(url: String): String =
        url.trim().removeSuffix("/").removeSuffix("/api")

    private fun isOllamaCloud(url: String): Boolean =
        runCatching {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("ollama.com", ignoreCase = true) &&
                (uri.port == -1 || uri.port == 443)
        }.getOrDefault(false)

    private fun isLocalGateway(url: String): Boolean =
        runCatching {
            val uri = URI(url)
            uri.host.equals("litellm", ignoreCase = true) &&
                (uri.port == -1 || uri.port == 4000)
        }.getOrDefault(false)

    private fun resolveSecret(file: String?, value: String?): String? =
        file?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            runCatching { Files.readString(Path.of(path)) }.getOrNull()?.normalizeSecret()
        } ?: value.normalizeSecret()

    private fun String?.normalizeSecret(): String? =
        this?.trim()?.takeIf { secret -> secret.isNotEmpty() && secret.none(Char::isISOControl) }
}
