package co.qwex.chickenapi.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class KoogOllamaConfigurationValidatorTests {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `allows missing api key when all agents are disabled`() {
        val validator =
            KoogOllamaConfigurationValidator(
                ollamaProperties = KoogOllamaProperties(apiKey = null),
                chickenFactsAgentProperties = KoogAgentProperties(enabled = false),
                breedResearchAgentProperties = BreedResearchAgentProperties(enabled = false),
            )

        assertDoesNotThrow {
            validator.validateConfiguration()
        }
    }

    @Test
    fun `allows missing api key for local dependencies`() {
        val validator =
            KoogOllamaConfigurationValidator(
                ollamaProperties = KoogOllamaProperties(
                    baseUrl = "http://localhost:11434",
                    apiKey = null,
                ),
                chickenFactsAgentProperties = KoogAgentProperties(enabled = false),
                breedResearchAgentProperties = BreedResearchAgentProperties(enabled = true),
            )

        assertDoesNotThrow {
            validator.validateConfiguration()
        }
    }

    @Test
    fun `requires api key when explicitly configured`() {
        val validator =
            KoogOllamaConfigurationValidator(
                ollamaProperties = KoogOllamaProperties(apiKey = null, apiKeyRequired = true),
                chickenFactsAgentProperties = KoogAgentProperties(enabled = true),
                breedResearchAgentProperties = BreedResearchAgentProperties(enabled = false),
            )

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateConfiguration()
        }
    }

    @Test
    fun `accepts api key from a mounted secret file`() {
        val keyFile = tempDir.resolve("ollama-api-key")
        Files.writeString(keyFile, "dedicated-cloud-key\n")
        val properties = KoogOllamaProperties(
            apiKeyFile = keyFile.toString(),
            apiKeyRequired = true,
        )
        val validator =
            KoogOllamaConfigurationValidator(
                ollamaProperties = properties,
                chickenFactsAgentProperties = KoogAgentProperties(enabled = true),
                breedResearchAgentProperties = BreedResearchAgentProperties(enabled = false),
            )

        assertDoesNotThrow { validator.validateConfiguration() }
        assertEquals("dedicated-cloud-key", properties.resolvedApiKey)
    }

    @Test
    fun `mounted secret takes precedence over environment fallback`() {
        val keyFile = tempDir.resolve("ollama-api-key")
        Files.writeString(keyFile, "file-key\n")

        val properties = KoogOllamaProperties(
            apiKey = "environment-key",
            apiKeyFile = keyFile.toString(),
        )

        assertEquals("file-key", properties.resolvedApiKey)
    }

    @Test
    fun `local embedding endpoint does not inherit cloud api key`() {
        val properties = KoogOllamaProperties(
            baseUrl = "https://ollama.com",
            apiKey = "cloud-key",
            embeddingBaseUrl = "http://ollama:11434",
        )

        assertNull(properties.resolvedEmbeddingApiKey)
    }

    @Test
    fun `gateway base url resolves the generation api key`() {
        val properties = KoogOllamaProperties(
            baseUrl = "http://litellm:4000",
            apiKey = "gateway-key",
        )

        assertEquals("gateway-key", properties.resolvedGenerationApiKey)
        assertTrue(properties.generationUsesGateway)
    }

    @Test
    fun `cloud api key is not sent to a non-cloud generation endpoint`() {
        val properties = KoogOllamaProperties(
            baseUrl = "http://ollama:11434",
            apiKey = "cloud-key",
        )

        assertNull(properties.resolvedGenerationApiKey)
    }
}
