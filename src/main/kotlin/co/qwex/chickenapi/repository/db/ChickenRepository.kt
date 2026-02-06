package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Chicken
import co.qwex.chickenapi.repository.ChickenRepository
import co.qwex.chickenapi.repository.sheets.ChickensTable
import co.qwex.chickenapi.repository.sheets.SheetsGateway
import mu.KotlinLogging
import org.springframework.stereotype.Repository

private val log = KotlinLogging.logger {}

@Repository
class ChickenRepository(
    private val sheetsGateway: SheetsGateway,
    private val table: ChickensTable = ChickensTable,
) : ChickenRepository {
    override fun getChickenById(id: Int): Chicken? {
        log.debug { "Fetching chicken with ID $id" }
        sheetsGateway.ensureTableExists(table)
        val rowNumber = id + table.columns.headerRow
        val chicken = sheetsGateway.getValues(table.rowRange(rowNumber))
            .mapNotNull(table.mapper::map)
            .firstOrNull()
        log.debug { "Result for chicken ID $id: $chicken" }
        return chicken
    }
}
