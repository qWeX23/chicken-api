package co.qwex.chickenapi.ai

import co.qwex.chickenapi.TestFixtures
import co.qwex.chickenapi.ai.tools.GetNextBreedToResearchTool
import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.repository.BreedRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BreedResearchToolsTests {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `GetNextBreedToResearchTool selects from never-updated breeds when available`() {
        val mockRepository = mock(BreedRepository::class.java)
        val breeds = listOf(
            TestFixtures.breedNeverUpdated1,
            TestFixtures.breedNeverUpdated2,
            TestFixtures.breedOldUpdate,
            TestFixtures.breedRecentUpdate,
        )
        `when`(mockRepository.getAllBreeds()).thenReturn(breeds)

        val tool = GetNextBreedToResearchTool(mockRepository)

        // Run multiple times to verify it picks from never-updated breeds
        repeat(10) {
            val result = runBlocking {
                tool.doExecute(GetNextBreedToResearchTool.Args(fetch = true))
            }

            val parsed = json.decodeFromString<GetNextBreedToResearchTool.Result>(result)

            // Should always select from breeds with null updatedAt
            assertTrue(parsed.breedId in listOf(1, 2), "Should select breed ID 1 or 2 (never updated)")
            assertEquals("never_updated", parsed.reason)
        }
    }

    @Test
    fun `GetNextBreedToResearchTool selects oldest when all breeds have been updated`() {
        val mockRepository = mock(BreedRepository::class.java)
        val breeds = listOf(
            TestFixtures.breedOldUpdate,      // 2024-01-01 - oldest
            TestFixtures.breedRecentUpdate,   // 2025-01-01 - newest
            TestFixtures.breedWithSources,    // 2024-06-15 - middle
        )
        `when`(mockRepository.getAllBreeds()).thenReturn(breeds)

        val tool = GetNextBreedToResearchTool(mockRepository)
        val result = runBlocking {
            tool.doExecute(GetNextBreedToResearchTool.Args(fetch = true))
        }

        val parsed = json.decodeFromString<GetNextBreedToResearchTool.Result>(result)

        // Should select the breed with the oldest updatedAt (Leghorn, ID 3)
        assertEquals(3, parsed.breedId)
        assertEquals("Leghorn", parsed.breedName)
        assertEquals("oldest_update", parsed.reason)
        assertNotNull(parsed.lastUpdated)
    }

    @Test
    fun `GetNextBreedToResearchTool handles empty repository`() {
        val mockRepository = mock(BreedRepository::class.java)
        `when`(mockRepository.getAllBreeds()).thenReturn(emptyList())

        val tool = GetNextBreedToResearchTool(mockRepository)
        val result = runBlocking {
            tool.doExecute(GetNextBreedToResearchTool.Args(fetch = true))
        }

        val parsed = json.decodeFromString<GetNextBreedToResearchTool.Result>(result)

        assertEquals(-1, parsed.breedId)
        assertEquals("NO_BREEDS_FOUND", parsed.breedName)
        assertEquals("no_breeds_available", parsed.reason)
    }

    @Test
    fun `GetNextBreedToResearchTool handles single breed with null updatedAt`() {
        val mockRepository = mock(BreedRepository::class.java)
        `when`(mockRepository.getAllBreeds()).thenReturn(listOf(TestFixtures.breedNeverUpdated1))

        val tool = GetNextBreedToResearchTool(mockRepository)
        val result = runBlocking {
            tool.doExecute(GetNextBreedToResearchTool.Args(fetch = true))
        }

        val parsed = json.decodeFromString<GetNextBreedToResearchTool.Result>(result)

        assertEquals(1, parsed.breedId)
        assertEquals("Rhode Island Red", parsed.breedName)
        assertEquals("never_updated", parsed.reason)
    }

    @Test
    fun `GetNextBreedToResearchTool handles single breed with updatedAt`() {
        val mockRepository = mock(BreedRepository::class.java)
        `when`(mockRepository.getAllBreeds()).thenReturn(listOf(TestFixtures.breedRecentUpdate))

        val tool = GetNextBreedToResearchTool(mockRepository)
        val result = runBlocking {
            tool.doExecute(GetNextBreedToResearchTool.Args(fetch = true))
        }

        val parsed = json.decodeFromString<GetNextBreedToResearchTool.Result>(result)

        assertEquals(4, parsed.breedId)
        assertEquals("Sussex", parsed.breedName)
        assertEquals("oldest_update", parsed.reason)
    }
}
