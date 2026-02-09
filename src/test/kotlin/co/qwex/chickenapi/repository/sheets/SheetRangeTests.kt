package co.qwex.chickenapi.repository.sheets

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SheetRangeTests {
    @Test
    fun `columns quotes sheet names and omits rows`() {
        val range = SheetRange.columns("Breed Data", "A", "K")

        assertEquals("'Breed Data'!A:K", range.toA1Range())
    }

    @Test
    fun `row quotes sheet names and escapes apostrophes`() {
        val range = SheetRange.row("Bob's Breeds", "A", "K", 5)

        assertEquals("'Bob''s Breeds'!A5:K5", range.toA1Range())
    }

    @Test
    fun `headerRow always targets first row`() {
        val range = SheetRange.headerRow("breeds", "A", "K")

        assertEquals("'breeds'!A1:K1", range.toA1Range())
    }
}
