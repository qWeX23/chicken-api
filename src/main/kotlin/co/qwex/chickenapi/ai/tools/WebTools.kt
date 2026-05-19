package co.qwex.chickenapi.ai.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
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
 * Web search tool that queries Ollama's web search API and uses a one-shot
 * Koog agent to summarize the search results to prevent context overflow.
 */
class WebSearchTool(
    private val httpClient: HttpClient?,
    private val baseUrl: String,
    private val defaultMaxResults: Int,
    private val promptExecutor: SingleLLMPromptExecutor?,
    private val model: LLModel?,
    private val summarizationFocus: String = "interesting, fun, or surprising facts",
) : SimpleTool<WebSearchTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("The search query to find information on the web")
        val query: String,
        @property:LLMDescription("Optional: number of results to return (1-5, default is 1)")
        val maxResults: Int? = null,
    )

    override val argsSerializer = Args.serializer()
    override val name = "web_search"
    override val description = "Search the public web to find the latest information. Returns search results with titles, URLs, and content snippets."

    override suspend fun doExecute(args: Args): String {
        val client = httpClient ?: throw IllegalStateException("Web search client not configured.")
        val executor = promptExecutor ?: throw IllegalStateException("Prompt executor not configured.")
        val llmModel = model ?: throw IllegalStateException("Model not configured.")
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

        // Summarize each result individually
        val summarizedResults = result.results.mapIndexed { index, searchResult ->
            val exactTitle = searchResult.title ?: "No title"
            val exactUrl = searchResult.url ?: "No URL"
            val resultText = buildString {
                appendLine("Title: $exactTitle")
                appendLine("Content: ${searchResult.content ?: "No content"}")
            }

            val summarizationPrompt = prompt("search_result_summarizer_$index") {
                system(
                    "You are a search result summarizer. Extract the key information from this single search result. " +
                        "Keep it concise (2-3 sentences max). " +
                        "Do not include or rewrite URLs. " +
                        "Focus on $summarizationFocus. " +
                        "Focus on facts relevant to the search query: ${args.query}"
                )
                user(resultText)
            }

            val summary =
                try {
                    log.info { "Summarizing result ${index + 1} for query: '${args.query}'" }
                    executor.execute(summarizationPrompt, llmModel).toString()
                } catch (ex: Exception) {
                    log.error(ex) { "Failed to summarize result ${index + 1} for '${args.query}'" }
                    // Fallback to truncated content
                    searchResult.content?.take(200) ?: "No content"
                }

            buildString {
                appendLine("Title: $exactTitle")
                appendLine("Exact URL: $exactUrl")
                appendLine("Summary: $summary")
            }
        }

        // Combine all summarized results
        return buildString {
            appendLine("Search results for: ${args.query}")
            appendLine()
            summarizedResults.forEachIndexed { index, summary ->
                appendLine("Result ${index + 1}:")
                appendLine(summary)
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
 * Web fetch tool that retrieves full content from a URL and uses a one-shot
 * Koog agent to summarize and distill facts from the page.
 */
class WebFetchTool(
    private val httpClient: HttpClient?,
    private val baseUrl: String,
    private val promptExecutor: SingleLLMPromptExecutor?,
    private val model: LLModel?,
    private val summarizationFocus: String = "interesting, fun, or surprising facts, trivia, quirky behaviors, amusing stories, and fascinating tidbits",
) : SimpleTool<WebFetchTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("The URL to fetch and read in detail")
        val url: String,
    )

    override val argsSerializer = Args.serializer()
    override val name = "web_fetch"
    override val description = "Fetches the full content of a web page by URL and returns a concise summary with key facts. Use this to extract information from URLs found in web search results."

    override suspend fun doExecute(args: Args): String {
        val client = httpClient ?: throw IllegalStateException("Web fetch client not configured.")
        val executor = promptExecutor ?: throw IllegalStateException("Prompt executor not configured.")
        val llmModel = model ?: throw IllegalStateException("Model not configured.")
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

        // Create a one-shot summarization prompt
        val summarizationPrompt = prompt("web_page_summarizer") {
            system(
                "You are a web content summarizer. Extract and distill $summarizationFocus from the provided web page content. " +
                    "Return a concise bullet-point summary. " +
                    "Include the source URL in your summary."
            )
            user(
                "URL: $normalizedUrl\n" +
                    "Title: ${result.title ?: "Unknown"}\n\n" +
                    "Content:\n${result.content ?: "No content available"}\n\n" +
                    "Please summarize the interesting and relevant facts from this web page."
            )
        }

        // Execute the one-shot summarization
        return try {
            log.info { "Summarizing content from $normalizedUrl with LLM" }
            val summary = executor.execute(summarizationPrompt, llmModel)
            log.info { "Successfully summarized content from $normalizedUrl" }
            summary.toString()
        } catch (ex: Exception) {
            log.error(ex) { "Failed to summarize web content from $normalizedUrl" }
            // Fallback to raw content if summarization fails
            json.encodeToString(WebFetchResponse.serializer(), result)
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
