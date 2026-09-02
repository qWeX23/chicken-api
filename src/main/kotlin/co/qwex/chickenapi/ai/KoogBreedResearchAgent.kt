package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.dsl.prompt
import co.qwex.chickenapi.ai.tools.GetBreedDetailsTool
import co.qwex.chickenapi.ai.tools.GetNextBreedToResearchTool
import co.qwex.chickenapi.ai.tools.BreedResearchRunContext
import co.qwex.chickenapi.ai.tools.SaveBreedResearchTool
import co.qwex.chickenapi.ai.tools.WebFetchTool
import co.qwex.chickenapi.ai.tools.WebSearchTool
import co.qwex.chickenapi.config.BreedResearchAgentProperties
import co.qwex.chickenapi.config.KoogOllamaProperties
import co.qwex.chickenapi.config.PhoenixTracingProperties
import co.qwex.chickenapi.config.WebToolsProvider
import co.qwex.chickenapi.repository.BreedRepository
import io.github.oshai.kotlinlogging.KotlinLogging as OshaiKotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.ktor.client.HttpClient
import mu.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps a Koog single-run agent that researches chicken breeds in depth.
 * The agent selects the breed most in need of research (oldest updatedAt or never updated),
 * researches it thoroughly, and produces a comprehensive report with verified facts.
 */
