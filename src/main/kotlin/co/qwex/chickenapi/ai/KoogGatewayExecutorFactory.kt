package co.qwex.chickenapi.ai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.ktor.client.HttpClient
import java.time.Duration

/**
 * Builds the Koog prompt executor and LLModel for the LiteLLM gateway.
 *
 * The gateway speaks the OpenAI Chat Completions API, so agents use Koog's
 * OpenAI-compatible client pointed at the gateway base URL.
 *
 * Auth: `OpenAILLMClient` injects its own `Authorization: Bearer <apiKey>`
 * header, so the Ktor `HttpClient` handed to it must not set an Authorization
 * header itself — sending the key twice made LiteLLM reject requests (the
 * duplicated header is hashed as `key,key`, which matches no virtual key).
 */
internal object KoogGatewayExecutorFactory {
    fun create(
        baseUrl: String,
        apiKey: String,
        httpClient: HttpClient,
        requestTimeout: Duration,
        modelId: String,
        contextLength: Long,
    ): AgentRuntimeComponents {
        val executor =
            MultiLLMPromptExecutor(
                LLMProvider.OpenAI to
                    OpenAILLMClient(
                        apiKey = apiKey,
                        settings =
                            OpenAIClientSettings(
                                baseUrl = normalizeOpenAiBaseUrl(baseUrl),
                                timeoutConfig =
                                    ConnectionTimeoutConfig(
                                        requestTimeoutMillis = requestTimeout.toMillis(),
                                        connectTimeoutMillis = 10_000,
                                        socketTimeoutMillis = requestTimeout.toMillis(),
                                    ),
                                chatCompletionsPath = "v1/chat/completions",
                                responsesAPIPath = "v1/responses",
                                embeddingsPath = "v1/embeddings",
                                moderationsPath = "v1/moderations",
                                modelsPath = "v1/models",
                            ),
                        httpClientFactory = KtorKoogHttpClient.Factory(httpClient),
                    ),
            )

        val model =
            LLModel(
                provider = LLMProvider.OpenAI,
                id = modelId,
                capabilities = listOf(
                    LLMCapability.Completion,
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.Schema.JSON.Basic,
                    LLMCapability.OpenAIEndpoint.Completions,
                ),
                contextLength = contextLength,
            )

        return AgentRuntimeComponents(executor, model)
    }

    /**
     * Koog's OpenAI client appends the endpoint paths itself, so the base URL
     * must not carry a trailing slash or a `/v1` suffix.
     */
    fun normalizeOpenAiBaseUrl(baseUrl: String): String =
        baseUrl
            .trim()
            .trimEnd('/')
            .removeSuffix("/v1")
}
