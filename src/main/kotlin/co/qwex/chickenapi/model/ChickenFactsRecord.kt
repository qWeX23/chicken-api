package co.qwex.chickenapi.model

import java.time.Instant

data class ChickenFactsRecord(
    val runId: String,
    val startedAt: Instant,
    val completedAt: Instant,
    val durationMillis: Long,
    val outcome: AgentRunOutcome,
    val factsMarkdown: String?,
    val errorMessage: String? = null,
)

enum class AgentRunOutcome {
    SUCCESS,
    NO_OUTPUT,
    FAILED,
}
