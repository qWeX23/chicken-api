package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.repository.BreedRepository
import co.qwex.chickenapi.repository.sheets.BreedsTable
import co.qwex.chickenapi.repository.sheets.SheetsGateway
import co.qwex.chickenapi.repository.sheets.ValueInputOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.stereotype.Repository
import java.time.Instant

private val log = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

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
        return findBreedRow(id)?.second
    }

    override fun update(entity: Breed) {
        sheetsGateway.ensureTableExists(table)
        val rowNumber = findBreedRow(entity.id)?.first
            ?: throw IllegalArgumentException("Breed with ID ${entity.id} does not exist")
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
            entity.sources
                ?.takeIf { it.isNotEmpty() }
                ?.let { sources -> json.encodeToString(sources) }
                .orEmpty(),
        )
        sheetsGateway.updateValues(table.rowRange(rowNumber), listOf(row), ValueInputOption.RAW)

        log.info { "Updated breed ${entity.id} (${entity.name}) at $updatedAt" }
    }

    private fun findBreedRow(id: Int): Pair<Int, Breed>? =
        sheetsGateway.getValues(table.dataRange())
            .mapIndexedNotNull { index, row ->
                table.mapper.map(row)
                    ?.takeIf { it.id == id }
                    ?.let { breed -> (table.columns.dataStartRow + index) to breed }
            }
            .firstOrNull()
}
