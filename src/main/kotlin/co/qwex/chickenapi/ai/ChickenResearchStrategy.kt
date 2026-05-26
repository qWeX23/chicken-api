package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestOnlyCallingTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import ai.koog.prompt.message.Message

private val log = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

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
    var toolCallCount = 0
    var duplicateRetries = 0
    var savedFactJson: String? = null
    var duplicateFeedback: String? = null

    val setupRun by subgraph<String, String>(name = "setup_run") {
        val resetToolCounter by node<String, String>("reset_tool_counter") { input ->
            toolCallCount = 0
            duplicateRetries = 0
            savedFactJson = null
            duplicateFeedback = null
            log.info { "Starting new chicken research run, counters reset" }
            input
        }

        edge(nodeStart forwardTo resetToolCounter)
        edge(resetToolCounter forwardTo nodeFinish transformed { it })
    }

    val llmTurn by subgraph<String, Message.Assistant>(name = "llm_turn") {
        val callLLM by nodeLLMRequest(name = "call_llm")

        edge(nodeStart forwardTo callLLM)
        edge(callLLM forwardTo nodeFinish transformed { it })
    }

    val executeToolsTurn by subgraph<ToolCalls, ReceivedToolResults>(name = "execute_tools_turn") {
        val executeTool by nodeExecuteTools(name = "execute_tool")

        edge(nodeStart forwardTo executeTool)
        edge(executeTool forwardTo nodeFinish transformed { it })
    }

    val toolResultTurn by subgraph<ReceivedToolResults, Message.Assistant>(name = "summarize_tool_result_turn") {
        val captureToolResult by node<ReceivedToolResults, ReceivedToolResults>("capture_tool_result") { toolResults ->
            toolResults.toolResults.forEach { toolResult ->
                val result = toolResult.result.toString()
                val parsedResult = runCatching {
                    json.decodeFromString(SaveChickenFactTool.Result.serializer(), result)
                }.getOrNull()

                if (parsedResult != null) {
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
                }
            }
            toolResults
        }

        val sendToolResult by nodeLLMSendToolResults(name = "summarize_tool_result")

        edge(nodeStart forwardTo captureToolResult)
        edge(captureToolResult forwardTo sendToolResult)
        edge(sendToolResult forwardTo nodeFinish transformed { it })
    }

    val requestSaveFactTurn by subgraph<String, Message.Assistant>(name = "request_save_fact_turn") {
        val requestSaveFact by nodeLLMRequestOnlyCallingTools(name = "request_save_fact")

        edge(nodeStart forwardTo requestSaveFact)
        edge(requestSaveFact forwardTo nodeFinish transformed { it })
    }

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

    edge(nodeStart forwardTo setupRun)
    edge(setupRun forwardTo llmTurn)

    edge(
        llmTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val canCallTool = toolCallCount + requestedToolCalls <= maxToolCalls
            if (canCallTool) {
                toolCallCount += requestedToolCalls
            }
            log.info {
                "Tool call batch of $requestedToolCalls requested (used: $toolCallCount/$maxToolCalls), will execute: $canCallTool"
            }
            canCallTool
        },
    )

    edge(
        llmTurn forwardTo requestSaveFactTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val exceedsMax = toolCallCount + requestedToolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn {
                    "Tool call limit would be exceeded (${toolCallCount + requestedToolCalls} > $maxToolCalls), forcing save_chicken_fact"
                }
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

    edge(llmTurn forwardTo returnResult onTextMessage { true })
    edge(returnResult forwardTo nodeFinish)

    edge(executeToolsTurn forwardTo toolResultTurn)

    edge(
        toolResultTurn forwardTo returnResult onTextMessage { true } onCondition { _ ->
            savedFactJson != null || (duplicateFeedback != null && duplicateRetries > maxDuplicateRetries)
        } transformed { "" },
    )

    edge(
        toolResultTurn forwardTo llmTurn onTextMessage { true } onCondition { _ ->
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
        toolResultTurn forwardTo requestSaveFactTurn onTextMessage { true } onCondition { _ ->
            savedFactJson == null && duplicateFeedback == null && toolCallCount >= maxToolCalls
        } transformed {
            """
            You have reached the tool call limit. Use the summarized research above and call save_chicken_fact now.

            Call the save_chicken_fact tool with:
            - fact: A clear, interesting chicken fact based on your research
            - sourceUrl: The primary source URL that supports the fact
            """.trimIndent()
        },
    )

    edge(
        toolResultTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            if (savedFactJson != null || duplicateFeedback != null) {
                return@onCondition false
            }
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val canCallTool = toolCallCount + requestedToolCalls <= maxToolCalls
            if (canCallTool) {
                toolCallCount += requestedToolCalls
            }
            log.info {
                "Tool call batch of $requestedToolCalls requested after tool result (used: $toolCallCount/$maxToolCalls), will execute: $canCallTool"
            }
            canCallTool
        },
    )

    edge(
        toolResultTurn forwardTo requestSaveFactTurn onToolCalls { true } onCondition { pendingToolCalls ->
            if (savedFactJson != null || duplicateFeedback != null) {
                return@onCondition false
            }
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val exceedsMax = toolCallCount + requestedToolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn {
                    "Tool call limit would be exceeded after tool result (${toolCallCount + requestedToolCalls} > $maxToolCalls), forcing save_chicken_fact"
                }
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

    edge(requestSaveFactTurn forwardTo executeToolsTurn onToolCalls { true })
    edge(requestSaveFactTurn forwardTo returnResult onTextMessage { true })
}

@kotlinx.serialization.Serializable
private data class SavedChickenFactResult(
    val fact: String,
    val sourceUrl: String,
)
