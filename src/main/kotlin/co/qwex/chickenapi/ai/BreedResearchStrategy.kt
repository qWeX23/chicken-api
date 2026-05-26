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
import ai.koog.prompt.message.Message
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Custom breed research strategy that enforces:
 * - A maximum number of tool calls (default 8)
 * - Forces final answer generation when tool limit is reached
 * - The save_breed_research tool saves directly to the repository
 */
fun breedResearchStrategy(
    maxToolCalls: Int = 8,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("breed_research") {
    var toolCallCount = 0
    var savedResearchJson: String? = null

    val setupRun by subgraph<String, String>(name = "setup_run") {
        val resetState by node<String, String>("reset_state") { input ->
            toolCallCount = 0
            savedResearchJson = null
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

    val toolResultTurn by subgraph<ReceivedToolResults, Message.Assistant>(name = "summarize_tool_result_turn") {
        val captureToolResult by node<ReceivedToolResults, ReceivedToolResults>("capture_tool_result") { toolResults ->
            toolResults.toolResults.forEach { toolResult ->
                val result = toolResult.result.toString()
                if (result.contains("\"success\"") && result.contains("\"breedId\"")) {
                    savedResearchJson = result
                    log.info { "Captured save_breed_research result" }
                }
            }
            toolResults
        }

        val sendToolResult by nodeLLMSendToolResults(name = "summarize_tool_result")

        edge(nodeStart forwardTo captureToolResult)
        edge(captureToolResult forwardTo sendToolResult)
        edge(sendToolResult forwardTo nodeFinish transformed { it })
    }

    val requestSaveResearchTurn by subgraph<String, Message.Assistant>(name = "request_save_research_turn") {
        val requestSaveResearch by nodeLLMRequestOnlyCallingTools(name = "request_save_research")

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
        llmTurn forwardTo requestSaveResearchTurn onToolCalls { true } onCondition { pendingToolCalls ->
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val exceedsMax = toolCallCount + requestedToolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn {
                    "Tool call limit would be exceeded (${toolCallCount + requestedToolCalls} > $maxToolCalls), forcing save_breed_research"
                }
            }
            exceedsMax
        } transformed {
            """
            You have gathered enough information. Now save your findings.

            Call save_breed_research with:
            - breedId: The breed ID you researched
            - description: A compelling 2-3 sentence description of what makes this breed unique and special
            - origin, eggColor, eggSize, temperament, numEggs: Include if verified (null if not)
            - sources: URLs you used for research
            """.trimIndent()
        },
    )

    edge(llmTurn forwardTo returnResult onTextMessage { true })
    edge(returnResult forwardTo nodeFinish)

    edge(executeToolsTurn forwardTo toolResultTurn)

    edge(
        toolResultTurn forwardTo returnResult onCondition { _ -> savedResearchJson != null } transformed { "" },
    )

    edge(
        toolResultTurn forwardTo executeToolsTurn onToolCalls { true } onCondition { pendingToolCalls ->
            if (savedResearchJson != null) {
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
        toolResultTurn forwardTo requestSaveResearchTurn onToolCalls { true } onCondition { pendingToolCalls ->
            if (savedResearchJson != null) {
                return@onCondition false
            }
            val requestedToolCalls = pendingToolCalls.toolCalls.size
            val exceedsMax = toolCallCount + requestedToolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn {
                    "Tool call limit would be exceeded after tool result (${toolCallCount + requestedToolCalls} > $maxToolCalls), forcing save_breed_research"
                }
            }
            exceedsMax
        } transformed {
            """
            Time to save your research findings.

            Call save_breed_research with:
            - breedId: The breed ID you researched
            - description: A compelling 2-3 sentence description of what makes this breed unique and special
            - origin, eggColor, eggSize, temperament, numEggs: Include if verified (null if not)
            - sources: URLs you used for research
            """.trimIndent()
        },
    )

    edge(toolResultTurn forwardTo returnResult onTextMessage { true })

    edge(requestSaveResearchTurn forwardTo executeToolsTurn onToolCalls { true })
    edge(requestSaveResearchTurn forwardTo returnResult onTextMessage { true })
}
