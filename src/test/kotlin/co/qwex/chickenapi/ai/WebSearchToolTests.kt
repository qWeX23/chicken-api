package co.qwex.chickenapi.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebSearchToolTests {

    @Test
    fun `clampMaxResults returns default when request is null`() {
        val resolved = WebSearchTool.clampMaxResults(requested = null, defaultMaxResults = 3)
        assertEquals(3, resolved)
    }

    @Test
    fun `clampMaxResults caps values above upper bound`() {
        val resolved = WebSearchTool.clampMaxResults(requested = 10, defaultMaxResults = 3)
        assertEquals(5, resolved)
    }

    @Test
    fun `clampMaxResults raises values below lower bound`() {
        val resolved = WebSearchTool.clampMaxResults(requested = 0, defaultMaxResults = 3)
        assertEquals(1, resolved)
    }
}
