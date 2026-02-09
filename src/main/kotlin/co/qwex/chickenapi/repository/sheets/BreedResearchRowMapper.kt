package co.qwex.chickenapi.repository.sheets

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.BreedResearchRecord
import mu.KotlinLogging

private const val FIELDS_DELIMITER = "|"
private const val SOURCES_DELIMITER = "|"

class BreedResearchRowMapper : RowMapper<BreedResearchRecord> {
    private val log = KotlinLogging.logger {}

    override fun map(row: List<Any?>): BreedResearchRecord? {
        val runId = row.stringAt(0) ?: return null
        if (runId.equals("runId", ignoreCase = true)) {
            return null
        }

        val breedId = row.intAt(1) ?: return null
        val breedName = row.stringAt(2) ?: return null
        val startedAt = row.instantAt(3) ?: return null
        val completedAt = row.instantAt(4) ?: return null
        val durationMillis = row.longAt(5) ?: 0L
        val outcomeValue = row.stringAt(6) ?: return null
        val outcome = runCatching { AgentRunOutcome.valueOf(outcomeValue) }
            .onFailure { log.warn(it) { "Unknown AgentRunOutcome '$outcomeValue' for run $runId" } }
            .getOrNull()
            ?: return null

        return BreedResearchRecord(
            runId = runId,
            breedId = breedId,
            breedName = breedName,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = durationMillis,
            outcome = outcome,
            report = row.stringAt(8, trim = false),
            sourcesFound = row.stringListAt(9, SOURCES_DELIMITER).orEmpty(),
            fieldsUpdated = row.stringListAt(10, FIELDS_DELIMITER).orEmpty(),
            errorMessage = row.stringAt(11, trim = false),
        )
    }
}

private fun List<Any?>.longAt(index: Int): Long? {
    val value = getOrNull(index) ?: return null
    return when (value) {
        is Number -> value.toLong()
        is String -> value.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
        else -> value.toString().trim().toLongOrNull()
    }
}
