package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.BreedResearchRecord
import co.qwex.chickenapi.repository.BreedResearchRepository
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.time.Instant

private const val BREED_RESEARCH_RANGE = "breed_research_runs!A1:K1"
private const val BREED_RESEARCH_DATA_RANGE = "breed_research_runs!A:K"
private const val FIELDS_DELIMITER = "|"
private const val SOURCES_DELIMITER = "|"

@Repository
class BreedResearchSheetRepository(
    private val sheets: Sheets,
    @Value("\${google.sheets.db.spreadsheetId}") private val spreadsheetId: String,
) : BreedResearchRepository {
    private val log = KotlinLogging.logger {}

    override fun create(entity: BreedResearchRecord) {
        val row = listOf(
            entity.runId,
            entity.breedId,
            entity.breedName,
            entity.startedAt.toString(),
            entity.completedAt.toString(),
            entity.durationMillis,
            entity.outcome.name,
            entity.report?.length ?: 0,
            entity.report.orEmpty(),
            entity.sourcesFound.joinToString(SOURCES_DELIMITER),
            entity.fieldsUpdated.joinToString(FIELDS_DELIMITER),
            entity.errorMessage.orEmpty(),
        )

        val valueRange = ValueRange().setValues(listOf(row))

        sheets.spreadsheets().values()
            .append(spreadsheetId, BREED_RESEARCH_RANGE, valueRange)
            .setValueInputOption("USER_ENTERED")
            .execute()

        log.debug { "Created breed research run ${entity.runId} for breed ${entity.breedId} with outcome ${entity.outcome}" }
    }

    override fun fetchLatestResearchForBreed(breedId: Int): BreedResearchRecord? {
        val rows = try {
            sheets.spreadsheets().values()
                .get(spreadsheetId, BREED_RESEARCH_DATA_RANGE)
                .execute()
                .getValues()
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        } catch (ex: Exception) {
            log.error(ex) { "Failed to read breed research sheet." }
            return null
        }

        if (rows.isEmpty()) {
            log.debug { "Breed research sheet returned no rows." }
            return null
        }

        for (row in rows.asReversed()) {
            val record = mapRowToRecord(row)
            if (record != null && record.breedId == breedId && record.outcome == AgentRunOutcome.SUCCESS) {
                return record
            }
        }

        log.debug { "No successful research runs found for breed $breedId." }
        return null
    }

    override fun fetchAllSuccessfulResearch(): List<BreedResearchRecord> {
        val rows = try {
            sheets.spreadsheets().values()
                .get(spreadsheetId, BREED_RESEARCH_DATA_RANGE)
                .execute()
                .getValues()
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        } catch (ex: Exception) {
            log.error(ex) { "Failed to read breed research sheet." }
            return emptyList()
        }

        if (rows.isEmpty()) {
            log.debug { "Breed research sheet returned no rows." }
            return emptyList()
        }

        return rows
            .mapNotNull { mapRowToRecord(it) }
            .filter { it.outcome == AgentRunOutcome.SUCCESS && !it.report.isNullOrBlank() }
            .sortedByDescending { it.completedAt }
    }

    private fun mapRowToRecord(row: List<Any>): BreedResearchRecord? {
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

        val report = row.stringAt(8, trim = false)
        val sourcesFound = row.stringAt(9, trim = false)?.split(SOURCES_DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()
        val fieldsUpdated = row.stringAt(10, trim = false)?.split(FIELDS_DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()
        val errorMessage = row.stringAt(11, trim = false)

        return BreedResearchRecord(
            runId = runId,
            breedId = breedId,
            breedName = breedName,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = durationMillis,
            outcome = outcome,
            report = report,
            sourcesFound = sourcesFound,
            fieldsUpdated = fieldsUpdated,
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
                .onFailure { log.debug(it) { "Unable to parse Instant '$raw' from breed research sheet" } }
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

    private fun List<Any>.intAt(index: Int): Int? {
        val value = getOrNull(index) ?: return null
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            else -> value.toString().trim().toIntOrNull()
        }
    }
}
