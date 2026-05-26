package co.qwex.chickenapi.ai.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

/**
 * Web search tool that queries Ollama's web search API and returns bounded raw
 * snippets. The main Koog strategy summarizes these visible tool results.
 */
class WebSearchTool(
    private val httpClient: HttpClient?,
    private val baseUrl: String,
    private val defaultMaxResults: Int,
) : SimpleTool<WebSearchTool.Args>(
        argsType = typeToken<Args>(),
        name = "web_search",
        description = "Search the public web to find the latest information. Returns search results with titles, URLs, and content snippets.",
    ) {

    @Serializable
    data class Args(
        @property:LLMDescription("The search query to find information on the web")
        val query: String,
        @property:LLMDescription("Optional: number of results to return (1-5, default is 1)")
        val maxResults: Int? = null,
    )

    override suspend fun execute(args: Args): String {
        val client = httpClient ?: throw IllegalStateException("Web search client not configured.")
        val resolvedMax = clampMaxResults(args.maxResults, defaultMaxResults)

        log.info { "Executing web_search with query: '${args.query}', maxResults: $resolvedMax" }

        val response =
            client.post("$baseUrl/api/web_search") {
                setBody(WebSearchRequest(query = args.query, maxResults = resolvedMax))
            }

        if (!response.status.isSuccess()) {
            val message = "web_search failed with status ${response.status}"
            log.warn { message }
            throw IllegalStateException(message)
        }

        val result: WebSearchResponse = response.body()
        log.info { "web_search returned ${result.results.size} results" }

        val boundedResults = result.results.mapIndexed { index, searchResult ->
            buildString {
                appendLine("Result ${index + 1}:")
                appendLine("Title: ${searchResult.title ?: "No title"}")
                appendLine("Exact URL: ${searchResult.url ?: "No URL"}")
                appendLine("Snippet: ${searchResult.content.orEmpty().boundedForToolResult(1_500)}")
            }
        }

        return buildString {
            appendLine("Search results for: ${args.query}")
            appendLine("Summarize these results before deciding whether to call web_fetch or save your findings.")
            appendLine()
            boundedResults.forEach { resultText ->
                appendLine(resultText)
                appendLine()
            }
        }
    }

    companion object {
        internal fun clampMaxResults(requested: Int?, defaultMaxResults: Int): Int =
            (requested ?: defaultMaxResults).coerceIn(1, 5)
    }
}

/**
 * Web fetch tool that retrieves full content from a URL and returns bounded raw
 * page text. The main Koog strategy summarizes these visible tool results.
 */
class WebFetchTool(
    private val httpClient: HttpClient?,
    private val baseUrl: String,
) : SimpleTool<WebFetchTool.Args>(
        argsType = typeToken<Args>(),
        name = "web_fetch",
        description = "Fetches the full content of a web page by URL and returns a concise summary with key facts. Use this to extract information from URLs found in web search results.",
    ) {

    @Serializable
    data class Args(
        @property:LLMDescription("The URL to fetch and read in detail")
        val url: String,
    )

    override suspend fun execute(args: Args): String {
        val client = httpClient ?: throw IllegalStateException("Web fetch client not configured.")
        val normalizedUrl = normalizeUrl(args.url)

        log.info { "Executing web_fetch for URL: $normalizedUrl" }

        // Fetch the raw web content
        val response =
            client.post("$baseUrl/api/web_fetch") {
                setBody(WebFetchRequest(url = normalizedUrl))
            }

        if (!response.status.isSuccess()) {
            val message = "web_fetch failed with status ${response.status}"
            log.warn { message }
            throw IllegalStateException(message)
        }

        val result: WebFetchResponse = response.body()
        log.info { "web_fetch received content from $normalizedUrl, title: ${result.title}" }

        return buildString {
            appendLine("Fetched web page")
            appendLine("URL: $normalizedUrl")
            appendLine("Title: ${result.title ?: "Unknown"}")
            if (result.links.isNotEmpty()) {
                appendLine("Links: ${result.links.take(10).joinToString()}")
            }
            appendLine()
            appendLine("Summarize this page content before saving your findings.")
            appendLine()
            appendLine(result.content.orEmpty().boundedForToolResult(8_000))
        }
    }

    companion object {
        internal fun normalizeUrl(url: String): String {
            var normalized = url.trim().removePrefix("<").removeSuffix(">")
            while (normalized.isNotEmpty() && normalized.last() in listOf('.', ',', ';', ':', ')', ']', '}')) {
                normalized = normalized.dropLast(1)
            }
            if (normalized.endsWith("?") && !normalized.contains("=") && !normalized.contains("&")) {
                normalized = normalized.dropLast(1)
            }
            return normalized
        }
    }
}

private fun String.boundedForToolResult(maxChars: Int): String =
    if (length <= maxChars) {
        this
    } else {
        take(maxChars).trimEnd() + "\n[truncated ${length - maxChars} chars]"
    }

//region DTOs
@Serializable
data class WebSearchRequest(
    @SerialName("query")
    val query: String,
    @SerialName("max_results")
    val maxResults: Int,
)

@Serializable
data class WebSearchResponse(
    val results: List<WebSearchResult> = emptyList(),
) {
    @Serializable
    data class WebSearchResult(
        val title: String? = null,
        val url: String? = null,
        val content: String? = null,
    )
}

@Serializable
data class WebFetchRequest(
    val url: String,
)

@Serializable
data class WebFetchResponse(
    val title: String? = null,
    val content: String? = null,
    val links: List<String> = emptyList(),
)
//endregion
