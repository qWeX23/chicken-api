package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import co.qwex.chickenapi.ai.tools.WebFetchTool
import co.qwex.chickenapi.ai.tools.WebSearchTool
import co.qwex.chickenapi.config.KoogAgentProperties
import co.qwex.chickenapi.config.KoogOllamaProperties
import co.qwex.chickenapi.config.PhoenixTracingProperties
import co.qwex.chickenapi.config.WebToolsProvider
import io.github.oshai.kotlinlogging.KotlinLogging as OshaiKotlinLogging
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.ktor.client.HttpClient
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import mu.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Wraps a Koog single-run agent that uses the shared LiteLLM gateway runtime
 * for model execution and exposes the chicken-facts workflow to the rest of
 * the Spring app.
 */
@Service
class KoogChickenFactsAgent(
    private val properties: KoogAgentProperties,
    private val ollamaProperties: KoogOllamaProperties,
    private val phoenixTracingProperties: PhoenixTracingProperties,
    private val phoenixSpanExporterProvider: ObjectProvider<OtlpHttpSpanExporter>,
    private val phoenixResourceAttributesProvider: ObjectProvider<Map<String, Any>>,
    private val chickenFactDuplicateCheckService: ChickenFactDuplicateCheckService,
    private val dependencyValidator: AgentDependencyValidator,
    @Qualifier("koogChickenFactsHttpClient")
    private val llmHttpClientProvider: ObjectProvider<HttpClient>,
    @Qualifier("koogChickenFactsWebToolsHttpClient")
    private val webToolsHttpClientProvider: ObjectProvider<HttpClient>,
) {
    private val log = KotlinLogging.logger {}
    private val sanitizedBaseUrl = ollamaProperties.normalizedBaseUrl
    private val sanitizedWebToolsBaseUrl = ollamaProperties.normalizedWebToolsBaseUrl

    @Volatile
    private var runtime: AgentRuntime? = null

    @Volatile
    private var saveTool: SaveChickenFactTool? = null

    @Volatile
    private var searchTool: WebSearchTool? = null

    @Volatile
    private var nextInitializationAttemptMillis = 0L

    @Volatile
    private var shuttingDown = false

    @Volatile
    private var dependenciesReady = false

    @PostConstruct
    fun initialize() {
        if (!properties.enabled) {
            log.info { "Koog chicken facts agent disabled via configuration." }
            return
        }
        initializeRuntime()
    }

    @Synchronized
    private fun initializeRuntime() {
        if (runtime != null || shuttingDown || System.currentTimeMillis() < nextInitializationAttemptMillis) {
            return
        }

        val llmHttpClient = llmHttpClientProvider.getIfAvailable()
        val webToolClient = webToolsHttpClientProvider.getIfAvailable()
        if (llmHttpClient == null || webToolClient == null) {
            deferInitializationRetry()
            return
        }
        try {
            runBlocking {
                dependencyValidator.requireModelsAvailable(llmHttpClient, listOf(properties.model))
                dependencyValidator.requireGeneration(llmHttpClient, properties.model)
                dependencyValidator.requireWebSearch(webToolClient)
            }
        } catch (ex: Exception) {
            log.error(ex) { "Koog chicken facts dependencies are not ready" }
            deferInitializationRetry()
            return
        }
        val components =
            KoogGatewayExecutorFactory.create(
                baseUrl = sanitizedBaseUrl,
                apiKey = requireNotNull(ollamaProperties.resolvedGenerationApiKey) {
                    "A gateway API key is required for the Koog agents (koog.ollama.api-key / api-key-file)"
                },
                httpClient = llmHttpClient,
                requestTimeout = ollamaProperties.llmRequestTimeout,
                modelId = properties.model,
                contextLength = properties.contextLength.toLong(),
            )
        val promptExecutor = components.promptExecutor

        val model = components.model

        val saveChickenFactTool = SaveChickenFactTool(chickenFactDuplicateCheckService)
        val webSearchTool =
            WebSearchTool(
                httpClient = webToolClient,
                baseUrl = sanitizedWebToolsBaseUrl,
                defaultMaxResults = properties.webSearchMaxResults,
                provider = ollamaProperties.webToolsProvider,
                excludedDomains = properties.excludedSearchDomains,
                preferredDomains = properties.preferredSearchDomains,
            )
        val webFetchTool = if (ollamaProperties.webToolsProvider == WebToolsProvider.OLLAMA) {
            WebFetchTool(
                httpClient = webToolClient,
                baseUrl = sanitizedWebToolsBaseUrl,
            )
        } else {
            null
        }

        val toolRegistry =
            ToolRegistry {
                tool(saveChickenFactTool)
                tool(webSearchTool)
                if (webFetchTool != null) {
                    tool(webFetchTool)
                }
            }

        val agentConfig =
            AIAgentConfig(
                prompt = prompt(id = "chicken_facts_prompt") {
                    system(
                        """
        You are a chicken trivia enthusiast who loves discovering fun, quirky, and surprising facts about chickens.

- When you need new information, call the web_search tool.
- Copy the `Exact URL` value from web_search verbatim into the saved sourceUrl.
- web_search returns raw snippets; use the relevant facts directly when choosing another search or saving.
- Call at most one tool per turn.
- You may call web_search at most 3 times.
- Focus on finding interesting tidbits, amusing behaviors, historical stories, or surprising facts rather than scientific papers or academic research.
- Look for sources like blogs, fun fact websites, farming communities, chicken keeper forums, and general interest articles.
- Avoid overly technical or academic sources when possible.
- Search for one specific, surprising chicken fact rather than using broad "fun facts" queries.
- Do not use Wikipedia as a source.
- As soon as one good source supports the fact, call the save_chicken_fact tool ONCE to record it.
- The save_chicken_fact tool preserves your research in a structured format:
  - fact: a fun, interesting, or quirky fact about chickens (plain text, no markdown)
  - sourceUrl: the URL of the source you used
- Always complete your research by calling save_chicken_fact to document your discovery with its citation.

        """.trimIndent()
                    )
                },
                model = model,
                maxAgentIterations = properties.maxAgentIterations,
            )

        saveTool = saveChickenFactTool
        searchTool = webSearchTool
        runtime = AgentRuntime(llmHttpClient, webToolClient, toolRegistry, promptExecutor, model, agentConfig)
        dependenciesReady = true
        log.info {
            "Koog chicken facts agent initialized with model ${properties.model}; llm base ${ollamaProperties.baseUrl}; web tools ${ollamaProperties.webToolsProvider} at ${ollamaProperties.webToolsBaseUrl}"
        }
    }

    @Scheduled(fixedDelayString = "\${koog.ollama.agent-readiness-retry-delay:PT1M}")
    fun retryInitialization() {
        if (!properties.enabled) {
            return
        }
        val activeRuntime = runtime
        if (activeRuntime == null) {
            initializeRuntime()
        } else if (!dependenciesReady) {
            revalidateDependencies(activeRuntime)
        }
    }

    /**
     * Indicates whether the Koog agent is ready to accept runs.
     */
    fun isReady(): Boolean {
        if (properties.enabled && runtime == null) {
            initializeRuntime()
        }
        return runtime != null && dependenciesReady
    }

    /**
     * Creates and runs a new agent instance (agents are single-use).
     */
    suspend fun fetchChickenFacts(): String? {
        val activeRuntime = runtime ?: return null
        val activeSaveTool = saveTool ?: return null
        val activeSearchTool = searchTool ?: return null

        return try {
            log.info { "Creating new agent instance for chicken facts fetch" }
            val agent =
                ai.koog.agents.core.agent.AIAgent(
                    promptExecutor = activeRuntime.promptExecutor,
                    strategy = chickenResearchStrategy(
                        saveTool = activeSaveTool,
                        researchTool = activeSearchTool,
                        maxToolCalls = 4,
                        maxDuplicateRetries = 3,
                        retrySearchBudgetPerDuplicate = properties.retrySearchBudgetPerDuplicate,
                    ),
                    toolRegistry = activeRuntime.toolRegistry,
                    agentConfig = activeRuntime.agentConfig,
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
                        // addMessageProcessor(TraceFeatureMessageFileWriter(
                        //     sinkOpener = { path -> SystemFileSystem.sink(path).buffered() },
                        //     targetPath = Path("agenttraces/agent-traces-${System.currentTimeMillis()}.log")
                        // ))
                    }
                    if (phoenixTracingProperties.enabled) {
                        val phoenixSpanExporter = phoenixSpanExporterProvider.getIfAvailable()
                        if (phoenixSpanExporter != null) {
                            val phoenixResourceAttributes = phoenixResourceAttributesProvider.getIfAvailable().orEmpty()
                            install(OpenTelemetry) {
                                setServiceInfo(
                                    phoenixTracingProperties.serviceName,
                                    phoenixTracingProperties.serviceVersion,
                                )
                                setVerbose(phoenixTracingProperties.verbose)
                                addSpanExporter(phoenixSpanExporter)
                                addResourceAttributes(
                                    phoenixResourceAttributes +
                                        mapOf("llm.application" to "chicken-facts-agent"),
                                )
                            }
                        }
                    }
                }
            agent.run(properties.prompt)
        } catch (ex: Exception) {
            dependenciesReady = false
            deferInitializationRetry()
            log.error(ex) { "Koog agent failed to produce chicken facts." }
            throw ex
        }
    }

    @PreDestroy
    @Synchronized
    fun shutdown() {
        shuttingDown = true
        val activeRuntime = runtime
        runtime = null
        dependenciesReady = false
        saveTool = null
        searchTool = null
        activeRuntime?.promptExecutor?.close()
        activeRuntime?.toolsHttpClient?.close()
        activeRuntime?.ollamaHttpClient?.close()
    }

    private fun deferInitializationRetry() {
        nextInitializationAttemptMillis = System.currentTimeMillis() + DEPENDENCY_RETRY_DELAY_MILLIS
    }

    @Synchronized
    private fun revalidateDependencies(activeRuntime: AgentRuntime) {
        if (dependenciesReady || shuttingDown || System.currentTimeMillis() < nextInitializationAttemptMillis) {
            return
        }
        dependenciesReady = try {
            runBlocking {
                dependencyValidator.requireGeneration(activeRuntime.ollamaHttpClient, properties.model)
                dependencyValidator.requireWebSearch(activeRuntime.toolsHttpClient)
            }
            true
        } catch (ex: Exception) {
            deferInitializationRetry()
            log.error(ex) { "Koog chicken facts dependencies are still unavailable" }
            false
        }
    }

    companion object {
        private const val DEPENDENCY_RETRY_DELAY_MILLIS = 60_000L
    }
}

