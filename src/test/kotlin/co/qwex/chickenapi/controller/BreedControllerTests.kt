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
class BreedControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var reviewQueue: ReviewQueue

    @MockitoBean(answers = org.mockito.Answers.RETURNS_DEEP_STUBS)
    lateinit var sheets: Sheets

    private fun mockBreedListResponse(values: List<List<Any>>) {
        val valueRange = ValueRange().setValues(values)
        Mockito.`when`(
            sheets.spreadsheets().values().get(anyString(), Mockito.eq("breeds!A1:I")).execute(),
        ).thenReturn(valueRange)
    }

    private fun mockBreedByIdResponse(id: Int, values: List<List<Any>>) {
        val valueRange = ValueRange().setValues(values)
        val range = "breeds!A${id + 1}:I${id + 1}"
        Mockito.`when`(
            sheets.spreadsheets().values().get(anyString(), Mockito.eq(range)).execute(),
        ).thenReturn(valueRange)
    }

    @Test
    fun `get all breeds returns data`() {
        mockBreedListResponse(
            listOf(
                listOf("id", "name", "origin", "eggColor", "eggSize", "temperament", "description", "imageUrl", "numEggs"),
                listOf(1, "Silkie", "China", "White", "Small", "Docile", "Fluffy", "img", 200),
                listOf(2, "Orpington", "UK", "Brown", "Large", "Friendly", "Big", "img2", 180),
            ),
        )

        mockMvc.get("/api/v1/breeds/")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(2) } }
            .andExpect { jsonPath("$[0].name") { value("Silkie") } }
            .andExpect { jsonPath("$[1].name") { value("Orpington") } }
            .andExpect { jsonPath("$[0].eggNumber") { value(200) } }
            .andExpect { jsonPath("$[1].eggNumber") { value(180) } }
            .andExpect { jsonPath("$[0].links[0].href") { value("http://localhost/api/v1/breeds/1") } }
            .andExpect { jsonPath("$[0].links[0].rel") { value("self") } }
            .andExpect { jsonPath("$[1].links[0].href") { value("http://localhost/api/v1/breeds/2") } }
    }

    @Test
    fun `get breed by id`() {
        val id = 1
        mockBreedByIdResponse(id, listOf(listOf(1, "Silkie", "China", "White", "Small", "Docile", "Fluffy", "img", 200)))

        mockMvc.get("/api/v1/breeds/$id")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.name") { value("Silkie") } }
            .andExpect { jsonPath("$.eggNumber") { value(200) } }
            .andExpect { jsonPath("$._links.self.href") { value("http://localhost/api/v1/breeds/$id") } }
    }

    @Test
    fun `filter breeds by name`() {
        mockBreedListResponse(
            listOf(
                listOf("id", "name", "origin", "eggColor", "eggSize", "temperament", "description", "imageUrl", "numEggs"),
                listOf(1, "Silkie", "China", "White", "Small", "Docile", "Fluffy", "img", 200),
                listOf(2, "Orpington", "UK", "Brown", "Large", "Friendly", "Big", "img2", 180),
            ),
        )

        mockMvc.get("/api/v1/breeds/?name=Orpington")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(1) } }
            .andExpect { jsonPath("$[0].name") { value("Orpington") } }
            .andExpect { jsonPath("$[0].eggNumber") { value(180) } }
            .andExpect { jsonPath("$[0].links[0].href") { value("http://localhost/api/v1/breeds/2") } }
            .andExpect { jsonPath("$[0].links[0].rel") { value("self") } }
    }

    @Test
    fun `submit breed for review`() {
        val payload = """{
            "name":"NewBreed",
            "origin":"Earth",
            "eggColor":"Brown",
            "eggSize":"Large",
            "eggNumber":0,
            "temperament":"Calm",
            "description":"desc",
            "imageUrl":"img"
        }"""

        mockMvc.post("/api/v1/breeds/") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = payload
        }.andExpect { status { isAccepted() } }

        assert(reviewQueue.getBreeds().any { it.name == "NewBreed" })
    }

    @Test
    fun `get all breeds works without trailing slash`() {
        mockBreedListResponse(
            listOf(
                listOf("id", "name", "origin", "eggColor", "eggSize", "temperament", "description", "imageUrl", "numEggs"),
                listOf(1, "Silkie", "China", "White", "Small", "Docile", "Fluffy", "img", 200),
            ),
        )

        mockMvc.get("/api/v1/breeds")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(1) } }
            .andExpect { jsonPath("$[0].name") { value("Silkie") } }
    }

    @Test
    fun `submit breed for review works without trailing slash`() {
        val payload = """{
            "name":"AnotherBreed",
            "origin":"Mars",
            "eggColor":"Red",
            "eggSize":"Medium",
            "eggNumber":100,
            "temperament":"Aggressive",
            "description":"test",
            "imageUrl":"img2"
        }"""

        mockMvc.post("/api/v1/breeds") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = payload
        }.andExpect { status { isAccepted() } }

        assert(reviewQueue.getBreeds().any { it.name == "AnotherBreed" })
    }
}
