package co.qwex.chickenapi.service

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private const val BREEDS_SHEET = "pending_breeds"
private const val CHICKENS_SHEET = "pending_chickens"
private const val BREED_UPDATES_SHEET = "pending_breed_updates"

@Component
class ReviewQueue(
    private val sheets: Sheets,
    @Value("\${google.sheets.db.spreadsheetId}") private val spreadsheetId: String,
) {
    fun addBreed(breed: PendingBreed) {
        appendRow(
            BREEDS_SHEET,
            listOf(
                breed.name,
                breed.origin,
                breed.eggColor,
                breed.eggSize,
                breed.eggNumber,
                breed.temperament,
                breed.description,
                breed.imageUrl,
            ),
        )
    }

    fun addBreedUpdate(update: PendingBreedUpdate) {
        appendRow(
            BREED_UPDATES_SHEET,
            listOf(
                update.id,
                update.name,
                update.origin,
                update.eggColor,
                update.eggSize,
                update.eggNumber,
                update.temperament,
                update.description,
                update.imageUrl,
            ),
        )
    }

    fun addChicken(chicken: PendingChicken) {
        appendRow(
            CHICKENS_SHEET,
            listOf(
                chicken.name,
                chicken.breedId,
                chicken.imageUrl,
            ),
        )
    }

    private fun appendRow(sheetName: String, row: List<Any?>) {
        val valueRange = ValueRange()
            .setMajorDimension("ROWS")
            .setValues(listOf(row))

        sheets.spreadsheets().values()
            .append(spreadsheetId, sheetName, valueRange)
            .setValueInputOption("USER_ENTERED")
            .setInsertDataOption("INSERT_ROWS")
            .execute()
    }
}

data class PendingBreed(
    val name: String,
    val origin: String?,
    val eggColor: String?,
    val eggSize: String?,
    val eggNumber: Int?,
    val temperament: String?,
    val description: String?,
    val imageUrl: String?,
)

data class PendingBreedUpdate(
    val id: Int = 0,
    val name: String?,
    val origin: String?,
    val eggColor: String?,
    val eggSize: String?,
    val eggNumber: Int?,
    val temperament: String?,
    val description: String?,
    val imageUrl: String?,
)

data class PendingChicken(
    val name: String,
    val breedId: Int,
    val imageUrl: String,
)
