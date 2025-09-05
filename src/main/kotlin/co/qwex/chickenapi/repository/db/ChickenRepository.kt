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
        val values = response.getValues() ?: return null
        val chicken = values.map {
            Chicken(
                id = it[0].toString().toInt(),
                name = it[2].toString(),
                breedId = it[1].toString().toInt(),
                imageUrl = it[3].toString(),
            )
        }.firstOrNull()
        log.debug { "Result for chicken ID $id: $chicken" }
        return chicken
    }
}
