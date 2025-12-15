package co.qwex.chickenapi.service

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreedResearchJsonParsingTests {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses successful save breed research result`() {
        val jsonString = """
            {
                "success": true,
                "breedId": 5,
                "breedName": "Silkie",
                "fieldsUpdated": ["origin", "eggColor", "temperament"],
                "error": null
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SaveBreedResearchResult>(jsonString)

        assertTrue(parsed.success)
        assertEquals(5, parsed.breedId)
        assertEquals("Silkie", parsed.breedName)
        assertEquals(3, parsed.fieldsUpdated.size)
        assertEquals("origin", parsed.fieldsUpdated[0])
        assertNull(parsed.error)
    }

    @Test
    fun `parses failed save breed research result`() {
        val jsonString = """
            {
                "success": false,
                "breedId": 3,
                "breedName": "Unknown",
                "fieldsUpdated": [],
                "error": "Breed not found with ID 3"
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SaveBreedResearchResult>(jsonString)

        assertFalse(parsed.success)
        assertEquals(3, parsed.breedId)
        assertEquals("Unknown", parsed.breedName)
        assertEquals(0, parsed.fieldsUpdated.size)
        assertEquals("Breed not found with ID 3", parsed.error)
    }

    @Test
    fun `parses save result with empty fields updated`() {
        val jsonString = """
            {
                "success": true,
                "breedId": 1,
                "breedName": "Leghorn",
                "fieldsUpdated": []
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SaveBreedResearchResult>(jsonString)

        assertTrue(parsed.success)
        assertEquals(1, parsed.breedId)
        assertEquals("Leghorn", parsed.breedName)
        assertEquals(0, parsed.fieldsUpdated.size)
        assertNull(parsed.error)
    }

    @Test
    fun `parses save result with all field types updated`() {
        val jsonString = """
            {
                "success": true,
                "breedId": 2,
                "breedName": "Plymouth Rock",
                "fieldsUpdated": ["origin", "eggColor", "eggSize", "temperament", "description", "numEggs", "sources"]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SaveBreedResearchResult>(jsonString)

        assertTrue(parsed.success)
        assertEquals(2, parsed.breedId)
        assertEquals("Plymouth Rock", parsed.breedName)
        assertEquals(7, parsed.fieldsUpdated.size)
        assertTrue(parsed.fieldsUpdated.contains("sources"))
    }

    @Test
    fun `handles extra unknown fields in JSON gracefully`() {
        val jsonString = """
            {
                "success": true,
                "breedId": 1,
                "breedName": "Test",
                "fieldsUpdated": [],
                "unknownField": "should be ignored",
                "anotherUnknown": 123
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SaveBreedResearchResult>(jsonString)

        assertTrue(parsed.success)
        assertEquals(1, parsed.breedId)
        assertEquals("Test", parsed.breedName)
    }

    @Test
    fun `parses result with savedData containing all breed fields`() {
        val jsonString = """
            {
                "success": true,
                "breedId": 5,
                "breedName": "Silkie",
                "fieldsUpdated": ["description", "origin", "eggColor", "temperament", "sources"],
                "savedData": {
                    "description": "The Silkie is beloved for its fluffy plumage and gentle nature.",
                    "origin": "China",
                    "eggColor": "Cream",
                    "eggSize": "Small",
                    "temperament": "Docile and friendly",
                    "numEggs": 120,
                    "sources": ["https://example.com/silkie", "https://poultry.org/silkie"]
                }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SaveBreedResearchResult>(jsonString)

        assertTrue(parsed.success)
        assertEquals(5, parsed.breedId)
        assertEquals("Silkie", parsed.breedName)
        assertEquals(5, parsed.fieldsUpdated.size)

        val savedData = parsed.savedData
        assertNotNull(savedData)
        assertEquals("The Silkie is beloved for its fluffy plumage and gentle nature.", savedData!!.description)
        assertEquals("China", savedData.origin)
        assertEquals("Cream", savedData.eggColor)
        assertEquals("Small", savedData.eggSize)
        assertEquals("Docile and friendly", savedData.temperament)
        assertEquals(120, savedData.numEggs)
        assertEquals(2, savedData.sources.size)
        assertEquals("https://example.com/silkie", savedData.sources[0])
    }

    @Test
    fun `parses result with savedData containing only required fields`() {
        val jsonString = """
            {
                "success": true,
                "breedId": 3,
                "breedName": "Leghorn",
                "fieldsUpdated": ["description", "sources"],
                "savedData": {
                    "description": "The Leghorn is an excellent layer known for prolific egg production.",
                    "sources": ["https://example.com/leghorn"]
                }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SaveBreedResearchResult>(jsonString)

        assertTrue(parsed.success)
        val savedData = parsed.savedData
        assertNotNull(savedData)
        assertEquals("The Leghorn is an excellent layer known for prolific egg production.", savedData!!.description)
        assertNull(savedData.origin)
        assertNull(savedData.eggColor)
        assertNull(savedData.eggSize)
        assertNull(savedData.temperament)
        assertNull(savedData.numEggs)
        assertEquals(1, savedData.sources.size)
    }

    @Test
    fun `parses failed result without savedData`() {
        val jsonString = """
            {
                "success": false,
                "breedId": 99,
                "breedName": "UNKNOWN",
                "fieldsUpdated": [],
                "error": "Breed not found with ID 99",
                "savedData": null
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SaveBreedResearchResult>(jsonString)

        assertFalse(parsed.success)
        assertEquals("Breed not found with ID 99", parsed.error)
        assertNull(parsed.savedData)
    }
}
