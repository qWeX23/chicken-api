import co.qwex.chickenapi.ChickenApiApplication
import com.google.api.services.sheets.v4.Sheets
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(classes = [ChickenApiApplication::class])
@AutoConfigureMockMvc
class AuthenticatedControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean(answers = org.mockito.Answers.RETURNS_DEEP_STUBS)
    lateinit var sheets: Sheets

    @Test
    fun `requires authentication`() {
        mockMvc.get("/api/v1/auth/hello")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `authenticated request`() {
        mockMvc.get("/api/v1/auth/hello") {
            with(jwt())
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("hello authenticated world") } }
    }
}
