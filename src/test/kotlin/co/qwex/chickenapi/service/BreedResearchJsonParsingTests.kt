package co.qwex.chickenapi.service

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BreedResearchJsonParsingTests {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses complete breed research JSON with all fields`() {
        val jsonString = """
            {
                "breedId": 5,
                "report": "The Silkie is a unique breed known for its fluffy plumage that feels like silk. Originally from China, this ornamental breed has a calm and friendly disposition.",
                "origin": "China",
                "eggColor": "Cream to tinted",
                "eggSize": "Small",
                "temperament": "Docile, friendly, and broody",
                "description": "A distinctive ornamental breed with fluffy silk-like plumage, black skin, and blue earlobes.",
                "numEggs": 120,
                "sources": [
                    "https://www.backyardchickens.com/silkie",
                    "https://www.mypetchicken.com/silkie-breed"
                ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BreedResearchJson>(jsonString)

        assertEquals(5, parsed.breedId)
        assertEquals("China", parsed.origin)
        assertEquals("Cream to tinted", parsed.eggColor)
        assertEquals("Small", parsed.eggSize)
        assertEquals("Docile, friendly, and broody", parsed.temperament)
        assertEquals(120, parsed.numEggs)
        assertEquals(2, parsed.sources.size)
        assertEquals("https://www.backyardchickens.com/silkie", parsed.sources[0])
    }

    @Test
    fun `parses breed research JSON with optional fields null`() {
        val jsonString = """
            {
                "breedId": 3,
                "report": "The Leghorn is an excellent layer known for high egg production.",
                "sources": ["https://example.com/leghorn"]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BreedResearchJson>(jsonString)

        assertEquals(3, parsed.breedId)
        assertEquals("The Leghorn is an excellent layer known for high egg production.", parsed.report)
        assertNull(parsed.origin)
        assertNull(parsed.eggColor)
        assertNull(parsed.eggSize)
        assertNull(parsed.temperament)
        assertNull(parsed.description)
        assertNull(parsed.numEggs)
        assertEquals(1, parsed.sources.size)
    }

    @Test
    fun `parses breed research JSON with empty sources list`() {
        val jsonString = """
            {
                "breedId": 1,
                "report": "Test report",
                "sources": []
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BreedResearchJson>(jsonString)

        assertEquals(1, parsed.breedId)
        assertEquals("Test report", parsed.report)
        assertEquals(0, parsed.sources.size)
    }

    @Test
    fun `parses breed research JSON with mixed null and present fields`() {
        val jsonString = """
            {
                "breedId": 2,
                "report": "Plymouth Rock is a versatile breed.",
                "origin": "USA",
                "eggColor": null,
                "eggSize": "Large",
                "temperament": null,
                "numEggs": 280,
                "sources": ["https://example.com"]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BreedResearchJson>(jsonString)

        assertEquals(2, parsed.breedId)
        assertEquals("USA", parsed.origin)
        assertNull(parsed.eggColor)
        assertEquals("Large", parsed.eggSize)
        assertNull(parsed.temperament)
        assertEquals(280, parsed.numEggs)
    }

    @Test
    fun `handles extra unknown fields in JSON gracefully`() {
        val jsonString = """
            {
                "breedId": 1,
                "report": "Test",
                "sources": [],
                "unknownField": "should be ignored",
                "anotherUnknown": 123
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BreedResearchJson>(jsonString)

        assertEquals(1, parsed.breedId)
        assertEquals("Test", parsed.report)
    }
}
