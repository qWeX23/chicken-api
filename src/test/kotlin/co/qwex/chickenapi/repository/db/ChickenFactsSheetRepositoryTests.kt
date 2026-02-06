package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.sheets.ChickenFactsTable
import co.qwex.chickenapi.repository.sheets.FakeSheetsGateway
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChickenFactsSheetRepositoryTests {
    private val gateway = FakeSheetsGateway()
    private val repository = ChickenFactsSheetRepository(gateway)

    @Test
    fun `create writes row to sheet`() {
        val record = ChickenFactsRecord(
            runId = "run",
            startedAt = Instant.EPOCH,
            completedAt = Instant.EPOCH,
            durationMillis = 0,
            outcome = AgentRunOutcome.SUCCESS,
            fact = "Chickens can recognize over 100 faces",
            sourceUrl = "https://example.com/chicken-facts",
            factEmbedding = listOf(0.1, 0.2, 0.3),
            errorMessage = null,
        )

        repository.create(record)

        val row = gateway.getValues(ChickenFactsTable.rowRange(2)).firstOrNull()
        assertNotNull(row)
        assertEquals("run", row[0])
        assertEquals("Chickens can recognize over 100 faces", row[6])
        assertEquals("https://example.com/chicken-facts", row[7])
        assertEquals("[0.1,0.2,0.3]", row[8])
    }

    @Test
    fun `fetchLatestChickenFact returns most recent successful fact`() {
        gateway.seed(
            ChickenFactsTable,
            listOf(
                listOf("run-1", Instant.EPOCH.toString(), Instant.EPOCH.plusSeconds(60).toString(), 60000, "FAILED", 0, "", "", "", "boom", Instant.EPOCH.toString()),
                listOf("run-2", Instant.EPOCH.toString(), Instant.EPOCH.plusSeconds(120).toString(), 120000, "SUCCESS", 10, "Chicken fact", "https://example.com", "[0.1,0.2]", "", Instant.EPOCH.plusSeconds(120).toString()),
            ),
        )

        val latest = repository.fetchLatestChickenFact()

        assertNotNull(latest)
        assertEquals("run-2", latest.runId)
        assertEquals("Chicken fact", latest.fact)
    }
}