/**
 * Tool for saving a chicken fact with structured output.
 * This is the final tool that should be called to save the research result.
 */
class SaveChickenFactTool(
    private val duplicateCheckService: ChickenFactDuplicateCheckService,
) : SimpleTool<SaveChickenFactTool.Args>(
        argsType = typeToken<Args>(),
        name = "save_chicken_fact",
        description = "Saves a chicken fact with its source URL after running a duplicate check.",
    ) {
    private val log = KotlinLogging.logger {}

    @Serializable
    @JsonIgnoreUnknownKeys
    data class Args(
        @property:LLMDescription("The chicken fact in plain text (no markdown formatting)")
        val fact: String,
        @property:LLMDescription("The source URL where this fact was found")
        val sourceUrl: String,
    )

    @Serializable
    data class Result(
        val fact: String,
        val sourceUrl: String,
        val duplicateCheck: FactDuplicateCheckResult,
    )

    override suspend fun execute(args: Args): String {
        log.info { "Saving chicken fact with URL: ${args.sourceUrl}" }
        val duplicateCheck = duplicateCheckService.checkFactForDuplicate(args.fact, args.sourceUrl)
        return jsonCodec.encodeToString(
            Result.serializer(),
            Result(
                fact = args.fact,
                sourceUrl = args.sourceUrl,
                duplicateCheck = duplicateCheck,
            ),
        )
    }

    companion object {
        private val jsonCodec = Json { prettyPrint = true }
    }
}
