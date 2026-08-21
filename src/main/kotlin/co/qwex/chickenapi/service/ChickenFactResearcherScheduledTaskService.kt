package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogChickenFactsAgent
import co.qwex.chickenapi.ai.OllamaEmbeddingService
import co.qwex.chickenapi.config.KoogAgentProperties
import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.ChickenFactsRepository
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Serializable
data class ChickenFactJson(
    val fact: String,
    val sourceUrl: String,
)

@Service
class ChickenFactResearcherScheduledTaskService(
    private val koogChickenFactsAgent: KoogChickenFactsAgent,
    private val ollamaEmbeddingService: OllamaEmbeddingService,
    private val chickenFactsRepository: ChickenFactsRepository,
    private val properties: KoogAgentProperties,
    scheduledTaskMetrics: ScheduledTaskMetrics,
) {

    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val taskMonitor = scheduledTaskMetrics.register(TASK_NAME, properties.scheduler.timeout)

    @PostConstruct
    fun initializeMetrics() {
        refreshConfigurationMetrics()
        runCatching {
            val latestRun = chickenFactsRepository.fetchLatestRun()
            val latestSuccess = chickenFactsRepository.fetchLatestChickenFact()
            taskMonitor.restore(
                latestRunStartedAt = latestRun?.startedAt,
                latestRunCompletedAt = latestRun?.completedAt,
                latestRunDurationMillis = latestRun?.durationMillis,
                latestRunOutcome = latestRun?.outcome,
                latestSuccessAt = latestSuccess?.completedAt,
            )
            taskMonitor.setHistoryLoaded(true)
        }.onFailure { ex ->
            log.warn(ex) { "Unable to restore $TASK_NAME metrics from Google Sheets" }
        }
        log.info {
            "$TASK_NAME configured: enabled=${isEnabled()}, cron='${properties.scheduler.cron}', zone=${properties.scheduler.zone}, timeout=${properties.scheduler.timeout}"
        }
    }

    @Scheduled(
        cron = "\${koog.agent.scheduler.cron:0 15 4 * * *}",
        zone = "\${koog.agent.scheduler.zone:America/Chicago}",
    )
    fun runDailyChickenFactResearcherTask() {
        refreshConfigurationMetrics()
        if (!isEnabled()) {
            log.warn { "$TASK_NAME is disabled; scheduled invocation skipped" }
            return
        }

        val startedAt = Instant.now()
        if (!koogChickenFactsAgent.isReady()) {
            log.error { "$TASK_NAME dependencies are not ready; scheduled invocation skipped" }
            persistNotReadyRun(startedAt)
            return
        }

        if (!taskMonitor.tryStart(startedAt)) {
            log.warn { "$TASK_NAME is already running; overlapping invocation skipped" }
            return
        }

        val runId = UUID.randomUUID().toString()
        log.info { "$TASK_NAME run $runId started" }

        val execution = try {
            runBlocking {
                withTimeout(properties.scheduler.timeout.toMillis()) {
                    executeResearch()
                }
            }
        } catch (ex: TimeoutCancellationException) {
            log.error(ex) { "$TASK_NAME run $runId timed out after ${properties.scheduler.timeout}" }
            ChickenFactExecution(
                outcome = AgentRunOutcome.TIMEOUT,
                errorMessage = "Run exceeded timeout ${properties.scheduler.timeout}",
            )
        } catch (ex: Exception) {
            log.error(ex) { "$TASK_NAME run $runId failed" }
            ChickenFactExecution(
                outcome = AgentRunOutcome.FAILED,
                errorMessage = ex.message ?: ex.javaClass.simpleName,
            )
        }
        val completedAt = Instant.now()

        val record = ChickenFactsRecord(
            runId = runId,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = Duration.between(startedAt, completedAt).toMillis(),
            outcome = execution.outcome,
            fact = execution.fact,
            sourceUrl = execution.sourceUrl,
            factEmbedding = execution.factEmbedding,
            errorMessage = execution.errorMessage,
        )

        var metricResult = execution.outcome.toScheduledTaskResult()
        try {
            chickenFactsRepository.create(record)
        } catch (ex: Exception) {
            metricResult = ScheduledTaskResult.PERSISTENCE_FAILURE
            log.error(ex) { "Failed to persist $TASK_NAME run $runId to Google Sheets" }
        }
        taskMonitor.complete(metricResult, startedAt, Instant.now())
        log.info { "$TASK_NAME run $runId completed with result ${metricResult.metricValue}" }
    }

    @Scheduled(fixedDelayString = "\${koog.ollama.readiness-metrics-refresh-delay:PT15S}")
    fun refreshReadinessMetrics() {
        refreshConfigurationMetrics()
    }

    private suspend fun executeResearch(): ChickenFactExecution {
        val response = koogChickenFactsAgent.fetchChickenFacts()
            ?: return ChickenFactExecution(AgentRunOutcome.FAILED, errorMessage = "Agent returned null response")
        if (response.isBlank()) {
            return ChickenFactExecution(AgentRunOutcome.NO_OUTPUT)
        }

        val factData = try {
            json.decodeFromString<ChickenFactJson>(response)
        } catch (ex: Exception) {
            return ChickenFactExecution(
                outcome = AgentRunOutcome.FAILED,
                errorMessage = "Failed to parse agent JSON: ${ex.message}",
            )
        }
        if (factData.fact.isBlank() || factData.sourceUrl.isBlank()) {
            return ChickenFactExecution(
                outcome = AgentRunOutcome.FAILED,
                errorMessage = "Agent returned a blank fact or source URL",
            )
        }
        if (!ollamaEmbeddingService.isReady()) {
            return ChickenFactExecution(
                outcome = AgentRunOutcome.FAILED,
                errorMessage = "Embedding service is not ready",
            )
        }

        val embedding = ollamaEmbeddingService.embedFact(factData.fact.trim())
            ?: return ChickenFactExecution(
                outcome = AgentRunOutcome.FAILED,
                errorMessage = "Embedding generation failed",
            )
        return ChickenFactExecution(
            outcome = AgentRunOutcome.SUCCESS,
            fact = factData.fact.trim(),
            sourceUrl = factData.sourceUrl.trim(),
            factEmbedding = embedding,
        )
    }

    private fun persistNotReadyRun(startedAt: Instant) {
        val completedAt = Instant.now()
        taskMonitor.recordSkipped(ScheduledTaskResult.NOT_READY, completedAt)
        runCatching {
            chickenFactsRepository.create(
                ChickenFactsRecord(
                    runId = UUID.randomUUID().toString(),
                    startedAt = startedAt,
                    completedAt = completedAt,
                    durationMillis = Duration.between(startedAt, completedAt).toMillis(),
                    outcome = AgentRunOutcome.NOT_READY,
                    fact = null,
                    sourceUrl = null,
                    errorMessage = "Agent dependencies are not ready",
                ),
            )
        }.onFailure { ex ->
            log.error(ex) { "Failed to persist $TASK_NAME not-ready run" }
        }
    }

    private fun refreshConfigurationMetrics() {
        taskMonitor.setConfiguration(
            isEnabled(),
            koogChickenFactsAgent.isReady() && ollamaEmbeddingService.isReady(),
        )
    }

    private fun isEnabled(): Boolean = properties.enabled && properties.scheduler.enabled

    private data class ChickenFactExecution(
        val outcome: AgentRunOutcome,
        val fact: String? = null,
        val sourceUrl: String? = null,
        val factEmbedding: List<Double>? = null,
        val errorMessage: String? = null,
    )

    companion object {
        private const val TASK_NAME = "chicken-facts"
    }
}
