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

        sheetsGateway.appendValues(table.appendRange(), listOf(row), ValueInputOption.RAW)
        log.debug { "Created breed research run ${entity.runId} for breed ${entity.breedId} with outcome ${entity.outcome}" }
    }

    override fun fetchLatestResearchForBreed(breedId: Int): BreedResearchRecord? {
        return fetchRecords()
            .filter { it.breedId == breedId && it.outcome == AgentRunOutcome.SUCCESS }
            .maxByOrNull { it.completedAt }
    }

    override fun fetchAllSuccessfulResearch(): List<BreedResearchRecord> {
        return fetchRecords()
            .filter { it.outcome == AgentRunOutcome.SUCCESS && !it.report.isNullOrBlank() }
            .sortedByDescending { it.completedAt }
    }

    override fun fetchLatestRun(): BreedResearchRecord? =
        fetchRecords().maxByOrNull { it.completedAt }

    private fun fetchRecords(): List<BreedResearchRecord> {
        sheetsGateway.ensureTableExists(table)
        return sheetsGateway.getValues(table.dataRange())
            .filter { it.isNotEmpty() }
            .mapNotNull(table.mapper::map)
    }
}
