package co.qwex.chickenapi.config

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class KoogOllamaConfigurationValidator(
    private val ollamaProperties: KoogOllamaProperties,
    private val chickenFactsAgentProperties: KoogAgentProperties,
    private val breedResearchAgentProperties: BreedResearchAgentProperties,
) {
    @PostConstruct
    fun validateConfiguration() {
        if (!chickenFactsAgentProperties.enabled && !breedResearchAgentProperties.enabled) {
            return
        }

        require(!ollamaProperties.apiKey.isNullOrBlank()) {
            "koog.ollama.api-key must be set when any Koog agent is enabled"
        }
    }
}
