package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.BreedResearchRecord
import co.qwex.chickenapi.repository.BreedResearchRepository
import co.qwex.chickenapi.repository.sheets.BreedResearchTable
import co.qwex.chickenapi.repository.sheets.SheetsGateway
import co.qwex.chickenapi.repository.sheets.ValueInputOption
import mu.KotlinLogging
import org.springframework.stereotype.Repository

private const val FIELDS_DELIMITER = "|"
private const val SOURCES_DELIMITER = "|"

@Repository
class BreedResearchSheetRepository(
    private val sheetsGateway: SheetsGateway,
    private val table: BreedResearchTable = BreedResearchTable,
) : BreedResearchRepository {
    private val log = KotlinLogging.logger {}

    override fun create(entity: BreedResearchRecord) {
        sheetsGateway.ensureTableExists(table)
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

        sheetsGateway.appendValues(table.appendRange(), listOf(row), ValueInputOption.USER_ENTERED)
        log.debug { "Created breed research run ${entity.runId} for breed ${entity.breedId} with outcome ${entity.outcome}" }
    }

    override fun fetchLatestResearchForBreed(breedId: Int): BreedResearchRecord? {
        sheetsGateway.ensureTableExists(table)
        val rows = try {
            sheetsGateway.getValues(table.dataRange()).filter { it.isNotEmpty() }
        } catch (ex: Exception) {
            log.error(ex) { "Failed to read breed research sheet." }
            return null
        }

        if (rows.isEmpty()) {
            log.debug { "Breed research sheet returned no rows." }
            return null
        }

        for (row in rows.asReversed()) {
            val record = table.mapper.map(row)
            if (record != null && record.breedId == breedId && record.outcome == AgentRunOutcome.SUCCESS) {
                return record
            }
        }

        log.debug { "No successful research runs found for breed $breedId." }
        return null
    }

    override fun fetchAllSuccessfulResearch(): List<BreedResearchRecord> {
        sheetsGateway.ensureTableExists(table)
        val rows = try {
            sheetsGateway.getValues(table.dataRange()).filter { it.isNotEmpty() }
        } catch (ex: Exception) {
            log.error(ex) { "Failed to read breed research sheet." }
            return emptyList()
        }

        if (rows.isEmpty()) {
            log.debug { "Breed research sheet returned no rows." }
            return emptyList()
        }

        return rows
            .mapNotNull(table.mapper::map)
            .filter { it.outcome == AgentRunOutcome.SUCCESS && !it.report.isNullOrBlank() }
            .sortedByDescending { it.completedAt }
    }
}
