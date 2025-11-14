package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.ext.agent.chatAgentStrategy
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

                val webSearchTool = WebSearchTool(
                    httpClient = webToolClient,
                    baseUrl = sanitizedBaseUrl,
                    defaultMaxResults = properties.webSearchMaxResults,
                )
                val webFetchTool = WebFetchTool(
                    httpClient = webToolClient,
                    baseUrl = sanitizedBaseUrl,
                )

                toolRegistry = ToolRegistry {
                    tool(webSearchTool)
                    tool(webFetchTool)
                }

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

                agentConfig = AIAgentConfig(
                    prompt = prompt("chicken_facts_prompt") {
                        system(
                            "You are Koog's chicken fact researcher. " +
                                "Always call the available web_search tool before responding, " +
                                "use web_fetch to capture supporting content, and return a short markdown bullet list " +
                                "that cites the URLs you used. You must use the tools unless you are 100% sure of the answer. " +
                                "CRITICAL: Do NOT output planning text, thoughts, or intentions. Only use tools or provide the final answer. " +
                                "Never respond with things like 'I'm ready to start', 'Ok I will do this now', 'My plan is', or 'I will'. " +
                                "If you are not using a tool, the output will be considered your final answer. " +
                                "Make sure to complete the task fully."
                        )
                    },
                    model = model!!,
                    maxAgentIterations = properties.maxAgentIterations,
                )

                promptExecutor = SingleLLMPromptExecutor(
                    OllamaClient(
                        baseUrl = sanitizedBaseUrl,
                        baseClient = llmHttpClient,
                    ),
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
                    strategy = chatAgentStrategy(),
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
                            targetPath = Path("agent-traces.log")
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
 * Web search tool that queries Ollama's web search API.
 */
class WebSearchTool(
    private val httpClient: HttpClient?,
    private val baseUrl: String,
    private val defaultMaxResults: Int,
) : SimpleTool<WebSearchTool.Args>() {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Args(
        @property:LLMDescription("The search query to find information on the web")
        val query: String,
        @property:LLMDescription("Optional: number of results to return (1-10, default is 5)")
        val maxResults: Int? = null,
    )

    override val argsSerializer = Args.serializer()
    override val name = "web_search"
    override val description = "Search the public web to find the latest information. Returns search results with titles, URLs, and content snippets."

    override suspend fun doExecute(args: Args): String {
        val client = httpClient ?: throw IllegalStateException("Web search client not configured.")
        val resolvedMax = (args.maxResults ?: defaultMaxResults).coerceIn(1, 10)

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
        return json.encodeToString(result)
    }
}

/**
 * Web fetch tool that retrieves full content from a URL.
 */
class WebFetchTool(
    private val httpClient: HttpClient?,
    private val baseUrl: String,
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
    override val description = "Fetches the full content of a web page by URL. Use this to read detailed content from URLs found in web search results."

    override suspend fun doExecute(args: Args): String {
        val client = httpClient ?: throw IllegalStateException("Web fetch client not configured.")

        log.info { "Executing web_fetch for URL: ${args.url}" }

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
        log.info { "web_fetch completed for ${args.url}" }
        return json.encodeToString(result)
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
