package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
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
    // Per-run tool call counter (reset at start node)
    var toolCalls = 0

    // 1) Reset counter on each run
    val resetState by node<String, String>("reset_state") { input ->
        toolCalls = 0
        log.info { "Starting new breed research run, state reset" }
        input
    }

    // 2) Main LLM node (can call tools)
    val callLLM by nodeLLMRequest(
        name = "call_llm",
        allowToolCalls = true,
    )

    // 3) Tool execution
    val executeTool by nodeExecuteTool()

    // 4) Send tool result to LLM
    val sendToolResult by nodeLLMSendToolResult()

    // 5) "Force final answer" node (tells LLM to call save_breed_research)
    val requestSaveResearch by nodeLLMRequest(
        name = "request_save_research",
        allowToolCalls = true,
    )

    // 6) Return node (agent finished)
    val returnResult by node<String, String>("return_result") { message ->
        log.info { "Agent finished with message: ${message.take(100)}..." }
        message
    }

    // ─────────────────────────────
    // Graph wiring
    // ─────────────────────────────

    // Start → reset state → first LLM call
    edge(nodeStart forwardTo resetState)
    edge(resetState forwardTo callLLM)

    // callLLM: tool call under cap → execute
    edge(
        callLLM forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls
            log.info { "Tool call #$toolCalls requested (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    // callLLM: tool call over cap → force save
    edge(
        callLLM forwardTo requestSaveResearch onToolCall { _ ->
            val exceedsMax = toolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn { "Tool call limit exceeded ($toolCalls > $maxToolCalls), forcing save_breed_research" }
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

    // callLLM: assistant message → done
    edge(
        callLLM forwardTo returnResult onAssistantMessage { true },
    )
    edge(returnResult forwardTo nodeFinish)

    // executeTool → sendToolResult → back to LLM
    edge(executeTool forwardTo sendToolResult)

    // sendToolResult: tool call under cap → execute (loop)
    edge(
        sendToolResult forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls
            log.info { "Tool call #$toolCalls requested after tool result (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    // sendToolResult: tool call over cap → force save
    edge(
        sendToolResult forwardTo requestSaveResearch onToolCall { _ ->
            val exceedsMax = toolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn { "Tool call limit exceeded after tool result ($toolCalls > $maxToolCalls), forcing save_breed_research" }
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

    // sendToolResult: assistant message → done
    edge(
        sendToolResult forwardTo returnResult onAssistantMessage { true },
    )

    // requestSaveResearch → execute it
    edge(
        requestSaveResearch forwardTo executeTool onToolCall { true },
    )

    // requestSaveResearch: if LLM gives assistant message instead → done
    edge(
        requestSaveResearch forwardTo returnResult onAssistantMessage { true },
    )
}
