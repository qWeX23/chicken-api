package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.BreedResearchRecord
import co.qwex.chickenapi.repository.sheets.BreedResearchTable
import co.qwex.chickenapi.repository.sheets.FakeSheetsGateway
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BreedResearchSheetRepositoryTests {
    private val gateway = FakeSheetsGateway()
    private val repository = BreedResearchSheetRepository(gateway)

    @Test
    fun `create writes row to sheet`() {
        val record = BreedResearchRecord(
            runId = "run-1",
            breedId = 1,
            breedName = "Silkie",
            startedAt = Instant.EPOCH,
            completedAt = Instant.EPOCH.plusSeconds(30),
            durationMillis = 30000,
            outcome = AgentRunOutcome.SUCCESS,
            report = "Research report",
            sourcesFound = listOf("https://example.com"),
            fieldsUpdated = listOf("origin"),
            errorMessage = null,
        )

        repository.create(record)

        val row = gateway.getValues(BreedResearchTable.rowRange(2)).firstOrNull()
        assertNotNull(row)
        assertEquals("run-1", row[0])
        assertEquals(1, row[1])
        assertEquals("Research report", row[8])
    }

    @Test
    fun `fetchLatestResearchForBreed returns latest successful run for breed`() {
        gateway.seed(
            BreedResearchTable,
            listOf(
                listOf("run-1", 1, "Silkie", Instant.EPOCH.toString(), Instant.EPOCH.plusSeconds(10).toString(), 10000, "FAILED", 0, "", "", "", "boom"),
                listOf("run-2", 2, "Orpington", Instant.EPOCH.toString(), Instant.EPOCH.plusSeconds(20).toString(), 20000, "SUCCESS", 10, "Other breed", "", "", ""),
                listOf("run-3", 1, "Silkie", Instant.EPOCH.toString(), Instant.EPOCH.plusSeconds(30).toString(), 30000, "SUCCESS", 12, "Latest report", "", "", ""),
            ),
        )

        val latest = repository.fetchLatestResearchForBreed(1)

        assertNotNull(latest)
        assertEquals("run-3", latest.runId)
        assertEquals("Latest report", latest.report)
    }
}
