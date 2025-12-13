package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.repository.BreedRepository
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.time.Instant

private const val SHEET_NAME = "breeds"
private const val MIN_COLUMN = "A"
private const val MAX_COLUMN = "K"
private const val SOURCES_DELIMITER = "|"
private val log = KotlinLogging.logger {}

@Repository
class GoogleSheetBreedRepository(
    private val sheets: Sheets,
    @Value("\${google.sheets.db.spreadsheetId}") private val spreadsheetId: String,
) : BreedRepository {
    override fun getAllBreeds(): List<Breed> {
        log.debug { "Fetching all breeds from sheet" }
        val response = sheets.spreadsheets().values()
            .get(
                spreadsheetId,
                buildRange(
                    sheetName = SHEET_NAME,
                    minColumn = MIN_COLUMN,
                    maxColumn = MAX_COLUMN,
                ),
            )
            .execute()
        val values = response.getValues() ?: return emptyList()
        val breeds = values.drop(1).map { row ->
            Breed(
                id = row[0]?.toString()?.toIntOrNull() ?: throw IllegalArgumentException("Invalid ID"),
                name = row.getOrNull(1)?.toString() ?: "",
                origin = row.getOrNull(2)?.toString(),
                eggColor = row.getOrNull(3)?.toString(),
                eggSize = row.getOrNull(4)?.toString(),
                temperament = row.getOrNull(5)?.toString(),
                description = row.getOrNull(6)?.toString(),
                imageUrl = row.getOrNull(7)?.toString(),
                numEggs = row.getOrNull(8)?.toString()?.toIntOrNull(),
                updatedAt = row.getOrNull(9)?.toString()?.parseInstantOrNull(),
                sources = row.getOrNull(10)?.toString()?.parseSourcesList(),
            )
        }
        log.debug { "Fetched ${breeds.size} breeds" }
        return breeds
    }
    override fun getBreedById(id: Int): Breed? {
        val response = sheets.spreadsheets().values()
            .get(spreadsheetId, buildRange(SHEET_NAME, MIN_COLUMN, MAX_COLUMN, id + 1))
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
                numEggs = row.getOrNull(8)?.toString()?.toIntOrNull(),
                updatedAt = row.getOrNull(9)?.toString()?.parseInstantOrNull(),
                sources = row.getOrNull(10)?.toString()?.parseSourcesList(),
            )
        }.firstOrNull()
    }

    override fun update(entity: Breed) {
        val rowNumber = entity.id + 1
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

        val range = "$SHEET_NAME!$MIN_COLUMN$rowNumber:$MAX_COLUMN$rowNumber"
        val valueRange = ValueRange().setValues(listOf(row))

        sheets.spreadsheets().values()
            .update(spreadsheetId, range, valueRange)
            .setValueInputOption("USER_ENTERED")
            .execute()

        log.info { "Updated breed ${entity.id} (${entity.name}) at $updatedAt" }
    }
}

fun buildRange(sheetName: String, minColumn: String, maxColumn: String, index: Int? = null): String {
    return "$sheetName!$minColumn${index ?: 1}:$maxColumn${index ?: ""}"
}

private fun String.parseInstantOrNull(): Instant? =
    takeIf { it.isNotBlank() }?.let {
        runCatching { Instant.parse(it) }.getOrNull()
    }

private fun String.parseSourcesList(): List<String>? =
    takeIf { it.isNotBlank() }?.split(SOURCES_DELIMITER)?.filter { it.isNotBlank() }
