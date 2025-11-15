package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository

private const val CHICKEN_FACTS_RANGE = "chicken_facts!A1:H1"

@Repository
class ChickenFactsSheetRepository(
    private val sheets: Sheets,
    @Value("\${google.sheets.db.spreadsheetId}") private val spreadsheetId: String,
) {
    private val log = KotlinLogging.logger {}

    fun append(record: ChickenFactsRecord) {
        val row = listOf(
            record.runId,
            record.startedAt.toString(),
            record.completedAt.toString(),
            record.durationMillis,
            record.outcome.name,
            record.factsMarkdown?.length ?: 0,
            record.factsMarkdown.orEmpty(),
            record.errorMessage.orEmpty(),
        )

        val valueRange = ValueRange().setValues(listOf(row))

        sheets.spreadsheets().values()
            .append(spreadsheetId, CHICKEN_FACTS_RANGE, valueRange)
            .setValueInputOption("USER_ENTERED")
            .execute()

        log.debug { "Appended chicken facts run ${record.runId} with outcome ${record.outcome}" }
    }
}
