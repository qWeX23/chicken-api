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
import ai.koog.agents.core.tools.ToolBase
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }
private val duplicateResearchAngles = listOf(
    "vocal communication between hens and chicks, excluding face recognition",
    "feeding, dust-bathing, movement, or sleep behavior, excluding ultraviolet vision",
    "domestication archaeology, cultural history, records, or an unusual breed-specific trait",
)

/**
 * Custom chicken research strategy that enforces:
 * - A maximum number of tool calls (default 4)
 * - save_chicken_fact calls run duplicate checks
 * - Duplicate hits restart research with feedback up to maxDuplicateRetries
 */
fun chickenResearchStrategy(
    saveTool: SaveChickenFactTool,
    researchTool: ToolBase<*, *>,
    maxToolCalls: Int = 4,
    maxDuplicateRetries: Int = 3,
    retrySearchBudgetPerDuplicate: Int = 3,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("chicken_research") {
    var toolCallCount = 0
    var duplicateRetries = 0
    var noToolResponseRetries = 0
    var forcedSaveRetries = 0
    var duplicateResearchRetries = 0
    var duplicateResearchRequired = false
    var savedFactJson: String? = null
    var duplicateFeedback: String? = null
    var coveredTopics: List<String> = emptyList()
    var retrySearchBudget = 0

    val saveFactPrompt =
        """
        You have gathered enough information. Call save_chicken_fact now with:
        - fact: A clear, interesting chicken fact based on your research
        - sourceUrl: The primary source URL that supports the fact

        Do not describe the tool call. Call the tool directly.
        """.trimIndent()
    val retrySaveFactPrompt =
        """
        Your previous response did not call save_chicken_fact. Web search is no longer available.
        Choose the best supported fact and exact source URL already present in the conversation,
        then call save_chicken_fact directly. Do not request another tool or describe the call.
        """.trimIndent()
    val continueWithToolsPrompt =
        """
        Continue the workflow using only the available tools. Call web_search for more evidence or
        save_chicken_fact when the fact and primary source URL are ready. Do not describe or imitate a tool call.
        """.trimIndent()
    val duplicateResearchPrompt = { angle: String ->
        """
            The proposed fact is too similar to an existing fact already in the database.

            Duplicate check details:
            ${duplicateFeedback.orEmpty()}

            Topics already covered by existing facts in the database:
            ${coveredTopics.joinToString("\n- ", prefix = "- ") { it }}

            Start over with this required research angle: $angle.
            Call ${researchTool.name} now with a targeted query for a genuinely different fact.
            Do not save or paraphrase any fact listed in the duplicate details, and do not reuse any of the covered topics.
        """.trimIndent()
    }

    val setupRun by subgraph<String, String>(name = "setup_run") {
        val resetToolCounter by node<String, String>("reset_tool_counter") { input ->
            toolCallCount = 0
            duplicateRetries = 0
            noToolResponseRetries = 0
            forcedSaveRetries = 0
            duplicateResearchRetries = 0
            duplicateResearchRequired = false
            savedFactJson = null
            duplicateFeedback = null
            coveredTopics = emptyList()
            retrySearchBudget = 0
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

    val captureToolResults by node<ReceivedToolResults, ReceivedToolResults>("capture_tool_results") { toolResults ->
        toolResults.toolResults.firstOrNull { it.resultKind !is ToolResultKind.Success }?.let { failedResult ->
            throw IllegalStateException("Tool ${failedResult.tool} failed")
        }
        toolResults.toolResults.forEach { toolResult ->
            if (toolResult.tool != saveTool.name || toolResult.resultKind !is ToolResultKind.Success) {
                return@forEach
            }
            val parsedResult = runCatching {
                json.decodeFromString(SaveChickenFactTool.Result.serializer(), toolResult.output)
            }.getOrNull()
            if (parsedResult == null) {
                log.warn { "Ignoring malformed save_chicken_fact result" }
                return@forEach
            }

            if (parsedResult.duplicateCheck.hasHit) {
                duplicateRetries += 1
                toolCallCount = 0
                forcedSaveRetries = 0
                duplicateResearchRetries = 0
                duplicateResearchRequired = duplicateRetries <= maxDuplicateRetries
                duplicateFeedback = json.encodeToString(FactDuplicateCheckResult.serializer(), parsedResult.duplicateCheck)
                coveredTopics = parsedResult.duplicateCheck.coveredTopics
                retrySearchBudget = retrySearchBudgetPerDuplicate
                savedFactJson = null
                log.warn {
                    "Detected duplicate chicken fact candidate (retry $duplicateRetries/$maxDuplicateRetries), requesting a new fact."
                }
            } else {
                duplicateResearchRequired = false
                duplicateFeedback = null
                coveredTopics = emptyList()
                retrySearchBudget = 0
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
        toolResults
    }

    val toolResultTurn by subgraph<ReceivedToolResults, Message.Assistant>(name = "summarize_tool_result_turn") {
        val sendToolResult by nodeLLMSendToolResults(name = "summarize_tool_result")

        edge(nodeStart forwardTo sendToolResult)
        edge(sendToolResult forwardTo nodeFinish transformed { it })
    }

    val requestSaveFactTurn by subgraph<String, Message.Assistant>(
        name = "request_save_fact_turn",
        tools = listOf(saveTool),
    ) {
        val requestSaveFact by nodeLLMRequest(name = "request_save_fact")

        edge(nodeStart forwardTo requestSaveFact)
        edge(requestSaveFact forwardTo nodeFinish transformed { it })
    }

    val restartResearchTurn by subgraph<String, Message.Assistant>(
        name = "restart_research_turn",
        tools = listOf(researchTool),
    ) {
        val restartResearch by nodeLLMRequest(name = "restart_research")

        edge(nodeStart forwardTo restartResearch)
        edge(restartResearch forwardTo nodeFinish transformed { it })
    }

    val returnResult by node<String, String>("return_result") { _ ->
        if (duplicateFeedback != null && duplicateRetries > maxDuplicateRetries) {
            log.error { "Duplicate retry limit exceeded ($duplicateRetries > $maxDuplicateRetries), failing chicken fact run" }
            ""
        } else {
            savedFactJson ?: run {
                log.warn { "No saved fact found, returning empty result" }
                ""
            }
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
                    "Tool call limit reached; allowing terminal save_chicken_fact"
                }
            }
            allowTerminalSave
        },
    )

    edge(
        llmTurn forwardTo requestSaveFactTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val rejectOverBudgetCall =
                requestedToolCalls == 1 &&
                    toolCallCount + requestedToolCalls > maxToolCalls &&
                    pendingToolCalls.toolCalls.single().tool != saveTool.name
            if (rejectOverBudgetCall) {
                log.error { "Tool call limit reached; rejecting non-save tool ${pendingToolCalls.toolCalls.single().tool}" }
            }
            rejectOverBudgetCall
        } transformed { saveFactPrompt },
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
    edge(llmTurn forwardTo requestSaveFactTurn onCondition { _ -> true } transformed { saveFactPrompt })
    edge(returnResult forwardTo nodeFinish)

    edge(executeToolsTurn forwardTo captureToolResults)

    edge(
        captureToolResults forwardTo returnResult onCondition { _ ->
            savedFactJson != null || (duplicateFeedback != null && duplicateRetries > maxDuplicateRetries)
        } transformed { "" },
    )
    edge(
        captureToolResults forwardTo restartResearchTurn onCondition { _ ->
            duplicateResearchRequired
        } transformed { duplicateResearchPrompt(duplicateResearchAngles[(duplicateRetries - 1).coerceAtLeast(0) % duplicateResearchAngles.size]) },
    )
    edge(captureToolResults forwardTo toolResultTurn onCondition { _ -> !duplicateResearchRequired })

    edge(
        (restartResearchTurn forwardTo executeToolsTurn)
            .onToolCalls { true }
            .onCondition { pendingToolCalls ->
                val canResearch =
                    pendingToolCalls.toolCalls.size == 1 &&
                        pendingToolCalls.toolCalls.single().tool == researchTool.name
                if (canResearch) {
                    toolCallCount = (maxToolCalls - retrySearchBudget).coerceAtLeast(0) + 1
                    noToolResponseRetries = 0
                    duplicateResearchRequired = false
                }
                canResearch
            },
    )
    edge(
        restartResearchTurn forwardTo restartResearchTurn onCondition { _ ->
            val canRetry = duplicateResearchRetries == 0
            if (canRetry) {
                duplicateResearchRetries += 1
                log.warn { "Duplicate-research response did not call ${researchTool.name}; retrying once" }
            }
            canRetry
        } transformed { duplicateResearchPrompt(duplicateResearchAngles[(duplicateRetries - 1).coerceAtLeast(0) % duplicateResearchAngles.size]) },
    )
    edge(restartResearchTurn forwardTo returnResult onCondition { _ -> true } transformed { "" })

    edge(
        toolResultTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            if (savedFactJson != null) {
                return@onCondition false
            }
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val canCallTool = requestedToolCalls == 1 && toolCallCount + requestedToolCalls <= maxToolCalls
            if (canCallTool) {
                toolCallCount += requestedToolCalls
                noToolResponseRetries = 0
                duplicateFeedback = null
            }
            log.info {
                "Tool call batch of $requestedToolCalls requested after tool result (used: $toolCallCount/$maxToolCalls), will execute: $canCallTool"
            }
            canCallTool
        },
    )

    edge(
        toolResultTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            if (savedFactJson != null) {
                return@onCondition false
            }
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val allowTerminalSave =
                requestedToolCalls == 1 &&
                    toolCallCount + requestedToolCalls > maxToolCalls &&
                    pendingToolCalls.toolCalls.single().tool == saveTool.name
            if (allowTerminalSave) {
                log.warn {
                    "Tool call limit reached after tool result; allowing terminal save_chicken_fact"
                }
            }
            allowTerminalSave
        },
    )

    edge(
        toolResultTurn forwardTo requestSaveFactTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val rejectOverBudgetCall =
                requestedToolCalls == 1 &&
                    toolCallCount + requestedToolCalls > maxToolCalls &&
                    pendingToolCalls.toolCalls.single().tool != saveTool.name
            if (rejectOverBudgetCall) {
                log.error { "Tool call limit reached; rejecting non-save tool ${pendingToolCalls.toolCalls.single().tool}" }
            }
            rejectOverBudgetCall
        } transformed { saveFactPrompt },
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
            val canRetry = duplicateFeedback == null && noToolResponseRetries == 0 && toolCallCount < maxToolCalls
            if (canRetry) {
                noToolResponseRetries += 1
                log.warn { "Tool-result response contained no executable tool call; requesting one corrective turn" }
            }
            canRetry
        } transformed { continueWithToolsPrompt },
    )
    edge(toolResultTurn forwardTo requestSaveFactTurn onCondition { _ -> true } transformed { saveFactPrompt })

    edge(
        (requestSaveFactTurn forwardTo executeToolsTurn)
            .onToolCalls { true }
            .onCondition { pendingToolCalls ->
                pendingToolCalls.toolCalls.size == 1 && pendingToolCalls.toolCalls.single().tool == saveTool.name
            },
    )
    edge(
        requestSaveFactTurn forwardTo requestSaveFactTurn onCondition { _ ->
            val canRetry = forcedSaveRetries == 0
            if (canRetry) {
                forcedSaveRetries += 1
                log.warn { "Forced save response did not call save_chicken_fact; retrying once" }
            }
            canRetry
        } transformed { retrySaveFactPrompt },
    )
    edge(requestSaveFactTurn forwardTo returnResult onCondition { _ -> true } transformed { "" })
}

@kotlinx.serialization.Serializable
private data class SavedChickenFactResult(
    val fact: String,
    val sourceUrl: String,
)
