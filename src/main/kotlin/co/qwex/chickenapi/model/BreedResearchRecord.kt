package co.qwex.chickenapi.model

import java.time.Instant

data class BreedResearchRecord(
    val runId: String,
    val breedId: Int,
    val breedName: String,
    val startedAt: Instant,
    val completedAt: Instant,
    val durationMillis: Long,
    val outcome: AgentRunOutcome,
    val report: String?,
    val sourcesFound: List<String>,
    val fieldsUpdated: List<String>,
    val errorMessage: String? = null,
)
