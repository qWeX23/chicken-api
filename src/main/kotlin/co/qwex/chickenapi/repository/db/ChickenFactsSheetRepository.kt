package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.time.Instant

private const val CHICKEN_FACTS_RANGE = "chicken_facts!A1:H1"
private const val CHICKEN_FACTS_DATA_RANGE = "chicken_facts!A:H"

@Repository
class ChickenFactsSheetRepository(
    private val sheets: Sheets,
    @Value("\${google.sheets.db.spreadsheetId}") private val spreadsheetId: String,
) {
    private val log = KotlinLogging.logger {}

    fun append(record: ChickenFactsRecord) {
        val row = listOf(
            record.runId,
            record.startedAt.toString(),
            record.completedAt.toString(),
            record.durationMillis,
            record.outcome.name,
            record.factsMarkdown?.length ?: 0,
            record.factsMarkdown.orEmpty(),
            record.errorMessage.orEmpty(),
        )

        val valueRange = ValueRange().setValues(listOf(row))

        sheets.spreadsheets().values()
            .append(spreadsheetId, CHICKEN_FACTS_RANGE, valueRange)
            .setValueInputOption("USER_ENTERED")
            .execute()

        log.debug { "Appended chicken facts run ${record.runId} with outcome ${record.outcome}" }
    }

    fun fetchLatestChickenFact(): ChickenFactsRecord? {
        val rows = try {
            sheets.spreadsheets().values()
                .get(spreadsheetId, CHICKEN_FACTS_DATA_RANGE)
                .execute()
                .getValues()
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        } catch (ex: Exception) {
            log.error(ex) { "Failed to read chicken facts sheet." }
            return null
        }

        if (rows.isEmpty()) {
            log.debug { "Chicken facts sheet returned no rows." }
            return null
        }

        for (row in rows.asReversed()) {
            val record = mapRowToRecord(row)
            if (record != null && record.outcome == AgentRunOutcome.SUCCESS && !record.factsMarkdown.isNullOrBlank()) {
                return record
            }
        }

        log.debug { "No successful chicken fact runs found in sheet." }
        return null
    }

    private fun mapRowToRecord(row: List<Any>): ChickenFactsRecord? {
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

        val factsMarkdown = row.stringAt(6, trim = false)
        val errorMessage = row.stringAt(7, trim = false)

        return ChickenFactsRecord(
            runId = runId,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = durationMillis,
            outcome = outcome,
            factsMarkdown = factsMarkdown,
            errorMessage = errorMessage,
        )
    }

    private fun List<Any>.stringAt(index: Int, trim: Boolean = true): String? {
        val rawValue = getOrNull(index)?.toString() ?: return null
        val value = if (trim) rawValue.trim() else rawValue
        return value.takeIf { it.isNotEmpty() }
    }

    private fun List<Any>.instantAt(index: Int): Instant? =
        stringAt(index)?.let { raw ->
            runCatching { Instant.parse(raw) }
                .onFailure { log.debug(it) { "Unable to parse Instant '$raw' from chicken facts sheet" } }
                .getOrNull()
        }

    private fun List<Any>.longAt(index: Int): Long? {
        val value = getOrNull(index) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
            else -> value.toString().trim().toLongOrNull()
        }
    }
}
