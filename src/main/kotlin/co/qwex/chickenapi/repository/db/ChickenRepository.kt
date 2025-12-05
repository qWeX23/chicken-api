package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Chicken
import co.qwex.chickenapi.repository.ChickenRepository
import com.google.api.services.sheets.v4.Sheets
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
private const val SHEET_NAME = "chickens"
private const val MIN_COLUMN = "A"
private const val MAX_COLUMN = "D"
private val log = KotlinLogging.logger {}

@Repository
class ChickenRepository(
    private val sheets: Sheets,
    @Value("\${google.sheets.db.spreadsheetId}") private val spreadsheetId: String,
) : ChickenRepository {
    override fun getChickenById(id: Int): Chicken? {
        log.debug { "Fetching chicken with ID $id" }
        val rowNumber = "${id + 1}"
        val rangeWithId = "$SHEET_NAME!$MIN_COLUMN$rowNumber:$MAX_COLUMN$rowNumber"
        val response = sheets.spreadsheets().values()
            .get(spreadsheetId, rangeWithId).execute()
        val row = response.getValues()?.firstOrNull() ?: return null

        val chickenId = row.getOrNull(0)?.toString()?.toIntOrNull() ?: return null
        val breedId = row.getOrNull(1)?.toString()?.toIntOrNull() ?: return null
        val name = row.getOrNull(2)?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val imageUrl = row.getOrNull(3)?.toString().orEmpty()

        val chicken = Chicken(
            id = chickenId,
            name = name,
            breedId = breedId,
            imageUrl = imageUrl,
        )
        log.debug { "Result for chicken ID $id: $chicken" }
        return chicken
    }
}
