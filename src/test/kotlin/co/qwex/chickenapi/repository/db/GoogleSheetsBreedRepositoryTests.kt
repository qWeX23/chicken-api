package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.repository.sheets.BreedsTable
import co.qwex.chickenapi.repository.sheets.FakeSheetsGateway
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GoogleSheetsBreedRepositoryTests {
    private val gateway = FakeSheetsGateway()
    private val repository = GoogleSheetsBreedRepository(gateway)

    @Test
    fun `getAllBreeds maps rows from table`() {
        gateway.seed(
            BreedsTable,
            listOf(
                listOf(1, "Silkie", "China"),
                listOf(2, "Orpington", "UK"),
            ),
        )

        val breeds = repository.getAllBreeds()

        assertEquals(2, breeds.size)
        assertEquals("Silkie", breeds.first().name)
    }

    @Test
    fun `getBreedById returns null when row missing`() {
        gateway.seed(
            BreedsTable,
            listOf(
                listOf(1, "Silkie", "China"),
            ),
        )

        val breed = repository.getBreedById(99)

        assertNull(breed)
    }

    @Test
    fun `update writes row with updated timestamp`() {
        gateway.seed(BreedsTable, emptyList())
        val updatedBreed = Breed(
            id = 1,
            name = "Silkie",
            origin = "China",
            eggColor = null,
            eggSize = null,
            temperament = null,
            description = null,
            imageUrl = null,
            numEggs = 100,
            updatedAt = null,
            sources = listOf("https://example.com"),
        )

        repository.update(updatedBreed)

        val row = gateway.getValues(BreedsTable.rowRange(2)).firstOrNull()
        assertNotNull(row)
        assertEquals(1, row[0])
        assertEquals("Silkie", row[1])
        assertNotNull(runCatching { Instant.parse(row[9].toString()) }.getOrNull())
        assertEquals("https://example.com", row[10])
    }
}
