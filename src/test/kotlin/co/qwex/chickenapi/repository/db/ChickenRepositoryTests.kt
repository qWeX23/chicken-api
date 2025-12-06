package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Chicken
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChickenRepositoryTests {

    private lateinit var sheets: Sheets
    private lateinit var repository: ChickenRepository

    @BeforeEach
    fun setup() {
        sheets = Mockito.mock(Sheets::class.java, Mockito.RETURNS_DEEP_STUBS)
        repository = ChickenRepository(sheets, "test-sheet")
    }

    @Test
    fun `getChickenById returns chicken when row is complete`() {
        val valueRange = ValueRange().setValues(
            listOf(
                listOf(1, 2, "Henrietta", "img"),
            ),
        )
        `when`(
            sheets.spreadsheets().values().get(eq("test-sheet"), eq("chickens!A2:E2")).execute(),
        ).thenReturn(valueRange)

        val chicken = repository.getChickenById(1)

        assertEquals(
            Chicken(id = 1, breedId = 2, name = "Henrietta", imageUrl = "img"),
            chicken,
        )
    }

    @Test
    fun `getChickenById returns null when row is incomplete`() {
        val incompleteRow = ValueRange().setValues(
            listOf(
                listOf(1, 2), // Missing name and image URL columns
            ),
        )
        `when`(
            sheets.spreadsheets().values().get(eq("test-sheet"), eq("chickens!A2:E2")).execute(),
        ).thenReturn(incompleteRow)

        val chicken = repository.getChickenById(1)

        assertNull(chicken)
    }
}
