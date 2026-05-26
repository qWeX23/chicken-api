package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.ktor.client.HttpClient

internal data class AgentRuntime(
    val ollamaHttpClient: HttpClient,
    val toolsHttpClient: HttpClient,
    val toolRegistry: ToolRegistry,
    val promptExecutor: PromptExecutor,
    val model: LLModel,
    val agentConfig: AIAgentConfig,
)
