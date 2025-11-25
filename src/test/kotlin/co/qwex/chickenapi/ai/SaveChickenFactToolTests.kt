package co.qwex.chickenapi.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SaveChickenFactToolTests {

    @Test
    fun `detect markdown with bold text`() {
        val tool = SaveChickenFactTool(null, null)
        val fact = "**Chickens** are amazing birds"

        assertTrue(tool.containsMarkdown(fact), "Should detect markdown in bold text")
    }

    @Test
    fun `detect markdown with italic text`() {
        val tool = SaveChickenFactTool(null, null)
        val fact = "Chickens are _amazing_ birds"

        assertTrue(tool.containsMarkdown(fact), "Should detect markdown in italic text")
    }

    @Test
    fun `detect markdown with bullet points`() {
        val tool = SaveChickenFactTool(null, null)
        val fact = "- Chickens are amazing birds"

        assertTrue(tool.containsMarkdown(fact), "Should detect markdown in bullet points")
    }

    @Test
    fun `detect markdown with headers`() {
        val tool = SaveChickenFactTool(null, null)
        val fact = "## Chickens are amazing"

        assertTrue(tool.containsMarkdown(fact), "Should detect markdown in headers")
    }

    @Test
    fun `detect markdown with links`() {
        val tool = SaveChickenFactTool(null, null)
        val fact = "Chickens are [amazing](http://example.com) birds"

        assertTrue(tool.containsMarkdown(fact), "Should detect markdown in links")
    }

    @Test
    fun `no markdown in plain text`() {
        val tool = SaveChickenFactTool(null, null)
        val fact = "Chickens are amazing birds that can recognize over 100 different faces"

        kotlin.test.assertFalse(tool.containsMarkdown(fact), "Should not detect markdown in plain text")
    }

    @Test
    fun `no markdown in text with parentheses`() {
        val tool = SaveChickenFactTool(null, null)
        val fact = "Chickens (Gallus gallus domesticus) are domesticated birds"

        kotlin.test.assertFalse(tool.containsMarkdown(fact), "Should not detect markdown in text with parentheses")
    }

    @Test
    fun `tool accepts plain text without cleanup`() = runBlocking {
        val tool = SaveChickenFactTool(null, null)
        val plainFact = "Chickens can recognize over 100 different faces"
        val args = SaveChickenFactTool.Args(
            fact = plainFact,
            sourceUrl = "http://example.com"
        )

        val result = tool.doExecute(args)

        assertNotNull(result)
        assertTrue(result.contains(plainFact), "Plain text should pass through unchanged")
        assertTrue(result.contains("http://example.com"), "Source URL should be preserved")
    }

    @Test
    fun `tool handles markdown without LLM by fallback`() = runBlocking {
        // Without LLM, tool should use fallback cleanup
        val tool = SaveChickenFactTool(null, null)
        val markdownFact = "**Chickens** can recognize over 100 different faces"
        val args = SaveChickenFactTool.Args(
            fact = markdownFact,
            sourceUrl = "http://example.com"
        )

        val result = tool.doExecute(args)

        assertNotNull(result)
        // Result should have markdown symbols removed by fallback
        assertTrue(!result.contains("**"), "Fallback should remove markdown symbols")
        assertTrue(result.contains("http://example.com"), "Source URL should be preserved")
    }

    @Test
    fun `tool rejects empty sourceUrl`() = runBlocking {
        val tool = SaveChickenFactTool(null, null)
        val args = SaveChickenFactTool.Args(
            fact = "Chickens can recognize over 100 different faces",
            sourceUrl = ""
        )

        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            tool.doExecute(args)
        }
        assertTrue(exception.message?.contains("sourceUrl is required") == true, "Should throw error for empty sourceUrl")
    }

    @Test
    fun `tool rejects blank sourceUrl`() = runBlocking {
        val tool = SaveChickenFactTool(null, null)
        val args = SaveChickenFactTool.Args(
            fact = "Chickens can recognize over 100 different faces",
            sourceUrl = "   "
        )

        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            tool.doExecute(args)
        }
        assertTrue(exception.message?.contains("sourceUrl is required") == true, "Should throw error for blank sourceUrl")
    }
}
