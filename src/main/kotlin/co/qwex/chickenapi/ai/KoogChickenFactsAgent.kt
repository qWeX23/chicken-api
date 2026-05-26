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
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import co.qwex.chickenapi.ai.tools.WebFetchTool
import co.qwex.chickenapi.ai.tools.WebSearchTool
import co.qwex.chickenapi.config.KoogAgentProperties
import co.qwex.chickenapi.config.KoogOllamaProperties
import co.qwex.chickenapi.config.PhoenixTracingProperties
import io.github.oshai.kotlinlogging.KotlinLogging as OshaiKotlinLogging
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.ktor.client.HttpClient
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Wraps a Koog single-run agent that uses the shared Ollama runtime for model
 * execution and exposes the chicken-facts workflow to the rest of the Spring app.
 */
@Service
class KoogChickenFactsAgent(
    private val properties: KoogAgentProperties,
    private val ollamaProperties: KoogOllamaProperties,
    private val phoenixTracingProperties: PhoenixTracingProperties,
    private val phoenixSpanExporterProvider: ObjectProvider<OtlpHttpSpanExporter>,
    private val phoenixResourceAttributesProvider: ObjectProvider<Map<String, Any>>,
    private val chickenFactDuplicateCheckService: ChickenFactDuplicateCheckService,
    @Qualifier("koogChickenFactsHttpClient")
    private val httpClientProvider: ObjectProvider<HttpClient>,
) {
    private val log = KotlinLogging.logger {}
    private val sanitizedBaseUrl = ollamaProperties.normalizedBaseUrl
    private val sanitizedWebToolsBaseUrl = ollamaProperties.normalizedWebToolsBaseUrl

    private var runtime: AgentRuntime? = null

    @PostConstruct
    fun initialize() {
        if (!properties.enabled) {
            log.info { "Koog chicken facts agent disabled via configuration." }
            return
        }

        val llmHttpClient = httpClientProvider.getIfAvailable() ?: return
        val webToolClient = llmHttpClient
        val promptExecutor =
            simpleOllamaAIExecutor(
                baseUrl = sanitizedBaseUrl,
                httpClientFactory = KtorKoogHttpClient.Factory(llmHttpClient),
            )

        val model =
            LLModel(
                provider = LLMProvider.Ollama,
                id = properties.model,
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.Schema.JSON.Basic,
                ),
                contextLength = 131_072,
            )

        val saveChickenFactTool = SaveChickenFactTool(chickenFactDuplicateCheckService)
        val webSearchTool =
            WebSearchTool(
                httpClient = webToolClient,
                baseUrl = sanitizedWebToolsBaseUrl,
                defaultMaxResults = properties.webSearchMaxResults,
            )
        val webFetchTool =
            WebFetchTool(
                httpClient = webToolClient,
                baseUrl = sanitizedWebToolsBaseUrl,
            )

        val toolRegistry =
            ToolRegistry {
                tool(saveChickenFactTool)
                tool(webSearchTool)
                tool(webFetchTool)
            }

        val agentConfig =
            AIAgentConfig(
                prompt = prompt("chicken_facts_prompt") {
                    system(
                        """
        You are a chicken trivia enthusiast who loves discovering fun, quirky, and surprising facts about chickens.

- When you need new information, call the web_search tool.
- Optionally use web_fetch to pull supporting content for specific URLs.
- When using a URL from web_search, copy the `Exact URL` value verbatim into web_fetch.
- web_search and web_fetch return raw snippets; summarize the relevant facts in your next visible reasoning step before choosing another tool or saving.
- You may call at most 3 tools total (any combination of web_search and web_fetch).
- Focus on finding interesting tidbits, amusing behaviors, historical stories, or surprising facts rather than scientific papers or academic research.
- Look for sources like blogs, fun fact websites, farming communities, chicken keeper forums, and general interest articles.
- Avoid overly technical or academic sources when possible.
- After you have information from 2–4 good sources, call the save_chicken_fact tool ONCE to record your finding.
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

        runtime = AgentRuntime(llmHttpClient, webToolClient, toolRegistry, promptExecutor, model, agentConfig)
        log.info {
            "Koog chicken facts agent initialized with model ${properties.model}; llm base ${ollamaProperties.baseUrl}; web tools base ${ollamaProperties.webToolsBaseUrl}"
        }
    }

    /**
     * Indicates whether the Koog agent is ready to accept runs.
     */
    fun isReady(): Boolean = runtime != null

    /**
     * Creates and runs a new agent instance (agents are single-use).
     */
    suspend fun fetchChickenFacts(): String? {
        val activeRuntime = runtime ?: return null

        return try {
            log.info { "Creating new agent instance for chicken facts fetch" }
            val agent =
                ai.koog.agents.core.agent.AIAgent(
                    promptExecutor = activeRuntime.promptExecutor,
                    strategy = chickenResearchStrategy(maxToolCalls = 4, maxDuplicateRetries = 3),
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
            log.error(ex) { "Koog agent failed to produce chicken facts." }
            null
        }
    }

    @PreDestroy
    fun shutdown() {
        runtime?.promptExecutor?.close()
        runtime?.toolsHttpClient?.close()
        runtime?.ollamaHttpClient?.close()
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
        val duplicateCheck = duplicateCheckService.checkFactForDuplicate(args.fact)
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
