import co.qwex.chickenapi.ChickenApiApplication
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(classes = [ChickenApiApplication::class])
@AutoConfigureMockMvc
class ChickenControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    lateinit var sheets: Sheets

    private fun mockChickenById(id: Int, values: List<List<Any>>) {
        val valueRange = ValueRange().setValues(values)
        val range = "chickens!A${id + 1}:D${id + 1}"
        Mockito.`when`(
            sheets.spreadsheets().values().get(anyString(), Mockito.eq(range)).execute(),
        ).thenReturn(valueRange)
    }

    @Test
    fun `get chicken by id`() {
        val id = 1
        mockChickenById(id, listOf(listOf(1, 2, "Clucky", "imgUrl")))

        mockMvc.get("/api/v1/chickens/$id")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.name") { value("Clucky") } }
            .andExpect { jsonPath("$.breedId") { value(2) } }
    }
}
