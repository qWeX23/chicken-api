package co.qwex.chickenapi.controller

import co.qwex.chickenapi.ChickenApiApplication
import co.qwex.chickenapi.config.TestConfig
import co.qwex.chickenapi.service.ChickenFactsService
import com.google.api.services.sheets.v4.Sheets
import kotlinx.coroutines.runBlocking
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
class ChickenFactsControllerTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var chickenFactsService: ChickenFactsService

    @MockitoBean(answers = org.mockito.Answers.RETURNS_DEEP_STUBS)
    lateinit var sheets: Sheets

    @Test
    fun `should return chicken facts when agent is ready and returns facts`() {
        // Given
        val facts = """
            - Chickens can recognize over 100 different faces. [https://example.com/source1]
            - The oldest chicken lived to be 16 years old. [https://example.com/source2]
        """.trimIndent()

        Mockito.`when`(chickenFactsService.isAgentReady()).thenReturn(true)
        Mockito.`when`(runBlocking { chickenFactsService.fetchChickenFacts() }).thenReturn(facts)

        // When & Then
        mockMvc.get("/api/v1/chicken-facts") {
        }.andExpect {
            status { isOk() }
            jsonPath("$.facts") { isNotEmpty() }
            jsonPath("$.error") { isEmpty() }
            jsonPath("$._links.self.href") { exists() }
        }
    }

    @Test
    fun `should return 503 when agent is not ready`() {
        // Given
        Mockito.`when`(chickenFactsService.isAgentReady()).thenReturn(false)

        // When & Then
        mockMvc.get("/api/v1/chicken-facts") {
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.facts") { isEmpty() }
            jsonPath("$.error") { value("Cloud agent is not available. Please check configuration.") }
            jsonPath("$._links.self.href") { exists() }
        }
    }

    @Test
    fun `should return 204 when agent returns no facts`() {
        // Given
        Mockito.`when`(chickenFactsService.isAgentReady()).thenReturn(true)
        Mockito.`when`(runBlocking { chickenFactsService.fetchChickenFacts() }).thenReturn(null)

        // When & Then
        mockMvc.get("/api/v1/chicken-facts") {
        }.andExpect {
            status { isNoContent() }
            jsonPath("$.facts") { isEmpty() }
            jsonPath("$.error") { value("No facts available at this time.") }
        }
    }

    @Test
    fun `should return 204 when agent returns blank facts`() {
        // Given
        Mockito.`when`(chickenFactsService.isAgentReady()).thenReturn(true)
        Mockito.`when`(runBlocking { chickenFactsService.fetchChickenFacts() }).thenReturn(null)

        // When & Then
        mockMvc.get("/api/v1/chicken-facts") {
        }.andExpect {
            status { isNoContent() }
            jsonPath("$.facts") { isEmpty() }
            jsonPath("$.error") { value("No facts available at this time.") }
        }
    }

    @Test
    fun `should return 500 when agent throws exception`() {
        // Given
        Mockito.`when`(chickenFactsService.isAgentReady()).thenReturn(true)
        Mockito.`when`(runBlocking { chickenFactsService.fetchChickenFacts() }).thenThrow(RuntimeException("Cloud service error"))

        // When & Then
        mockMvc.get("/api/v1/chicken-facts") {
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.facts") { isEmpty() }
            jsonPath("$.error") { value("Failed to fetch chicken facts: Cloud service error") }
            jsonPath("$._links.self.href") { exists() }
        }
    }
}
