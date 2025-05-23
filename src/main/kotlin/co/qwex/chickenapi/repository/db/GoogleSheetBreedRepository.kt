package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.repository.BreedRepository
import com.google.api.services.sheets.v4.Sheets
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
private val log = mu.KotlinLogging.logger {}

@Repository
class GoogleSheetBreedRepository(
    private val sheets: Sheets,
    @Value("\${google.sheets.breeds.spreadsheetId}") private val spreadsheetId: String,
    @Value("\${google.sheets.breeds.range}") private val range: String,
) : BreedRepository {
    override fun getAllBreeds(): List<Breed> {
        val response = sheets.spreadsheets().values()
            .get(spreadsheetId, range)
            .execute()
        val values = response.getValues() ?: return emptyList()
        return values.drop(1).map { row ->
            Breed(
                id = row[0]?.toString()?.toIntOrNull() ?: throw IllegalArgumentException("Invalid ID"),
                name = row.getOrNull(1)?.toString() ?: "",
                origin = row.getOrNull(2)?.toString(),
                eggColor = row.getOrNull(3)?.toString(),
                eggSize = row.getOrNull(4)?.toString(),
                temperament = row.getOrNull(5)?.toString(),
                description = row.getOrNull(6)?.toString(),
                imageUrl = row.getOrNull(7)?.toString(),
            )
        }
    }
    override fun getBreedById(id: Int): Breed? {
        val rangeWithId = "breeds!A${id + 1}:G${id + 1}"
        val response = sheets.spreadsheets().values()
            .get(spreadsheetId, rangeWithId)
            .execute()
        val values = response.getValues() ?: return null
        log.info { "Fetched breed with ID $id: $values" }
        return values.map { row ->
            Breed(
                id = row[0]?.toString()?.toIntOrNull() ?: throw IllegalArgumentException("Invalid ID"),
                name = row.getOrNull(1)?.toString() ?: "",
                origin = row.getOrNull(2)?.toString(),
                eggColor = row.getOrNull(3)?.toString(),
                eggSize = row.getOrNull(4)?.toString(),
                temperament = row.getOrNull(5)?.toString(),
                description = row.getOrNull(6)?.toString(),
                imageUrl = row.getOrNull(7)?.toString(),
            )
        }.firstOrNull()
    }
}
