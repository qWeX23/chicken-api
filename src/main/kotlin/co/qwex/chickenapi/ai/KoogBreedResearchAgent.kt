package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import co.qwex.chickenapi.ai.tools.GetBreedDetailsTool
import co.qwex.chickenapi.ai.tools.GetNextBreedToResearchTool
import co.qwex.chickenapi.ai.tools.SaveBreedResearchTool
import co.qwex.chickenapi.ai.tools.WebFetchTool
import co.qwex.chickenapi.ai.tools.WebSearchTool
import co.qwex.chickenapi.config.BreedResearchAgentProperties
import co.qwex.chickenapi.repository.BreedRepository
import io.github.oshai.kotlinlogging.KotlinLogging as OshaiKotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import jakarta.annotation.PreDestroy
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Wraps a Koog single-run agent that researches chicken breeds in depth.
 * The agent selects the breed most in need of research (oldest updatedAt or never updated),
 * researches it thoroughly, and produces a comprehensive report with verified facts.
 */
@Service
class KoogBreedResearchAgent(
    private val properties: BreedResearchAgentProperties,
    private val breedRepository: BreedRepository,
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
            log.info { "Koog breed research agent disabled via configuration." }
        } else {
            val apiKey = properties.apiKey?.takeIf { it.isNotBlank() }
            if (apiKey == null) {
                log.warn { "koog.breed-research-agent.api-key is not set; Breed research agent will be skipped." }
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

                // Create tools
                val getNextBreedTool = GetNextBreedToResearchTool(breedRepository)
                val getBreedDetailsTool = GetBreedDetailsTool(breedRepository)
                val webSearchTool = WebSearchTool(
                    httpClient = webToolClient,
                    baseUrl = sanitizedBaseUrl,
                    defaultMaxResults = properties.webSearchMaxResults,
                    promptExecutor = promptExecutor,
                    model = model,
                    summarizationFocus = "breed-specific facts, characteristics, history, temperament, and egg production details",
                )
                val webFetchTool = WebFetchTool(
                    httpClient = webToolClient,
                    baseUrl = sanitizedBaseUrl,
                    promptExecutor = promptExecutor,
                    model = model,
                    summarizationFocus = "breed-specific characteristics, history, temperament, egg production, and unique traits",
                )
                val saveBreedResearchTool = SaveBreedResearchTool()

                toolRegistry = ToolRegistry {
                    tool(getNextBreedTool)
                    tool(getBreedDetailsTool)
                    tool(webSearchTool)
                    tool(webFetchTool)
                    tool(saveBreedResearchTool)
                }

                agentConfig = AIAgentConfig(
                    prompt = prompt("breed_research_prompt") {
                        system(BREED_RESEARCH_SYSTEM_PROMPT)
                    },
                    model = model!!,
                    maxAgentIterations = properties.maxAgentIterations,
                )

                log.info {
                    "Koog breed research agent initialized with model ${properties.model} using base ${properties.baseUrl}"
                }
            }
        }
    }

    /**
     * Indicates whether the Koog agent is ready to accept runs.
     */
    fun isReady(): Boolean = promptExecutor != null

    /**
     * Creates and runs a new agent instance to research a breed.
     * Returns the JSON output from save_breed_research tool.
     */
    suspend fun researchBreed(): String? {
        val executor = promptExecutor ?: return null
        val registry = toolRegistry ?: return null
        val config = agentConfig ?: return null

        return try {
            log.info { "Creating new agent instance for breed research" }
            val agent =
                AIAgent(
                    promptExecutor = executor,
                    strategy = breedResearchStrategy(
                        maxToolCalls = properties.maxToolCalls,
                    ),
                    toolRegistry = registry,
                    agentConfig = config,
                ) {
                    install(Tracing) {
                        addMessageProcessor(TraceFeatureMessageLogWriter(OshaiKotlinLogging.logger {}))
                    }
                }
            agent.run(BREED_RESEARCH_USER_PROMPT)
        } catch (ex: Exception) {
            log.error(ex) { "Koog agent failed to research breed." }
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

    companion object {
        private val BREED_RESEARCH_SYSTEM_PROMPT = """
            You are a chicken breed research specialist with deep knowledge of poultry breeds from around the world.

            Your task is to thoroughly research a specific chicken breed and produce a comprehensive report.

            ## Workflow

            1. First, call `get_next_breed_to_research` to get the breed you should research
            2. Call `get_breed_details` to see the current information we have on file
            3. Use `web_search` to find authoritative sources about this breed:
               - Search for "<breed name> chicken breed characteristics"
               - Search for "<breed name> chicken egg production facts"
               - Search for "<breed name> chicken history origin"
               - Search for other specific aspects that need verification
            4. Use `web_fetch` to read promising sources in detail
            5. You may call up to 8 research tools (web_search + web_fetch combined)
            6. After gathering sufficient information, call `save_breed_research` to record your findings

            ## Research Goals

            For each breed, your report should cover:
            - **History & Origin**: Where did this breed come from? When was it developed?
            - **Physical Characteristics**: What makes this breed visually distinctive?
            - **Temperament**: How do these chickens behave? Are they friendly, flighty, broody?
            - **Egg Production**: Verify egg color, size, and annual production numbers
            - **Unique Traits**: What makes this breed special or interesting compared to others?

            ## Quality Standards

            - Verify each existing data field against your research
            - If you find conflicting information, note the discrepancy and use the most authoritative source
            - Prioritize sources like:
              - Poultry breed registries and associations
              - University agricultural extensions
              - Established chicken keeping communities (backyardchickens.com, mypetchicken.com)
              - Breed-specific clubs and organizations
            - Always include source URLs for every fact you report

            ## Output Format

            When calling `save_breed_research`, provide:
            - A 2-4 paragraph report summarizing what makes this breed unique
            - Updated values for any fields you can verify or improve
            - A list of all source URLs you used

            Remember: Your research will be used to update our breed database, so accuracy is critical.
        """.trimIndent()

        private val BREED_RESEARCH_USER_PROMPT = """
            Research the next chicken breed that needs updating.
            Start by calling get_next_breed_to_research to find out which breed to research.
            Then gather comprehensive information and save your findings.
        """.trimIndent()
    }
}
