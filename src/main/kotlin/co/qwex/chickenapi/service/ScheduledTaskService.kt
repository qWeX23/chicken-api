package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogChickenFactsAgent
import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.db.ChickenFactsSheetRepository
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@Service
class ScheduledTaskService(
    private val koogChickenFactsAgent: KoogChickenFactsAgent,
    private val chickenFactsSheetRepository: ChickenFactsSheetRepository,
) {

    private val log = KotlinLogging.logger {}

    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    fun performScheduledTask() {
        log.info { "Scheduled task started at: ${LocalDateTime.now()}" }
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
        val outcome = when {
            response == null -> AgentRunOutcome.FAILED
            response.isBlank() -> AgentRunOutcome.NO_OUTPUT
            else -> AgentRunOutcome.SUCCESS
        }

        val failureDetails = failureReason?.let { " Reason: $it" }.orEmpty()

        when (outcome) {
            AgentRunOutcome.SUCCESS -> log.info { "Koog agent output:\n$response" }
            AgentRunOutcome.NO_OUTPUT -> log.warn { "Koog agent returned no chicken facts." }
            AgentRunOutcome.FAILED -> log.error { "Koog agent failed to produce chicken facts.$failureDetails" }
        }

        val record = ChickenFactsRecord(
            runId = runId,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = Duration.between(startedAt, completedAt).toMillis(),
            outcome = outcome,
            factsMarkdown = response,
            errorMessage = when {
                outcome != AgentRunOutcome.FAILED -> null
                !failureReason.isNullOrBlank() -> failureReason
                response == null -> "Agent returned null response"
                else -> null
            },
        )

        try {
            chickenFactsSheetRepository.append(record)
        } catch (ex: Exception) {
            log.error(ex) { "Failed to persist chicken facts run ${record.runId} to Google Sheets." }
        }
        log.info { "Scheduled task completed at: ${LocalDateTime.now()}" }
    }
}
