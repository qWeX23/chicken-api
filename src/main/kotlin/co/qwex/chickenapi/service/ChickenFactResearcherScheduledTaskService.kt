package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogChickenFactsAgent
import co.qwex.chickenapi.ai.OllamaEmbeddingService
import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.ChickenFactsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@Serializable
data class ChickenFactJson(
    val fact: String,
    val sourceUrl: String,
)

@Service
@ConditionalOnProperty(
    name = ["koog.agent.scheduler.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class ChickenFactResearcherScheduledTaskService(
    private val koogChickenFactsAgent: KoogChickenFactsAgent,
    private val ollamaEmbeddingService: OllamaEmbeddingService,
    private val chickenFactsRepository: ChickenFactsRepository,
) {

    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    @Scheduled(cron = "\${koog.agent.scheduler.cron:0 30 0 * * *}") // Default: 12:30 AM daily
    fun runDailyChickenFactResearcherTask() {
        log.info { "Chicken Fact Researcher scheduled task started at: ${LocalDateTime.now()}" }
        if (!koogChickenFactsAgent.isReady()) {
            log.info { "Koog agent is not ready, skipping run." }
            return
        }

        val runId = UUID.randomUUID().toString()
        val startedAt = Instant.now()

        var failureReason: String? = null
        val response = try {
            runBlocking {
                koogChickenFactsAgent.fetchChickenFacts()
            }
        } catch (ex: Exception) {
            failureReason = ex.message
            log.error(ex) { "Koog agent invocation threw an exception" }
            null
        }

        val completedAt = Instant.now()

        // Parse JSON output from agent
        var fact: String? = null
        var sourceUrl: String? = null
        var factEmbedding: List<Double>? = null
        var outcome = AgentRunOutcome.FAILED

        if (response != null && response.isNotBlank()) {
            try {
                val factData = json.decodeFromString<ChickenFactJson>(response)
                fact = factData.fact
                sourceUrl = factData.sourceUrl
                outcome = AgentRunOutcome.SUCCESS
                log.info { "Successfully parsed chicken fact: $fact from $sourceUrl" }
            } catch (ex: Exception) {
                log.error(ex) { "Failed to parse JSON response from agent: $response" }
                failureReason = "Failed to parse JSON: ${ex.message}"
                outcome = AgentRunOutcome.FAILED
            }
        } else if (response == null) {
            outcome = AgentRunOutcome.FAILED
        } else {
            outcome = AgentRunOutcome.NO_OUTPUT
        }

        val failureDetails = failureReason?.let { " Reason: $it" }.orEmpty()

        when (outcome) {
            AgentRunOutcome.SUCCESS -> {
                log.info { "Koog agent successfully produced fact" }
                if (!fact.isNullOrBlank() && ollamaEmbeddingService.isReady()) {
                    factEmbedding = runBlocking {
                        ollamaEmbeddingService.embedFact(fact!!.trim())
                    }
                    if (factEmbedding == null) {
                        log.warn { "Embedding generation failed for chicken fact run $runId" }
                    }
                } else if (fact.isNullOrBlank()) {
                    log.warn { "Skipping embedding generation; chicken fact is blank." }
                } else {
                    log.warn { "Skipping embedding generation; embedding service is not ready." }
                }
            }
            AgentRunOutcome.NO_OUTPUT -> log.warn { "Koog agent returned no chicken facts." }
            AgentRunOutcome.FAILED -> log.error { "Koog agent failed to produce chicken facts.$failureDetails" }
        }

        val record = ChickenFactsRecord(
            runId = runId,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = Duration.between(startedAt, completedAt).toMillis(),
            outcome = outcome,
            fact = fact,
            sourceUrl = sourceUrl,
            factEmbedding = factEmbedding,
            errorMessage = when {
                outcome != AgentRunOutcome.FAILED -> null
                !failureReason.isNullOrBlank() -> failureReason
                response == null -> "Agent returned null response"
                else -> null
            },
        )

        try {
            chickenFactsRepository.create(record)
        } catch (ex: Exception) {
            log.error(ex) { "Failed to persist chicken facts run ${record.runId} to Google Sheets." }
        }
        log.info { "Chicken Fact Researcher scheduled task completed at: ${LocalDateTime.now()}" }
    }
}
