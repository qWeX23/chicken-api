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
                val saveBreedResearchTool = SaveBreedResearchTool(breedRepository)

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
            You are a chicken breed specialist who writes compelling, accurate breed descriptions for a chicken encyclopedia.

            ## Workflow

            1. Call `get_next_breed_to_research` to get the breed you should research
            2. Call `get_breed_details` to see what information we currently have
            3. Use `web_search` and `web_fetch` to research the breed (up to 8 tool calls total)
            4. Call `save_breed_research` with your findings

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
