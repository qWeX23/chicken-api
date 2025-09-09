import co.qwex.chickenapi.ChickenApiApplication
import co.qwex.chickenapi.config.TestConfig
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(classes = [ChickenApiApplication::class, TestConfig::class])
@AutoConfigureMockMvc
class ChickenControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean(answers = org.mockito.Answers.RETURNS_DEEP_STUBS)
    lateinit var sheets: Sheets

    private fun mockChickenById(id: Int, values: List<List<Any>>) {
        val valueRange = ValueRange().setValues(values)
        val range = "chickens!A${id + 1}:D${id + 1}"
        Mockito.`when`(
            sheets.spreadsheets().values().get(anyString(), eq(range)).execute(),
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
            .andExpect { jsonPath("$._links.self.href") { value("http://localhost/api/v1/chickens/$id") } }
            .andExpect { jsonPath("$._links.breed.href") { value("http://localhost/api/v1/breeds/2") } }
    }

    @Test
    fun `submit chicken for review`() {
        val payload = """{
            "name":"NewChicken",
            "breedId":1,
            "imageUrl":"img"
        }"""

        val values = sheets.spreadsheets().values()
        val append = Mockito.mock(Sheets.Spreadsheets.Values.Append::class.java)
        Mockito.`when`(values.append(anyString(), anyString(), Mockito.any())).thenReturn(append)
        Mockito.`when`(append.setValueInputOption(anyString())).thenReturn(append)
        Mockito.`when`(append.setInsertDataOption(anyString())).thenReturn(append)
        Mockito.`when`(append.execute()).thenReturn(AppendValuesResponse())

        mockMvc.post("/api/v1/chickens") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = payload
        }.andExpect { status { isAccepted() } }
    }
}
