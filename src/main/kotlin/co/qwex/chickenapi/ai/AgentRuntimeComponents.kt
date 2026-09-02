package co.qwex.chickenapi.ai

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

internal data class AgentRuntimeComponents(
    val promptExecutor: PromptExecutor,
    val model: LLModel,
)
