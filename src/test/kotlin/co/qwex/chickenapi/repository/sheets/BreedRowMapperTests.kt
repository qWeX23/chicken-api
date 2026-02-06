package co.qwex.chickenapi.repository.sheets

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BreedRowMapperTests {
    private val mapper = BreedRowMapper()

    @Test
    fun `maps row with mixed types and sources`() {
        val updatedAt = Instant.parse("2024-01-01T00:00:00Z")
        val row = listOf(
            3.0,
            "Lavender Orpington",
            "UK",
            "Brown",
            "Large",
            "Docile",
            "Calm bird",
            "https://example.com/img.png",
            200.0,
            updatedAt.toString(),
            "https://a.com| https://b.com ",
        )

        val breed = mapper.map(row)

        requireNotNull(breed)
        assertEquals(3, breed.id)
        assertEquals("Lavender Orpington", breed.name)
        assertEquals(200, breed.numEggs)
        assertEquals(updatedAt, breed.updatedAt)
        assertEquals(listOf("https://a.com", "https://b.com"), breed.sources)
    }

    @Test
    fun `returns null for header row`() {
        val row = BreedsTable.headerRow()

        val breed = mapper.map(row)

        assertNull(breed)
    }

    @Test
    fun `handles short row with missing optional values`() {
        val row = listOf(
            1,
            "Silkie",
        )

        val breed = mapper.map(row)

        requireNotNull(breed)
        assertEquals(1, breed.id)
        assertEquals("Silkie", breed.name)
        assertNull(breed.origin)
        assertNull(breed.sources)
    }
}
