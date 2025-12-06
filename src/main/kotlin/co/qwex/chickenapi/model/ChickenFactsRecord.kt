package co.qwex.chickenapi.model

import java.time.Instant

data class ChickenFactsRecord(
    val runId: String,
    val startedAt: Instant,
    val completedAt: Instant,
    val durationMillis: Long,
    val outcome: AgentRunOutcome,
    val fact: String?,
    val sourceUrl: String?,
    val errorMessage: String? = null,
    val updatedAt: Instant? = null,
)

enum class AgentRunOutcome {
    SUCCESS,
    NO_OUTPUT,
    FAILED,
}
