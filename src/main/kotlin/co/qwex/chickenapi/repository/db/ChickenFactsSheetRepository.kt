package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.ChickenFactsRepository
import co.qwex.chickenapi.repository.sheets.ChickenFactsTable
import co.qwex.chickenapi.repository.sheets.SheetsGateway
import co.qwex.chickenapi.repository.sheets.ValueInputOption
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ChickenFactsSheetRepository(
    private val sheetsGateway: SheetsGateway,
    private val table: ChickenFactsTable = ChickenFactsTable,
) : ChickenFactsRepository {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    override fun create(entity: ChickenFactsRecord) {
        sheetsGateway.ensureTableExists(table)
        val updatedAt = Instant.now()
        val row = listOf(
            entity.runId,
            entity.startedAt.toString(),
            entity.completedAt.toString(),
            entity.durationMillis,
            entity.outcome.name,
            entity.fact?.length ?: 0,
            entity.fact.orEmpty(),
            entity.sourceUrl.orEmpty(),
            entity.factEmbedding?.let { json.encodeToString(ListSerializer(Double.serializer()), it) }.orEmpty(),
            entity.errorMessage.orEmpty(),
            updatedAt.toString(),
        )

        sheetsGateway.appendValues(table.appendRange(), listOf(row), ValueInputOption.USER_ENTERED)
        log.debug { "Created chicken facts run ${entity.runId} with outcome ${entity.outcome}" }
    }

    override fun fetchLatestChickenFact(): ChickenFactsRecord? {
        sheetsGateway.ensureTableExists(table)
        val rows = try {
            sheetsGateway.getValues(table.dataRange()).filter { it.isNotEmpty() }
        } catch (ex: Exception) {
            log.error(ex) { "Failed to read chicken facts sheet." }
            return null
        }

        if (rows.isEmpty()) {
            log.debug { "Chicken facts sheet returned no rows." }
            return null
        }

        for (row in rows.asReversed()) {
            val record = table.mapper.map(row)
            if (record != null && record.outcome == AgentRunOutcome.SUCCESS && !record.fact.isNullOrBlank()) {
                return record
            }
        }

        log.debug { "No successful chicken fact runs found in sheet." }
        return null
    }

    override fun fetchAllSuccessfulChickenFacts(): List<ChickenFactsRecord> {
        sheetsGateway.ensureTableExists(table)
        val rows = try {
            sheetsGateway.getValues(table.dataRange()).filter { it.isNotEmpty() }
        } catch (ex: Exception) {
            log.error(ex) { "Failed to read chicken facts sheet." }
            return emptyList()
        }

        if (rows.isEmpty()) {
            log.debug { "Chicken facts sheet returned no rows." }
            return emptyList()
        }

        return rows
            .mapNotNull(table.mapper::map)
            .filter { it.outcome == AgentRunOutcome.SUCCESS && !it.fact.isNullOrBlank() }
            .sortedByDescending { it.completedAt }
    }
}
