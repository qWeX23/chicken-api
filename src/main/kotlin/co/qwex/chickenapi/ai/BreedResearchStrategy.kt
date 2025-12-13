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
 * Custom breed research strategy that enforces:
 * - A maximum number of tool calls (default 8)
 * - Forces final answer generation when tool limit is reached
 * - Expects save_breed_research tool to be called to return structured JSON output
 */
fun breedResearchStrategy(
    maxToolCalls: Int = 8,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("breed_research") {
    // Per-run tool call counter (reset at start node)
    var toolCalls = 0
    // Track if save_breed_research was called
    var savedResearchJson: String? = null
    // Track the current breed being researched
    var currentBreedId: Int? = null

    // 1) Reset counter on each run
    val resetState by node<String, String>("reset_state") { input ->
        toolCalls = 0
        savedResearchJson = null
        currentBreedId = null
        log.info { "Starting new breed research run, state reset" }
        input
    }

    // 2) Main LLM node (can call tools)
    val callLLM by nodeLLMRequest(
        name = "call_llm",
        allowToolCalls = true,
    )

    // 3) Tool nodes
    val executeTool by nodeExecuteTool()

    // Capture tool results, especially save_breed_research
    val captureToolResult by node<ReceivedToolResult, ReceivedToolResult>("capture_tool_result") { toolResult ->
        val result = toolResult.result.toString()

        // Check if this was save_breed_research by looking for unique fields
        if (result.contains("\"breedId\"") && result.contains("\"report\"") && result.contains("\"sources\"")) {
            savedResearchJson = result
            log.info { "Captured save_breed_research result" }
        }

        // Track breed ID from get_next_breed_to_research
        if (result.contains("\"breedId\"") && result.contains("\"reason\"") && !result.contains("\"report\"")) {
            val breedIdMatch = Regex("\"breedId\"\\s*:\\s*(\\d+)").find(result)
            currentBreedId = breedIdMatch?.groupValues?.get(1)?.toIntOrNull()
            log.info { "Captured breed ID: $currentBreedId" }
        }

        toolResult
    }

    val sendToolResult by nodeLLMSendToolResult()

    // 4) "Force final answer" node (tells LLM to call save_breed_research)
    val requestSaveResearch by nodeLLMRequest(
        name = "request_save_research",
        allowToolCalls = true,
    )

    // 5) Return the saved research JSON or fallback
    val returnResult by node<String, String>("return_result") { _ ->
        savedResearchJson ?: run {
            log.warn { "No saved research found, returning empty result" }
            "{}"
        }
    }

    // ─────────────────────────────
    // Graph wiring
    // ─────────────────────────────

    // Start → reset state → first LLM call
    edge(nodeStart forwardTo resetState)
    edge(resetState forwardTo callLLM)

    // If LLM calls a tool and we're still under the cap → execute it
    edge(
        callLLM forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls
            log.info { "Tool call #$toolCalls requested (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    // If LLM tries to call tools *after* the cap → force save_breed_research call
    edge(
        callLLM forwardTo requestSaveResearch onToolCall { _ ->
            val exceedsMax = toolCalls > maxToolCalls
            if (exceedsMax) {
                log.warn { "Tool call limit exceeded ($toolCalls > $maxToolCalls), forcing save_breed_research" }
            }
            exceedsMax
        } transformed {
            """
            You have gathered enough information about this chicken breed. Now synthesize your research into a comprehensive report.

            Call the save_breed_research tool to record your findings with:
            - breedId: The ID of the breed you researched
            - report: A comprehensive 2-4 paragraph report about what makes this breed unique
            - origin: Verified country/region of origin (or null if unverified)
            - eggColor: Verified egg color (or null if unverified)
            - eggSize: Verified egg size - small/medium/large/extra-large (or null if unverified)
            - temperament: Verified temperament description (or null if unverified)
            - description: An enriched description of the breed (or null if unchanged)
            - numEggs: Verified annual egg production (or null if unverified)
            - sources: List of all source URLs you used in your research

            Your report should cover the breed's history, physical characteristics, temperament, egg production, and any unique or interesting facts.
            """.trimIndent()
        },
    )

    // If LLM gives assistant message without calling save_breed_research → return what we have
    edge(
        callLLM forwardTo returnResult onAssistantMessage { true },
    )
    edge(returnResult forwardTo nodeFinish)

    // Execute tool → capture result → send to LLM
    edge(executeTool forwardTo captureToolResult)
    edge(captureToolResult forwardTo sendToolResult)

    // After tool result, check if save_breed_research was called
    edge(
        sendToolResult forwardTo returnResult onAssistantMessage { savedResearchJson != null },
    )

    // After tool result, if save_breed_research not called and under cap → allow more tools
    edge(
        sendToolResult forwardTo executeTool onToolCall { _ ->
            toolCalls++
            val canCallTool = toolCalls <= maxToolCalls && savedResearchJson == null
            log.info { "Tool call #$toolCalls requested after tool result (max: $maxToolCalls), will execute: $canCallTool" }
            canCallTool
        },
    )

    // After tool result, if save_breed_research not called but cap exceeded → force save
    edge(
        sendToolResult forwardTo requestSaveResearch onToolCall { _ ->
            val exceedsMax = toolCalls > maxToolCalls && savedResearchJson == null
            if (exceedsMax) {
                log.warn { "Tool call limit exceeded after tool result ($toolCalls > $maxToolCalls), forcing save_breed_research" }
            }
            exceedsMax
        } transformed {
            """
            You have gathered sufficient information from your research. It's time to finalize your findings.

            Call the save_breed_research tool to document your discovery with:
            - breedId: The ID of the breed you researched
            - report: Your comprehensive research report (2-4 paragraphs) synthesized from all sources
            - Updated/verified values for any breed fields you can confirm
            - sources: All the URLs you used as references

            The save_breed_research tool stores your research in a structured format that will be used to update the breed database.
            """.trimIndent()
        },
    )

    // Request save research → execute it
    edge(
        requestSaveResearch forwardTo executeTool onToolCall { true },
    )
}
