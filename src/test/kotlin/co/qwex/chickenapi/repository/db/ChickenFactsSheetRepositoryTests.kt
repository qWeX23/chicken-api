package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.time.Instant

class ChickenFactsSheetRepositoryTests {

    private val sheets: Sheets = Mockito.mock(Sheets::class.java, Mockito.RETURNS_DEEP_STUBS)
    private val repository = ChickenFactsSheetRepository(sheets, "test-sheet")

    @Test
    fun `append writes row to sheet`() {
        val values = sheets.spreadsheets().values()
        val append = Mockito.mock(Sheets.Spreadsheets.Values.Append::class.java)
        Mockito.`when`(values.append(anyString(), anyString(), Mockito.any(ValueRange::class.java))).thenReturn(append)
        Mockito.`when`(append.setValueInputOption(anyString())).thenReturn(append)
        Mockito.`when`(append.execute()).thenReturn(AppendValuesResponse())

        val record = ChickenFactsRecord(
            runId = "run",
            startedAt = Instant.EPOCH,
            completedAt = Instant.EPOCH,
            durationMillis = 0,
            outcome = AgentRunOutcome.SUCCESS,
            factsMarkdown = "- Fact",
            errorMessage = null,
        )

        repository.append(record)

        val valueRangeCaptor = ArgumentCaptor.forClass(ValueRange::class.java)
        Mockito.verify(values).append(
            Mockito.eq("test-sheet"),
            Mockito.eq("chicken_facts!A1:H1"),
            valueRangeCaptor.capture(),
        )
        Mockito.verify(append).setValueInputOption("USER_ENTERED")
        Mockito.verify(append).execute()

        val row = valueRangeCaptor.value.getValues().first()
        assert(row.contains("- Fact"))
    }
}
