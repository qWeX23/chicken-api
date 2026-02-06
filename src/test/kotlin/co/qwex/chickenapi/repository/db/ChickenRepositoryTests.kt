package co.qwex.chickenapi.repository.db

import co.qwex.chickenapi.model.Chicken
import co.qwex.chickenapi.repository.sheets.ChickensTable
import co.qwex.chickenapi.repository.sheets.FakeSheetsGateway
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChickenRepositoryTests {
    private val gateway = FakeSheetsGateway()
    private val repository = ChickenRepository(gateway)

    @Test
    fun `getChickenById returns chicken when row is complete`() {
        gateway.seed(
            ChickensTable,
            listOf(
                listOf(1, 2, "Henrietta", "img"),
            ),
        )

        val chicken = repository.getChickenById(1)

        assertEquals(
            Chicken(id = 1, breedId = 2, name = "Henrietta", imageUrl = "img"),
            chicken,
        )
    }

    @Test
    fun `getChickenById returns null when row is incomplete`() {
        gateway.seed(
            ChickensTable,
            listOf(
                listOf(1, 2),
            ),
        )

        val chicken = repository.getChickenById(1)

        assertNull(chicken)
    }
}
