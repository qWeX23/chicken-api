package co.qwex.chickenapi.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveChickenFactToolTests {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `save tool returns duplicate metadata when duplicate exists`() {
        val tool = SaveChickenFactTool(
            duplicateCheckService =
                object : ChickenFactDuplicateCheckService {
                    override suspend fun checkFactForDuplicate(fact: String): FactDuplicateCheckResult =
                        FactDuplicateCheckResult(
                            hasHit = true,
                            threshold = 0.88,
                            topSimilarity = 0.95,
                            matches =
                                listOf(
                                    SimilarFactMatch(
                                        runId = "run-1",
                                        fact = "Chickens can recognize over 100 faces.",
                                        sourceUrl = "https://example.com/faces",
                                        similarity = 0.95,
                                    ),
                                ),
                        )
                },
        )

        val resultJson = runBlocking {
            tool.doExecute(
                SaveChickenFactTool.Args(
                    fact = "Chickens can remember over 100 faces.",
                    sourceUrl = "https://example.com/new",
                ),
            )
        }

        val result = json.decodeFromString(SaveChickenFactTool.Result.serializer(), resultJson)

        assertTrue(result.duplicateCheck.hasHit)
        assertEquals(0.88, result.duplicateCheck.threshold)
        assertEquals(1, result.duplicateCheck.matches.size)
        assertEquals("run-1", result.duplicateCheck.matches.first().runId)
    }

    @Test
    fun `save tool returns no-hit duplicate metadata when fact is unique`() {
        val tool = SaveChickenFactTool(
            duplicateCheckService =
                object : ChickenFactDuplicateCheckService {
                    override suspend fun checkFactForDuplicate(fact: String): FactDuplicateCheckResult =
                        FactDuplicateCheckResult(
                            hasHit = false,
                            threshold = 0.88,
                            topSimilarity = null,
                            matches = emptyList(),
                        )
                },
        )

        val resultJson = runBlocking {
            tool.doExecute(
                SaveChickenFactTool.Args(
                    fact = "Hens often communicate with their chicks before they hatch.",
                    sourceUrl = "https://example.com/chicks",
                ),
            )
        }

        val result = json.decodeFromString(SaveChickenFactTool.Result.serializer(), resultJson)

        assertFalse(result.duplicateCheck.hasHit)
        assertEquals(0.88, result.duplicateCheck.threshold)
        assertTrue(result.duplicateCheck.matches.isEmpty())
    }
}
