package co.qwex.chickenapi.ai

import co.qwex.chickenapi.config.KoogAgentProperties

data class KoogCredentialCheck(
    val hasCredentials: Boolean,
    val missingFields: List<String>,
    val missingEnvVars: List<String>,
)

object KoogCredentialValidator {
    fun validate(properties: KoogAgentProperties): KoogCredentialCheck {
        val apiKey = properties.apiKey?.takeIf { it.isNotBlank() }
        val clientId = properties.clientId?.takeIf { it.isNotBlank() }
        val clientSecret = properties.clientSecret?.takeIf { it.isNotBlank() }
        val hasCloudflareCredentials = clientId != null && clientSecret != null
        if (apiKey != null || hasCloudflareCredentials) {
            return KoogCredentialCheck(true, emptyList(), emptyList())
        }

        val missingFields = buildList {
            add("koog.agent.api-key")
            if (clientId == null) {
                add("koog.agent.client-id")
            }
            if (clientSecret == null) {
                add("koog.agent.client-secret")
            }
        }
        val missingEnvVars = buildList {
            add("KOOG_AGENT_API_KEY")
            add("OLLAMA_API_KEY")
            if (clientId == null) {
                add("KOOG_AGENT_CLIENT_ID")
                add("OLLAMA_CLIENT_ID")
            }
            if (clientSecret == null) {
                add("KOOG_AGENT_CLIENT_SECRET")
                add("OLLAMA_CLIENT_SECRET")
            }
        }
        return KoogCredentialCheck(false, missingFields, missingEnvVars)
    }
}