@Service
class KoogBreedResearchAgent(
    private val properties: BreedResearchAgentProperties,
    private val ollamaProperties: KoogOllamaProperties,
    private val phoenixTracingProperties: PhoenixTracingProperties,
    private val phoenixSpanExporterProvider: ObjectProvider<OtlpHttpSpanExporter>,
    private val phoenixResourceAttributesProvider: ObjectProvider<Map<String, Any>>,
    private val breedRepository: BreedRepository,
    private val dependencyValidator: AgentDependencyValidator,
    @Qualifier("koogBreedResearchHttpClient")
    private val llmHttpClientProvider: ObjectProvider<HttpClient>,
    @Qualifier("koogBreedResearchWebToolsHttpClient")
    private val webToolsHttpClientProvider: ObjectProvider<HttpClient>,
) {
    private val log = KotlinLogging.logger {}
    private val sanitizedBaseUrl = ollamaProperties.normalizedBaseUrl
    private val sanitizedWebToolsBaseUrl = ollamaProperties.normalizedWebToolsBaseUrl
    private val runContext = BreedResearchRunContext()
    private val runInProgress = AtomicBoolean()

    @Volatile
    private var runtime: AgentRuntime? = null

    @Volatile
    private var saveTool: SaveBreedResearchTool? = null

    @Volatile
    private var nextInitializationAttemptMillis = 0L

    @Volatile
    private var shuttingDown = false

    @Volatile
    private var dependenciesReady = false

    @PostConstruct
    fun initialize() {
        if (!properties.enabled) {
            log.info { "Koog breed research agent disabled via configuration." }
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
            kotlinx.coroutines.runBlocking {
                dependencyValidator.requireModelsAvailable(llmHttpClient, listOf(properties.model))
                dependencyValidator.requireGeneration(llmHttpClient, properties.model)
                dependencyValidator.requireWebSearch(webToolClient)
            }
        } catch (ex: Exception) {
            log.error(ex) { "Koog breed research dependencies are not ready" }
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

        // Create tools
        val getNextBreedTool = GetNextBreedToResearchTool(breedRepository, runContext)
        val getBreedDetailsTool = GetBreedDetailsTool(breedRepository, runContext)
        val webSearchTool =
            WebSearchTool(
                httpClient = webToolClient,
                baseUrl = sanitizedWebToolsBaseUrl,
                defaultMaxResults = properties.webSearchMaxResults,
                provider = ollamaProperties.webToolsProvider,
            )
        val webFetchTool = if (ollamaProperties.webToolsProvider == WebToolsProvider.OLLAMA) {
            WebFetchTool(
                httpClient = webToolClient,
                baseUrl = sanitizedWebToolsBaseUrl,
            )
        } else {
            null
        }
        val saveBreedResearchTool = SaveBreedResearchTool(breedRepository, runContext)

        val toolRegistry =
            ToolRegistry {
                tool(getNextBreedTool)
                tool(getBreedDetailsTool)
                tool(webSearchTool)
                if (webFetchTool != null) {
                    tool(webFetchTool)
                }
                tool(saveBreedResearchTool)
            }

        val agentConfig =
            AIAgentConfig(
                prompt = prompt(
                    id = "breed_research_prompt",
                ) {
                    system(BREED_RESEARCH_SYSTEM_PROMPT)
                },
                model = model,
                maxAgentIterations = properties.maxAgentIterations,
            )

        saveTool = saveBreedResearchTool
        runtime = AgentRuntime(llmHttpClient, webToolClient, toolRegistry, promptExecutor, model, agentConfig)
        dependenciesReady = true
        log.info {
            "Koog breed research agent initialized with model ${properties.model}; llm base ${ollamaProperties.baseUrl}; web tools ${ollamaProperties.webToolsProvider} at ${ollamaProperties.webToolsBaseUrl}"
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
     * Creates and runs a new agent instance to research a breed.
     * Returns the JSON output from save_breed_research tool.
     */
    suspend fun researchBreed(): String? {
        val activeRuntime = runtime ?: return null
        val activeSaveTool = saveTool ?: return null
        check(runInProgress.compareAndSet(false, true)) { "Breed research agent is already running" }

        return try {
            runContext.reset()
            log.info { "Creating new agent instance for breed research" }
            val agent =
                ai.koog.agents.core.agent.AIAgent(
                    promptExecutor = activeRuntime.promptExecutor,
                    strategy = breedResearchStrategy(
                        saveTool = activeSaveTool,
                        maxToolCalls = properties.maxToolCalls,
                    ),
                    toolRegistry = activeRuntime.toolRegistry,
                    agentConfig = activeRuntime.agentConfig,
                ) {
                    install(Tracing) {
                        addMessageProcessor(TraceFeatureMessageLogWriter(OshaiKotlinLogging.logger {}))
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
                                        mapOf("llm.application" to "breed-research-agent"),
                                )
                            }
                        }
                    }
                }
            agent.run(BREED_RESEARCH_USER_PROMPT)
        } catch (ex: Exception) {
            dependenciesReady = false
            deferInitializationRetry()
            log.error(ex) { "Koog agent failed to research breed." }
            throw ex
        } finally {
            runInProgress.set(false)
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
            kotlinx.coroutines.runBlocking {
                dependencyValidator.requireGeneration(activeRuntime.ollamaHttpClient, properties.model)
                dependencyValidator.requireWebSearch(activeRuntime.toolsHttpClient)
            }
            true
        } catch (ex: Exception) {
            deferInitializationRetry()
            log.error(ex) { "Koog breed research dependencies are still unavailable" }
            false
        }
    }

    companion object {
        private const val DEPENDENCY_RETRY_DELAY_MILLIS = 60_000L

        private val BREED_RESEARCH_SYSTEM_PROMPT = """
            You are a chicken breed specialist who writes compelling, accurate breed descriptions for a chicken encyclopedia.

            ## Workflow

            1. Call `get_next_breed_to_research` to get the breed you should research
            2. Call `get_breed_details` to see what information we currently have
            3. Use `web_search` to research the breed (up to 8 tool calls total)
            4. Call `save_breed_research` with your findings
            5. Copy source URLs from the `Exact URL` values returned by `web_search`
            6. Use breed-specific facts from the `web_search` snippets when choosing another tool or saving.
            7. Call at most one tool per turn.

            ## Research Focus

            Look for information about:
            - Origin and history
            - Egg production (color, size, annual quantity)
            - Temperament and personality
            - What makes this breed unique or special

            ## Writing the Description

            The most important output is the `description` field. Write 2-3 sentences that:
            - Capture what makes this breed special and distinctive
            - Help someone decide if this breed is right for them
            - Are engaging and informative, not dry or clinical
            - Highlight the breed's personality, appearance, or unique traits
            - Do NOT include URLs or citations - those go in the separate `sources` field

            **Good example**: "The Silkie is beloved for its extraordinarily fluffy plumage that feels like silk and its gentle, docile nature. These bantam birds make exceptional pets and devoted mothers, often used to hatch eggs from other breeds. With their unique black skin and blue earlobes, Silkies are as striking as they are friendly."

            **Bad example**: "The Silkie is a breed of chicken. It has fluffy feathers. It lays eggs. (Source: example.com)"

            ## Quality Standards

            - Prioritize authoritative sources (breed registries, university extensions, established chicken communities)
            - Only include facts you can verify from your research
            - Use null for optional fields if you cannot verify them
            - Always include at least one source URL
        """.trimIndent()

        private val BREED_RESEARCH_USER_PROMPT = """
            Research the next chicken breed and write a compelling description for our database.
            Start by calling get_next_breed_to_research, then gather information and save your findings.
        """.trimIndent()
    }
}
