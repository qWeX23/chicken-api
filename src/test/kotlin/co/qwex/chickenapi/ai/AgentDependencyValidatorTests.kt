package co.qwex.chickenapi.ai

import co.qwex.chickenapi.config.KoogOllamaProperties
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AgentDependencyValidatorTests {

    @Test
    fun `generation probe accepts an authenticated chat response`() {
        val client = clientResponding(HttpStatusCode.OK, """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}""")
        val validator = AgentDependencyValidator(KoogOllamaProperties())

        assertDoesNotThrow {
            runBlocking { validator.requireGeneration(client, "gpt-oss:120b") }
        }
    }

    @Test
    fun `generation probe requests a bounded non-streaming response`() {
        lateinit var requestBody: String
        val client =
            HttpClient(
                MockEngine { request ->
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    respond(
                        content = """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
        val validator = AgentDependencyValidator(KoogOllamaProperties())

        runBlocking { validator.requireGeneration(client, "gpt-oss:120b") }

        assertTrue(requestBody.contains("\"stream\":false"))
        assertTrue(requestBody.contains("\"max_tokens\":2"))
    }

    @Test
    fun `generation probe rejects unauthorized credentials`() {
        val client = clientResponding(HttpStatusCode.Unauthorized, """{"error":{"message":"Unauthorized"}}""")
        val validator = AgentDependencyValidator(KoogOllamaProperties())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { validator.requireGeneration(client, "gpt-oss:120b") }
        }
    }

    @Test
    fun `generation probe rejects a malformed successful response`() {
        val client = clientResponding(HttpStatusCode.OK, "{}")
        val validator = AgentDependencyValidator(KoogOllamaProperties())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { validator.requireGeneration(client, "gpt-oss:120b") }
        }
    }

    private fun clientResponding(status: HttpStatusCode, body: String): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
}
