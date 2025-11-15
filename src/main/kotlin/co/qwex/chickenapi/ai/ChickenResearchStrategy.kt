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
 * Custom chicken research strategy that enforces:
 * - A maximum number of tool calls (default 4)
 * - Forces final answer generation when tool limit is reached
 * - Validates final answers have proper markdown bullet format with URLs
 * - Fixes malformed answers automatically
 */
fun chickenResearchStrategy(
    maxToolCalls: Int = 4,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("chicken_research") {
    // Per-run tool call counter (reset at start node)
    var toolCalls = 0

    // 1) Reset counter on each run
    val resetToolCounter by node<String, String>("reset_tool_counter") { input ->
        toolCalls = 0
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
    val sendToolResult by nodeLLMSendToolResult()

    // 4) "Force final answer" node (LLM, *no* tools)
    val requestFinalNoTools by nodeLLMRequest(
        name = "request_final_no_tools",
        allowToolCalls = false,
    )

    // 5) Answer validator / fixer
    val validateAnswer by node<String, String>("validate_answer") { rawAnswer ->
        val trimmed = rawAnswer.trim()
        val hasBullet = trimmed.lines().any { it.trim().startsWith("- ") }
        val hasUrl = "http" in trimmed

        if (trimmed.isNotEmpty() && hasBullet && hasUrl) {
            // Looks like a proper markdown bullet list with URLs → accept as-is
            log.info { "Answer validated successfully with bullets and URLs" }
            trimmed
        } else {
            // One last no-tools turn to "fix" the answer into the right format
            log.warn { "Answer validation failed, attempting to fix format" }
            llm.writeSession {
                appendPrompt {
                    user(
                        """
                        The previous response was not a valid final answer.
                        Using ONLY what you already know in this conversation,
                        produce a SHORT markdown bullet list of cool, factual chicken facts.

                        Requirements:
                        - Each bullet starts with "- "
                        - Each bullet includes at least one source URL you actually used
                        - No extra commentary, just the bullet list
                        """.trimIndent(),
                    )
                }

                val resp = requestLLMWithoutTools()
                log.info { "Fixed answer generated" }
                resp.content
            }
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

    // If LLM tries to call tools *after* the cap → force final, no tools
    edge(
        callLLM forwardTo requestFinalNoTools onToolCall { _ ->
            val exceedsMax = toolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn { "Tool call limit exceeded ($toolCalls > $maxToolCalls), forcing final answer" }
            }
            exceedsMax
        } transformed {
            """
            You have already used enough tools.
            Now STOP calling tools and synthesize the final answer as a SHORT markdown bullet list.
            Each bullet MUST include a source URL you actually used.
            """.trimIndent()
        },
    )

    // If LLM already gives a normal assistant message (no tools) → validate & finish
    edge(
        callLLM forwardTo validateAnswer onAssistantMessage { true },
    )
    edge(validateAnswer forwardTo nodeFinish)

    // Execute tool → send result back to LLM
    edge(executeTool forwardTo sendToolResult)

    // After tool result, if LLM wants more tools and we're under cap → run again
    edge(
        sendToolResult forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls
            log.info { "Tool call #$toolCalls requested after tool result (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    // After tool result, if LLM wants more tools but cap exceeded → force final, no tools
    edge(
        sendToolResult forwardTo requestFinalNoTools onToolCall { _ ->
            val exceedsMax = toolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn { "Tool call limit exceeded after tool result ($toolCalls > $maxToolCalls), forcing final answer" }
            }
            exceedsMax
        } transformed {
            """
            You have already used enough tools.
            Now STOP calling tools and synthesize the final answer as a SHORT markdown bullet list.
            Each bullet MUST include a source URL you actually used.
            """.trimIndent()
        },
    )

    // After tool result, if LLM gives an assistant message → validate & finish
    edge(
        sendToolResult forwardTo validateAnswer onAssistantMessage { true },
    )

    // Final no-tools request → validate & finish
    edge(
        requestFinalNoTools forwardTo validateAnswer onAssistantMessage { true },
    )
}
