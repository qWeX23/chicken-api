package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import co.qwex.chickenapi.config.KoogAgentProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import jakarta.annotation.PreDestroy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.stereotype.Service
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import io.github.oshai.kotlinlogging.KotlinLogging as OshaiKotlinLogging
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.buffered

/**
 * Wraps a Koog single-run agent that talks to Ollama Cloud and exposes the
 * chicken-facts workflow to the rest of the Spring app.
 */
@Service
class KoogChickenFactsAgent(
    private val properties: KoogAgentProperties,
) {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val sanitizedBaseUrl =
        properties.baseUrl.trim().removeSuffix("/").removeSuffix("/api")

    private var ollamaHttpClient: HttpClient? = null
    private var toolsHttpClient: HttpClient? = null
    private var toolRegistry: ToolRegistry? = null
    private var promptExecutor: SingleLLMPromptExecutor? = null
    private var model: LLModel? = null
    private var agentConfig: AIAgentConfig? = null

    init {
        if (!properties.enabled) {
            log.info { "Koog chicken facts agent disabled via configuration." }
        } else {
            val apiKey = properties.apiKey?.takeIf { it.isNotBlank() }
            if (apiKey == null) {
                log.warn { "koog.agent.api-key is not set; Koog agent will be skipped." }
            } else {
                val llmHttpClient = authorizedHttpClient(apiKey)
                val webToolClient = authorizedHttpClient(apiKey)
                ollamaHttpClient = llmHttpClient
                toolsHttpClient = webToolClient

                promptExecutor = SingleLLMPromptExecutor(
                    OllamaClient(
                        baseUrl = sanitizedBaseUrl,
                        baseClient = llmHttpClient,
                    ),
                )

                model = LLModel(
                    provider = LLMProvider.Ollama,
                    id = properties.model,
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Tools,
                        LLMCapability.Schema.JSON.Basic,
                    ),
                    contextLength = 131_072,
                )

                val webSearchTool = WebSearchTool(
                    httpClient = webToolClient,
                    baseUrl = sanitizedBaseUrl,
                    defaultMaxResults = properties.webSearchMaxResults,
                    promptExecutor = promptExecutor,
                    model = model,
                )
                val webFetchTool = WebFetchTool(
                    httpClient = webToolClient,
                    baseUrl = sanitizedBaseUrl,
                    promptExecutor = promptExecutor,
                    model = model,
                )

                toolRegistry = ToolRegistry {
                    tool(webSearchTool)
                    tool(webFetchTool)
                }

                agentConfig = AIAgentConfig(
                    prompt = prompt("chicken_facts_prompt") {
                        system(
                                   """
        You are an expert chicken fact researcher.

- When you need new information, call the web_search tool.
- Optionally use web_fetch to pull supporting content for specific URLs.
- You may call at most 3 tools total (any combination of web_search and web_fetch).
- After you have information from 2–4 good sources, you MUST STOP calling tools
  and produce the final answer.
- Final answer must be a SHORT markdown bullet list,
  each bullet with:
  - a cool, recent fact about chickens, and
  - a source URL you actually used.

Do NOT ever call tools again after you have started writing the final answer.

        """.trimIndent()
         )
                    },
                    model = model!!,
                    maxAgentIterations = properties.maxAgentIterations,
                )

                log.info {
                    "Koog chicken facts agent initialized with model ${properties.model} using base ${properties.baseUrl}"
                }
            }
        }
    }

    /**
     * Indicates whether the Koog agent is ready to accept runs.
     */
    fun isReady(): Boolean = promptExecutor != null

    /**
     * Creates and runs a new agent instance (agents are single-use).
     */
    suspend fun fetchChickenFacts(): String? {
        val executor = promptExecutor ?: return null
        val registry = toolRegistry ?: return null
        val config = agentConfig ?: return null

        return try {
            log.info { "Creating new agent instance for chicken facts fetch" }
            val agent =
                AIAgent(
                    promptExecutor = executor,
                    strategy = chickenResearchStrategy(
                        maxToolCalls = 4,
                    ),
                    toolRegistry = registry,
                    agentConfig = config,
                ) {
                    // handleEvents {
                    //     // Log LLM interactions
                    //     onLLMCallStarting { ctx ->
                    //         log.info { "Sending prompt to LLM: ${ctx.prompt}" }
                    //     }

                    //     onLLMCallCompleted { ctx ->
                    //         log.info { "Received ${ctx.responses.size} response(s) from LLM ${ctx.responses.joinToString()}" }
                    //     }

                    //     // Monitor tool usage
                    //     onToolCallStarting { ctx ->
                    //         log.info { "Tool called: ${ctx.tool.name} with args: ${ctx.toolArgs}" }
                    //     }

                    //     onToolCallCompleted { ctx ->
                    //         log.info { "Tool result: ${ctx.result}" }
                    //     }

                    //     onToolCallFailed { ctx ->
                    //         log.error(ctx.throwable) { "Tool failed: ${ctx.throwable.message}" }
                    //     }

                    //     // Track agent progress
                    //     onStrategyStarting { ctx ->
                    //         log.info { "Strategy started: ${ctx.strategy.name}" }
                    //     }

                    //     onStrategyCompleted { ctx ->
                    //         log.info { "Strategy finished with result: ${ctx.result}" }
                    //     }

                    //     onAgentCompleted { ctx ->
                    //         log.info { "Koog agent finished run with output preview ${ctx.result}" }
                    //     }
                    // }
                    install(Tracing) {
                        addMessageProcessor(TraceFeatureMessageLogWriter(OshaiKotlinLogging.logger {}))
                        addMessageProcessor(TraceFeatureMessageFileWriter(
                            sinkOpener = { path -> SystemFileSystem.sink(path).buffered() },
                            targetPath = Path("agenttraces/agent-traces-${System.currentTimeMillis()}.log")
                        ))
                    }
                }
            agent.run(properties.prompt)
        } catch (ex: Exception) {
            log.error(ex) { "Koog agent failed to produce chicken facts." }
            null
        }
    }

    @PreDestroy
    fun shutdown() {
        toolsHttpClient?.close()
        ollamaHttpClient?.close()
    }

    private fun authorizedHttpClient(apiKey: String): HttpClient =
        HttpClient(CIO) {
            defaultRequest {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
}

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
) : SimpleTool<WebSearchTool.Args>() {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

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
        val resolvedMax = (args.maxResults ?: defaultMaxResults).coerceIn(1, 1)

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
            val resultText = buildString {
                appendLine("Title: ${searchResult.title ?: "No title"}")
                appendLine("URL: ${searchResult.url ?: "No URL"}")
                appendLine("Content: ${searchResult.content ?: "No content"}")
            }

            val summarizationPrompt = prompt("search_result_summarizer_$index") {
                system(
                    "You are a search result summarizer. Extract the key information from this single search result. " +
                        "Keep it concise (2-3 sentences max). " +
                        "Include the URL. " +
                        "Focus on facts relevant to the search query: ${args.query}"
                )
                user(resultText)
            }

            try {
                log.info { "Summarizing result ${index + 1} for query: '${args.query}'" }
                val summary = executor.execute(summarizationPrompt, llmModel)
                summary.toString()
            } catch (ex: Exception) {
                log.error(ex) { "Failed to summarize result ${index + 1} for '${args.query}'" }
                // Fallback to truncated content
                "Title: ${searchResult.title}\nURL: ${searchResult.url}\nSummary: ${searchResult.content?.take(200)}"
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
) : SimpleTool<WebFetchTool.Args>() {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

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

        log.info { "Executing web_fetch for URL: ${args.url}" }

        // Fetch the raw web content
        val response =
            client.post("$baseUrl/api/web_fetch") {
                setBody(WebFetchRequest(url = args.url))
            }

        if (!response.status.isSuccess()) {
            val message = "web_fetch failed with status ${response.status}"
            log.warn { message }
            throw IllegalStateException(message)
        }

        val result: WebFetchResponse = response.body()
        log.info { "web_fetch received content from ${args.url}, title: ${result.title}" }

        // Create a one-shot summarization prompt
        val summarizationPrompt = prompt("web_page_summarizer") {
            system(
                "You are a web content summarizer. Extract and distill the key facts from the provided web page content. " +
                    "Focus on factual information and important details. " +
                    "Return a concise bullet-point summary. " +
                    "Include the source URL in your summary."
            )
            user(
                "URL: ${args.url}\n" +
                    "Title: ${result.title ?: "Unknown"}\n\n" +
                    "Content:\n${result.content ?: "No content available"}\n\n" +
                    "Please summarize the key facts from this web page."
            )
        }

        // Execute the one-shot summarization
        return try {
            log.info { "Summarizing content from ${args.url} with LLM" }
            val summary = executor.execute(summarizationPrompt, llmModel)
            log.info { "Successfully summarized content from ${args.url}" }
            summary.toString()
        } catch (ex: Exception) {
            log.error(ex) { "Failed to summarize web content from ${args.url}" }
            // Fallback to raw content if summarization fails
            json.encodeToString(result)
        }
    }
}

//region DTOs
@Serializable
private data class WebSearchRequest(
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
private data class WebFetchRequest(
    val url: String,
)

@Serializable
data class WebFetchResponse(
    val title: String? = null,
    val content: String? = null,
    val links: List<String> = emptyList(),
)
//endregion
