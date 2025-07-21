import co.qwex.chickenapi.ChickenApiApplication
import co.qwex.chickenapi.config.TestConfig
import co.qwex.chickenapi.service.ReviewQueue
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
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

    @Autowired
    lateinit var reviewQueue: ReviewQueue

    @MockitoBean(answers = org.mockito.Answers.RETURNS_DEEP_STUBS)
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

        mockMvc.post("/api/v1/chickens") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = payload
        }.andExpect { status { isAccepted() } }

        assert(reviewQueue.getChickens().any { it.name == "NewChicken" })
    }
}
