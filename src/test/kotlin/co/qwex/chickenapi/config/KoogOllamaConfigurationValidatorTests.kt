package co.qwex.chickenapi.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class KoogOllamaConfigurationValidatorTests {

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
    fun `requires api key when any agent is enabled`() {
        val validator =
            KoogOllamaConfigurationValidator(
                ollamaProperties = KoogOllamaProperties(apiKey = null),
                chickenFactsAgentProperties = KoogAgentProperties(enabled = false),
                breedResearchAgentProperties = BreedResearchAgentProperties(enabled = true),
            )

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateConfiguration()
        }
    }
}
