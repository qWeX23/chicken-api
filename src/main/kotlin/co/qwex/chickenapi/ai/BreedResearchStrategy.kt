package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.environment.ToolResultKind
import co.qwex.chickenapi.ai.tools.SaveBreedResearchTool
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

/**
 * Custom breed research strategy that enforces:
 * - A maximum number of tool calls (default 8)
 * - Forces final answer generation when tool limit is reached
 * - The save_breed_research tool saves directly to the repository
 */
fun breedResearchStrategy(
    saveTool: SaveBreedResearchTool,
    maxToolCalls: Int = 8,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("breed_research") {
    var toolCallCount = 0
    var noToolResponseRetries = 0
    var forcedSaveRetries = 0
    var savedResearchJson: String? = null
    var saveErrorFeedback: String? = null
    var saveRetries = 0
    var retrySaveNoCallRetries = 0

    val saveResearchPrompt =
        """
        Save the completed research now by calling save_breed_research with the selected breed ID,
        a compelling description, every verified optional field, and at least one source URL.
        Do not describe the tool call. Call the tool directly.
        """.trimIndent()
    val retrySaveResearchPrompt =
        """
        Your previous response did not call save_breed_research. Web search is no longer available.
        Use the selected breed, verified details, and source URLs already present in the conversation,
        then call save_breed_research directly. Do not request another tool or describe the call.
        """.trimIndent()
    val continueWithToolsPrompt =
        """
        Continue the workflow using only the available tools. Select the breed, inspect its current details,
        search for evidence, and call save_breed_research when ready. Do not describe or imitate a tool call.
        """.trimIndent()
    val retrySaveWithErrorPrompt = {
        """
        Your previous save_breed_research call failed to parse.

        Error: ${saveErrorFeedback.orEmpty()}

        The most likely cause is that the sources field must be a list of plain URL strings,
        not a list of objects. Re-issue save_breed_research with the same breed ID, description,
        and verified fields, but pass sources as a list of URL strings only.
        Do not request another tool or describe the call.
        """.trimIndent()
    }

    val setupRun by subgraph<String, String>(name = "setup_run") {
        val resetState by node<String, String>("reset_state") { input ->
            toolCallCount = 0
            noToolResponseRetries = 0
            forcedSaveRetries = 0
            savedResearchJson = null
            saveErrorFeedback = null
            saveRetries = 0
            retrySaveNoCallRetries = 0
            log.info { "Starting new breed research run, state reset" }
            input
        }

        edge(nodeStart forwardTo resetState)
        edge(resetState forwardTo nodeFinish transformed { it })
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

    val captureToolResults by node<ReceivedToolResults, ReceivedToolResults>("capture_tool_results") { toolResults ->
        toolResults.toolResults.firstOrNull { it.resultKind !is ToolResultKind.Success }?.let { failedResult ->
            val errorMessage =
                when (val kind = failedResult.resultKind) {
                    is ToolResultKind.Failure -> kind.error?.message
                    is ToolResultKind.ValidationError -> kind.error.message
                    else -> null
                }
            if (failedResult.tool == saveTool.name && saveRetries == 0) {
                saveRetries += 1
                saveErrorFeedback = errorMessage ?: "The tool arguments could not be parsed"
                log.warn {
                    "save_breed_research failed (retry $saveRetries); requesting a corrected save. Error: $saveErrorFeedback"
                }
            } else {
                throw IllegalStateException("Tool ${failedResult.tool} failed")
            }
        }
        toolResults.toolResults.forEach { toolResult ->
            if (toolResult.tool != saveTool.name || toolResult.resultKind !is ToolResultKind.Success) {
                return@forEach
            }
            val parsedResult = runCatching {
                json.decodeFromString(SaveBreedResearchTool.Result.serializer(), toolResult.output)
            }.getOrNull()
            if (parsedResult == null) {
                log.warn { "Ignoring malformed save_breed_research result" }
                return@forEach
            }
            savedResearchJson = toolResult.output
            log.info { "Captured save_breed_research result" }
        }
        toolResults
    }

    val toolResultTurn by subgraph<ReceivedToolResults, Message.Assistant>(name = "summarize_tool_result_turn") {
        val sendToolResult by nodeLLMSendToolResults(name = "summarize_tool_result")

        edge(nodeStart forwardTo sendToolResult)
        edge(sendToolResult forwardTo nodeFinish transformed { it })
    }

    val requestSaveResearchTurn by subgraph<String, Message.Assistant>(
        name = "request_save_research_turn",
        tools = listOf(saveTool),
    ) {
        val requestSaveResearch by nodeLLMRequest(name = "request_save_research")

        edge(nodeStart forwardTo requestSaveResearch)
        edge(requestSaveResearch forwardTo nodeFinish transformed { it })
    }

    val returnResult by node<String, String>("return_result") { _ ->
        savedResearchJson ?: run {
            log.warn { "No saved research found, returning empty result" }
            "{}"
        }
    }

    edge(nodeStart forwardTo setupRun)
    edge(setupRun forwardTo llmTurn)

    edge(
        llmTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val canCallTool = requestedToolCalls == 1 && toolCallCount + requestedToolCalls <= maxToolCalls
            if (canCallTool) {
                toolCallCount += requestedToolCalls
                noToolResponseRetries = 0
            }
            log.info {
                "Tool call batch of $requestedToolCalls requested (used: $toolCallCount/$maxToolCalls), will execute: $canCallTool"
            }
            canCallTool
        },
    )

    edge(
        llmTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val allowTerminalSave =
                requestedToolCalls == 1 &&
                    toolCallCount + requestedToolCalls > maxToolCalls &&
                    pendingToolCalls.toolCalls.single().tool == saveTool.name
            if (allowTerminalSave) {
                log.warn {
                    "Tool call limit reached; allowing terminal save_breed_research"
                }
            }
            allowTerminalSave
        },
    )

    edge(
        llmTurn forwardTo requestSaveResearchTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val rejectOverBudgetCall =
                requestedToolCalls == 1 &&
                    toolCallCount + requestedToolCalls > maxToolCalls &&
                    pendingToolCalls.toolCalls.single().tool != saveTool.name
            if (rejectOverBudgetCall) {
                log.error { "Tool call limit reached; rejecting non-save tool ${pendingToolCalls.toolCalls.single().tool}" }
            }
            rejectOverBudgetCall
        } transformed { saveResearchPrompt },
    )

    edge(
        llmTurn forwardTo returnResult onToolCalls { true } onCondition { pendingToolCalls ->
            val invalidBatch = pendingToolCalls.toolCalls.size != 1
            if (invalidBatch) {
                log.error { "LLM requested ${pendingToolCalls.toolCalls.size} tools in one turn; terminating the run" }
            }
            invalidBatch
        } transformed { "" },
    )

    edge(
        llmTurn forwardTo llmTurn onCondition { _ ->
            val canRetry = noToolResponseRetries == 0 && toolCallCount < maxToolCalls
            if (canRetry) {
                noToolResponseRetries += 1
                log.warn { "LLM response contained no executable tool call; requesting one corrective turn" }
            }
            canRetry
        } transformed { continueWithToolsPrompt },
    )
    edge(llmTurn forwardTo requestSaveResearchTurn onCondition { _ -> true } transformed { saveResearchPrompt })
    edge(returnResult forwardTo nodeFinish)

    edge(executeToolsTurn forwardTo captureToolResults)

    edge(
        captureToolResults forwardTo returnResult onCondition { _ -> savedResearchJson != null } transformed { "" },
    )
    edge(
        captureToolResults forwardTo requestSaveResearchTurn onCondition { _ -> saveErrorFeedback != null }
            transformed { retrySaveWithErrorPrompt() },
    )
    edge(captureToolResults forwardTo toolResultTurn onCondition { _ -> true })

    edge(
        toolResultTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            if (savedResearchJson != null) {
                return@onCondition false
            }
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val canCallTool = requestedToolCalls == 1 && toolCallCount + requestedToolCalls <= maxToolCalls
            if (canCallTool) {
                toolCallCount += requestedToolCalls
                noToolResponseRetries = 0
            }
            log.info {
                "Tool call batch of $requestedToolCalls requested after tool result (used: $toolCallCount/$maxToolCalls), will execute: $canCallTool"
            }
            canCallTool
        },
    )

    edge(
        toolResultTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            if (savedResearchJson != null) {
                return@onCondition false
            }
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val allowTerminalSave =
                requestedToolCalls == 1 &&
                    toolCallCount + requestedToolCalls > maxToolCalls &&
                    pendingToolCalls.toolCalls.single().tool == saveTool.name
            if (allowTerminalSave) {
                log.warn {
                    "Tool call limit reached after tool result; allowing terminal save_breed_research"
                }
            }
            allowTerminalSave
        },
    )

    edge(
        toolResultTurn forwardTo requestSaveResearchTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val rejectOverBudgetCall =
                requestedToolCalls == 1 &&
                    toolCallCount + requestedToolCalls > maxToolCalls &&
                    pendingToolCalls.toolCalls.single().tool != saveTool.name
            if (rejectOverBudgetCall) {
                log.error { "Tool call limit reached; rejecting non-save tool ${pendingToolCalls.toolCalls.single().tool}" }
            }
            rejectOverBudgetCall
        } transformed { saveResearchPrompt },
    )

    edge(
        toolResultTurn forwardTo returnResult onToolCalls { true } onCondition { pendingToolCalls ->
            val invalidBatch = pendingToolCalls.toolCalls.size != 1
            if (invalidBatch) {
                log.error { "LLM requested ${pendingToolCalls.toolCalls.size} tools in one turn; terminating the run" }
            }
            invalidBatch
        } transformed { "" },
    )

    edge(
        toolResultTurn forwardTo llmTurn onCondition { _ ->
            val canRetry = noToolResponseRetries == 0 && toolCallCount < maxToolCalls
            if (canRetry) {
                noToolResponseRetries += 1
                log.warn { "Tool-result response contained no executable tool call; requesting one corrective turn" }
            }
            canRetry
        } transformed { continueWithToolsPrompt },
    )
    edge(toolResultTurn forwardTo requestSaveResearchTurn onCondition { _ -> true } transformed { saveResearchPrompt })

    edge(
        (requestSaveResearchTurn forwardTo executeToolsTurn)
            .onToolCalls { true }
            .onCondition { pendingToolCalls ->
                pendingToolCalls.toolCalls.size == 1 && pendingToolCalls.toolCalls.single().tool == saveTool.name
            },
    )
    edge(
        requestSaveResearchTurn forwardTo requestSaveResearchTurn onCondition { _ ->
            val canRetry =
                if (saveErrorFeedback != null) {
                    val canRetrySave = retrySaveNoCallRetries == 0
                    if (canRetrySave) {
                        retrySaveNoCallRetries += 1
                        log.warn { "Corrected save response did not call save_breed_research; retrying once" }
                    }
                    canRetrySave
                } else {
                    val canRetry = forcedSaveRetries == 0
                    if (canRetry) {
                        forcedSaveRetries += 1
                        log.warn { "Forced save response did not call save_breed_research; retrying once" }
                    }
                    canRetry
                }
            canRetry
        } transformed { if (saveErrorFeedback != null) retrySaveWithErrorPrompt() else retrySaveResearchPrompt },
    )
    edge(requestSaveResearchTurn forwardTo returnResult onCondition { _ -> true } transformed { "" })
}
