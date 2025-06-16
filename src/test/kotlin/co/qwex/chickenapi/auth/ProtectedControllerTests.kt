package co.qwex.chickenapi.auth

import co.qwex.chickenapi.ChickenApiApplication
import com.google.api.services.sheets.v4.Sheets
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request

@SpringBootTest(classes = [ChickenApiApplication::class])
@AutoConfigureMockMvc
class ProtectedControllerTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    lateinit var sheets: Sheets

    @Test
    fun `requires authentication`() {
        mockMvc.get("/api/v1/protected").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `returns user info with jwt`() {
        mockMvc.get("/api/v1/protected") {
            with(SecurityMockMvcRequestPostProcessors.jwt().jwt {
                it.claim("email", "test@example.com")
                it.subject("123")
            })
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.email") { value("test@example.com") } }
    }
}
