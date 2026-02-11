package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Custom chicken research strategy that enforces:
 * - A maximum number of tool calls (default 4)
 * - save_chicken_fact calls run duplicate checks
 * - Duplicate hits restart research with feedback up to maxDuplicateRetries
 */
fun chickenResearchStrategy(
    maxToolCalls: Int = 4,
    maxDuplicateRetries: Int = 3,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("chicken_research") {
    var toolCalls = 0
    var duplicateRetries = 0
    var savedFactJson: String? = null
    var duplicateFeedback: String? = null

    val resetToolCounter by node<String, String>("reset_tool_counter") { input ->
        toolCalls = 0
        duplicateRetries = 0
        savedFactJson = null
        duplicateFeedback = null
        log.info { "Starting new chicken research run, counters reset" }
        input
    }

    val callLLM by nodeLLMRequest(
        name = "call_llm",
        allowToolCalls = true,
    )

    val executeTool by nodeExecuteTool()

    val captureToolResult by node<ReceivedToolResult, ReceivedToolResult>("capture_tool_result") { toolResult ->
        val result = toolResult.result.toString()
        val parsedResult = runCatching {
            json.decodeFromString(SaveChickenFactTool.Result.serializer(), result)
        }.getOrNull()

        if (parsedResult == null) {
            toolResult
        } else {
            if (parsedResult.duplicateCheck.hasHit) {
                duplicateRetries += 1
                duplicateFeedback = json.encodeToString(FactDuplicateCheckResult.serializer(), parsedResult.duplicateCheck)
                savedFactJson = null
                log.warn {
                    "Detected duplicate chicken fact candidate (retry $duplicateRetries/$maxDuplicateRetries), requesting a new fact."
                }
            } else {
                duplicateFeedback = null
                savedFactJson = json.encodeToString(
                    SavedChickenFactResult.serializer(),
                    SavedChickenFactResult(
                        fact = parsedResult.fact,
                        sourceUrl = parsedResult.sourceUrl,
                    ),
                )
                log.info { "Captured save_chicken_fact result with no duplicate hit" }
            }
            toolResult
        }
    }

    val sendToolResult by nodeLLMSendToolResult()

    val requestSaveFact by nodeLLMRequest(
        name = "request_save_fact",
        allowToolCalls = true,
    )

    val returnResult by node<String, String>("return_result") { _ ->
        if (duplicateFeedback != null && duplicateRetries > maxDuplicateRetries) {
            log.error { "Duplicate retry limit exceeded ($duplicateRetries > $maxDuplicateRetries), failing chicken fact run" }
            "{}"
        } else {
            savedFactJson ?: run {
                log.warn { "No saved fact found, returning empty result" }
                "{}"
            }
        }
    }

    edge(nodeStart forwardTo resetToolCounter)
    edge(resetToolCounter forwardTo callLLM)

    edge(
        callLLM forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls
            log.info { "Tool call #$toolCalls requested (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    edge(
        callLLM forwardTo requestSaveFact onToolCall { _ ->
            val exceedsMax = toolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn { "Tool call limit exceeded ($toolCalls > $maxToolCalls), forcing save_chicken_fact" }
            }
            exceedsMax
        } transformed {
            """
            You have gathered enough information. Now synthesize your research into a single, compelling chicken fact.

            Call the save_chicken_fact tool to record your finding with:
            - fact: A clear, interesting statement about chickens based on your research
            - sourceUrl: The most authoritative URL you used as the primary source

            This tool will save your research result in a structured format for later use.
            """.trimIndent()
        },
    )

    edge(
        callLLM forwardTo returnResult onAssistantMessage { true },
    )
    edge(returnResult forwardTo nodeFinish)

    edge(executeTool forwardTo captureToolResult)
    edge(captureToolResult forwardTo sendToolResult)

    edge(
        sendToolResult forwardTo returnResult onAssistantMessage {
            savedFactJson != null || (duplicateFeedback != null && duplicateRetries > maxDuplicateRetries)
        },
    )

    edge(
        sendToolResult forwardTo callLLM onAssistantMessage {
            duplicateFeedback != null && duplicateRetries <= maxDuplicateRetries
        } transformed {
            """
            The proposed fact is too similar to an existing fact already in the database.

            Duplicate check details:
            ${duplicateFeedback.orEmpty()}

            You must produce a genuinely different chicken fact.
            - Do not paraphrase the same core claim.
            - Choose a distinct topic, behavior, historical event, or trivia angle.
            - You may continue researching with web_search/web_fetch if needed.
            - When ready, call save_chicken_fact again with the new fact and source URL.
            """.trimIndent()
        },
    )

    edge(
        sendToolResult forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls && savedFactJson == null
            log.info { "Tool call #$toolCalls requested after tool result (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    edge(
        sendToolResult forwardTo requestSaveFact onToolCall { _ ->
            val exceedsMax = toolCalls > maxToolCalls && savedFactJson == null
            if (exceedsMax) {
                log.warn { "Tool call limit exceeded after tool result ($toolCalls > $maxToolCalls), forcing save_chicken_fact" }
            }
            exceedsMax
        } transformed {
            """
            You have gathered sufficient information from your research. It's time to finalize your findings.

            Call the save_chicken_fact tool to document your discovery with:
            - fact: Your most interesting chicken fact synthesized from the sources you've reviewed
            - sourceUrl: The primary URL that best supports this fact

            The save_chicken_fact tool stores your research in a structured format that preserves both the fact and its citation.
            """.trimIndent()
        },
    )

    edge(
        requestSaveFact forwardTo executeTool onToolCall { true },
    )
}


@kotlinx.serialization.Serializable
private data class SavedChickenFactResult(
    val fact: String,
    val sourceUrl: String,
)

private val json = Json { ignoreUnknownKeys = true }
