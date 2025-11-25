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
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Custom chicken research strategy that enforces:
 * - A maximum number of tool calls (default 4)
 * - Forces final answer generation when tool limit is reached
 * - Expects save_chicken_fact tool to be called to return structured JSON output
 */
fun chickenResearchStrategy(
    maxToolCalls: Int = 4,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("chicken_research") {
    // Per-run tool call counter (reset at start node)
    var toolCalls = 0
    // Track if save_chicken_fact was called
    var savedFactJson: String? = null

    // 1) Reset counter on each run
    val resetToolCounter by node<String, String>("reset_tool_counter") { input ->
        toolCalls = 0
        savedFactJson = null
        log.info { "Starting new chicken research run, tool counter reset" }
        input
    }

    // 2) Main LLM node (can call tools)
    val callLLM by nodeLLMRequest(
        name = "call_llm",
        allowToolCalls = true,
    )

    // 3) Tool nodes
    val executeTool by nodeExecuteTool()

    // Capture save_chicken_fact result
    val captureToolResult by node<ReceivedToolResult, ReceivedToolResult>("capture_tool_result") { toolResult ->
        // Check if this was save_chicken_fact by looking at the result
        val result = toolResult.result.toString()
        if (result.contains("\"fact\"") && result.contains("\"sourceUrl\"")) {
            savedFactJson = result
            log.info { "Captured save_chicken_fact result" }
        }
        toolResult
    }

    val sendToolResult by nodeLLMSendToolResult()

    // 4) "Force final answer" node (tells LLM to call save_chicken_fact)
    val requestSaveFact by nodeLLMRequest(
        name = "request_save_fact",
        allowToolCalls = true,
    )

    // 5) Return the saved fact JSON or fallback
    val returnResult by node<String, String>("return_result") { _ ->
        savedFactJson ?: run {
            log.warn { "No saved fact found, returning empty result" }
            "{}"
        }
    }

    // ─────────────────────────────
    // Graph wiring
    // ─────────────────────────────

    // Start → reset tool counter → first LLM call
    edge(nodeStart forwardTo resetToolCounter)
    edge(resetToolCounter forwardTo callLLM)

    // If LLM calls a tool and we're still under the cap → execute it
    edge(
        callLLM forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls
            log.info { "Tool call #$toolCalls requested (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    // If LLM tries to call tools *after* the cap → force save_chicken_fact call
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
            - fact: ONE clear, interesting statement about chickens in PLAIN TEXT (no markdown, no bullets, no asterisks, no formatting)
            - sourceUrl: The most authoritative URL you used as the primary source

            This tool will save your research result in a structured format for later use.
            """.trimIndent()
        },
    )

    // If LLM gives assistant message without calling save_chicken_fact → return what we have
    edge(
        callLLM forwardTo returnResult onAssistantMessage { true },
    )
    edge(returnResult forwardTo nodeFinish)

    // Execute tool → capture result → send to LLM
    edge(executeTool forwardTo captureToolResult)
    edge(captureToolResult forwardTo sendToolResult)

    // After tool result, check if save_chicken_fact was called
    edge(
        sendToolResult forwardTo returnResult onAssistantMessage { savedFactJson != null },
    )

    // After tool result, if save_chicken_fact not called and under cap → allow more tools
    edge(
        sendToolResult forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls && savedFactJson == null
            log.info { "Tool call #$toolCalls requested after tool result (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    // After tool result, if save_chicken_fact not called but cap exceeded → force save
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
            - fact: ONE interesting chicken fact in PLAIN TEXT synthesized from the sources you've reviewed (no markdown, no bullets, no asterisks, no formatting)
            - sourceUrl: The primary URL that best supports this fact

            The save_chicken_fact tool stores your research in a structured format that preserves both the fact and its citation.
            """.trimIndent()
        },
    )

    // Request save fact → execute it
    edge(
        requestSaveFact forwardTo executeTool onToolCall { true },
    )
}
