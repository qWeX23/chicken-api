import co.qwex.chickenapi.ChickenApiApplication
import co.qwex.chickenapi.TestFixtures
import co.qwex.chickenapi.config.TestConfig
import co.qwex.chickenapi.repository.BreedRepository
import com.google.api.services.sheets.v4.Sheets
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(classes = [ChickenApiApplication::class, TestConfig::class])
@AutoConfigureMockMvc
class BreedPageControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var breedRepository: BreedRepository

    @MockitoBean(answers = org.mockito.Answers.RETURNS_DEEP_STUBS)
    lateinit var sheets: Sheets

    @Test
    fun `breeds page lists breeds`() {
        Mockito.`when`(breedRepository.getAllBreeds()).thenReturn(TestFixtures.breedList())

        mockMvc.get("/breeds")
            .andExpect { status { isOk() } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("Silkie")) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("Orpington")) } }
    }

    @Test
    fun `breed detail page`() {
        Mockito.`when`(breedRepository.getBreedById(1)).thenReturn(TestFixtures.silkie)

        mockMvc.get("/breeds/1/view")
            .andExpect { status { isOk() } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("Fluffy")) } }
    }
}
