package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Chicken
import co.qwex.chickenapi.repository.ChickenRepository
import com.google.api.services.sheets.v4.Sheets
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository

@Repository
class ChickenRepository(
    private val sheets: Sheets,
    @Value("\${google.sheets.breeds.spreadsheetId}") private val spreadsheetId: String,
    @Value("\${google.sheets.chickens.range}") private val range: String,
) : ChickenRepository {
    override fun getChickenById(id: Int): Chicken? {
        val rowNumber = "${id+1}"
        val rangeWithId = "chickens!A$rowNumber:C$rowNumber"
        val response = sheets.spreadsheets().values()
            .get(spreadsheetId, rangeWithId).execute()
        val values = response.getValues()?:return null
        return values.map { Chicken(
            id = it[0].toString().toInt(),
            name = it[2].toString(),
            breedId = it[1].toString().toInt(),
        ) }.firstOrNull()
    }
}