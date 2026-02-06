package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogBreedResearchAgent
import co.qwex.chickenapi.repository.BreedResearchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class BreedResearcherScheduledTaskServiceTests {

    @Test
    fun `parseSaveResult parses pretty printed save JSON`() {
        val agent = mock(KoogBreedResearchAgent::class.java)
        val researchRepository = mock(BreedResearchRepository::class.java)
        val service = BreedResearcherScheduledTaskService(agent, researchRepository)

        val response =
            """
            {
              "success": true,
              "breedId": 5,
              "breedName": "Silkie",
              "fieldsUpdated": ["description", "origin", "sources"],
              "savedData": {
                "description": "Silkies are calm, fluffy birds prized as friendly backyard companions.",
                "origin": "China",
                "eggColor": "Cream",
                "eggSize": "Small",
                "temperament": "Docile and friendly",
                "numEggs": 120,
                "sources": ["https://example.com/silkie"]
              }
            }
            """.trimIndent()

        val parsed = invokeParseSaveResult(service, response)

        assertNotNull(parsed)
        assertTrue(parsed!!.success)
        assertEquals(5, parsed.breedId)
        assertEquals("Silkie", parsed.breedName)
        assertEquals(listOf("description", "origin", "sources"), parsed.fieldsUpdated)
    }

    @Test
    fun `parseSaveResult extracts save JSON when embedded in surrounding text`() {
        val agent = mock(KoogBreedResearchAgent::class.java)
        val researchRepository = mock(BreedResearchRepository::class.java)
        val service = BreedResearcherScheduledTaskService(agent, researchRepository)

        val response =
            """
            The tool returned the following payload:
            {
              "success": true,
              "breedId": 2,
              "breedName": "Plymouth Rock",
              "fieldsUpdated": ["description", "sources"],
              "savedData": {
                "description": "A dependable dual-purpose breed with calm temperament.",
                "origin": "United States",
                "eggColor": "Brown",
                "eggSize": "Large",
                "temperament": "Friendly and calm",
                "numEggs": 220,
                "sources": ["https://example.com/plymouth-rock"]
              }
            }
            End of payload.
            """.trimIndent()

        val parsed = invokeParseSaveResult(service, response)

        assertNotNull(parsed)
        assertTrue(parsed!!.success)
        assertEquals(2, parsed.breedId)
        assertEquals("Plymouth Rock", parsed.breedName)
        assertEquals(listOf("description", "sources"), parsed.fieldsUpdated)
    }

    @Test
    fun `parseSaveResult returns null when response has no save result JSON`() {
        val agent = mock(KoogBreedResearchAgent::class.java)
        val researchRepository = mock(BreedResearchRepository::class.java)
        val service = BreedResearcherScheduledTaskService(agent, researchRepository)

        val parsed = invokeParseSaveResult(service, "I researched a breed but did not save it.")
        assertNull(parsed)
    }

    private fun invokeParseSaveResult(
        service: BreedResearcherScheduledTaskService,
        response: String,
    ): SaveBreedResearchResult? {
        val method = BreedResearcherScheduledTaskService::class.java.getDeclaredMethod("parseSaveResult", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(service, response) as SaveBreedResearchResult?
    }
}
