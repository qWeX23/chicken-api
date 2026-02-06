package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.repository.BreedRepository
import co.qwex.chickenapi.repository.sheets.BreedsTable
import co.qwex.chickenapi.repository.sheets.SheetsGateway
import co.qwex.chickenapi.repository.sheets.ValueInputOption
import mu.KotlinLogging
import org.springframework.stereotype.Repository
import java.time.Instant

private const val SOURCES_DELIMITER = "|"
private val log = KotlinLogging.logger {}

@Repository
class GoogleSheetsBreedRepository(
    private val sheetsGateway: SheetsGateway,
    private val table: BreedsTable = BreedsTable,
) : BreedRepository {
    override fun getAllBreeds(): List<Breed> {
        sheetsGateway.ensureTableExists(table)
        log.debug { "Fetching all breeds from sheet" }
        val values = sheetsGateway.getValues(table.dataRange())
        val breeds = values.mapNotNull(table.mapper::map)
        log.debug { "Fetched ${breeds.size} breeds" }
        return breeds
    }

    override fun getBreedById(id: Int): Breed? {
        sheetsGateway.ensureTableExists(table)
        val rowNumber = id + table.columns.headerRow
        val values = sheetsGateway.getValues(table.rowRange(rowNumber))
        log.info { "Fetched breed with ID $id: $values" }
        return values.mapNotNull(table.mapper::map).firstOrNull()
    }

    override fun update(entity: Breed) {
        sheetsGateway.ensureTableExists(table)
        val rowNumber = entity.id + table.columns.headerRow
        val updatedAt = Instant.now()
        val row = listOf(
            entity.id,
            entity.name,
            entity.origin.orEmpty(),
            entity.eggColor.orEmpty(),
            entity.eggSize.orEmpty(),
            entity.temperament.orEmpty(),
            entity.description.orEmpty(),
            entity.imageUrl.orEmpty(),
            entity.numEggs ?: "",
            updatedAt.toString(),
            entity.sources?.joinToString(SOURCES_DELIMITER).orEmpty(),
        )
        sheetsGateway.updateValues(table.rowRange(rowNumber), listOf(row), ValueInputOption.USER_ENTERED)

        log.info { "Updated breed ${entity.id} (${entity.name}) at $updatedAt" }
    }
}
