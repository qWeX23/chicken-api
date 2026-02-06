package co.qwex.chickenapi.repository.sheets

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging

class ChickenFactsRowMapper : RowMapper<ChickenFactsRecord> {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    override fun map(row: List<Any?>): ChickenFactsRecord? {
        val runId = row.stringAt(0) ?: return null
        if (runId.equals("runId", ignoreCase = true)) {
            return null
        }

        val startedAt = row.instantAt(1) ?: return null
        val completedAt = row.instantAt(2) ?: return null
        val durationMillis = row.longAt(3) ?: 0L
        val outcomeValue = row.stringAt(4) ?: return null
        val outcome = runCatching { AgentRunOutcome.valueOf(outcomeValue) }
            .onFailure { log.warn(it) { "Unknown AgentRunOutcome '$outcomeValue' for run $runId" } }
            .getOrNull()
            ?: return null

        val embedding = row.stringAt(8, trim = false)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(Double.serializer()), raw) }
                .onFailure { log.debug(it) { "Unable to parse embedding for run $runId" } }
                .getOrNull()
        }

        return ChickenFactsRecord(
            runId = runId,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = durationMillis,
            outcome = outcome,
            fact = row.stringAt(6, trim = false),
            sourceUrl = row.stringAt(7, trim = false),
            factEmbedding = embedding,
            errorMessage = row.stringAt(9, trim = false),
            updatedAt = row.instantAt(10),
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
