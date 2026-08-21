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
            "A readable Ollama API key is required, and koog.ollama.base-url must be https://ollama.com"
        }
    }
}
