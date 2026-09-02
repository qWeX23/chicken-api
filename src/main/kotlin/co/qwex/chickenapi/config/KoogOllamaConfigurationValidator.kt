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

        require(
            !ollamaProperties.generationUsesOllamaCloud ||
                !ollamaProperties.apiKeyRequired ||
                ollamaProperties.resolvedGenerationApiKey != null,
        ) {
            "A readable gateway API key is required when koog.ollama.base-url points at Ollama Cloud"
        }
    }
}
