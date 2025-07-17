package co.qwex.chickenapi.service

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private const val BREEDS_SHEET = "pending_breeds"
private const val CHICKENS_SHEET = "pending_chickens"

@Component
class ReviewQueue(
    private val sheets: Sheets,
    @Value("\${google.sheets.db.spreadsheetId}") private val spreadsheetId: String,
) {
    private val pendingBreeds = mutableListOf<PendingBreed>()
    private val pendingChickens = mutableListOf<PendingChicken>()

    fun addBreed(breed: PendingBreed) {
        pendingBreeds += breed
        val valueRange = ValueRange().setValues(
            listOf(
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
            ),
        )
        sheets.spreadsheets().values()
            .append(spreadsheetId, "$BREEDS_SHEET!A1", valueRange)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    fun addChicken(chicken: PendingChicken) {
        pendingChickens += chicken
        val valueRange = ValueRange().setValues(
            listOf(
                listOf(
                    chicken.name,
                    chicken.breedId,
                    chicken.imageUrl,
                ),
            ),
        )
        sheets.spreadsheets().values()
            .append(spreadsheetId, "$CHICKENS_SHEET!A1", valueRange)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    fun getBreeds(): List<PendingBreed> = pendingBreeds.toList()

    fun getChickens(): List<PendingChicken> = pendingChickens.toList()
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

data class PendingChicken(
    val name: String,
    val breedId: Int,
    val imageUrl: String,
)
