package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogBreedResearchAgent
import co.qwex.chickenapi.config.BreedResearchAgentProperties
import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.BreedResearchRecord
import co.qwex.chickenapi.repository.BreedResearchRepository
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

/**
 * Result from the save_breed_research tool.
 * The tool saves directly to the repository, so we just track the outcome here.
 */
@Serializable
data class SaveBreedResearchResult(
    val success: Boolean,
    val breedId: Int,
    val breedName: String,
    val fieldsUpdated: List<String>,
    val error: String? = null,
    val savedData: SavedBreedData? = null,
)

/**
 * The actual breed data that was saved by the tool.
 */
@Serializable
data class SavedBreedData(
    val description: String,
    val origin: String? = null,
    val eggColor: String? = null,
    val eggSize: String? = null,
    val temperament: String? = null,
    val numEggs: Int? = null,
    val sources: List<String> = emptyList(),
)

@Service
class BreedResearcherScheduledTaskService(
    private val koogBreedResearchAgent: KoogBreedResearchAgent,
    private val breedResearchRepository: BreedResearchRepository,
    private val properties: BreedResearchAgentProperties,
    scheduledTaskMetrics: ScheduledTaskMetrics,
) {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val taskMonitor = scheduledTaskMetrics.register(TASK_NAME, properties.scheduler.timeout)

    @PostConstruct
    fun initializeMetrics() {
        refreshConfigurationMetrics()
        runCatching {
            val latestRun = breedResearchRepository.fetchLatestRun()
            val latestSuccess = breedResearchRepository.fetchAllSuccessfulResearch().firstOrNull()
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
        cron = "\${koog.breed-research-agent.scheduler.cron:0 15 5 * * *}",
        zone = "\${koog.breed-research-agent.scheduler.zone:America/Chicago}",
    )
    fun runDailyBreedResearchTask() {
        refreshConfigurationMetrics()
        if (!isEnabled()) {
            log.warn { "$TASK_NAME is disabled; scheduled invocation skipped" }
            return
        }

        val startedAt = Instant.now()
        if (!koogBreedResearchAgent.isReady()) {
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
            BreedResearchExecution(
                outcome = AgentRunOutcome.TIMEOUT,
                errorMessage = "Run exceeded timeout ${properties.scheduler.timeout}",
            )
        } catch (ex: Exception) {
            log.error(ex) { "$TASK_NAME run $runId failed" }
            BreedResearchExecution(
                outcome = AgentRunOutcome.FAILED,
                errorMessage = ex.message ?: ex.javaClass.simpleName,
            )
        }
        val completedAt = Instant.now()

        val record = BreedResearchRecord(
            runId = runId,
            breedId = execution.breedId,
            breedName = execution.breedName,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = Duration.between(startedAt, completedAt).toMillis(),
            outcome = execution.outcome,
            report = execution.report,
            sourcesFound = execution.sourcesFound,
            fieldsUpdated = execution.fieldsUpdated,
            errorMessage = execution.errorMessage,
        )

        var metricResult = execution.outcome.toScheduledTaskResult()
        try {
            breedResearchRepository.create(record)
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

    private suspend fun executeResearch(): BreedResearchExecution {
        val response = koogBreedResearchAgent.researchBreed()
            ?: return BreedResearchExecution(AgentRunOutcome.FAILED, errorMessage = "Agent returned null response")
        if (response.isBlank()) {
            return BreedResearchExecution(AgentRunOutcome.NO_OUTPUT)
        }

        val result = parseSaveResult(response)
            ?: return BreedResearchExecution(
                outcome = AgentRunOutcome.NO_OUTPUT,
                errorMessage = "Agent response did not contain a save result",
            )
        return BreedResearchExecution(
            outcome = if (result.success) AgentRunOutcome.SUCCESS else AgentRunOutcome.FAILED,
            breedId = result.breedId,
            breedName = result.breedName,
            report = result.savedData?.description,
            sourcesFound = result.savedData?.sources.orEmpty(),
            fieldsUpdated = result.fieldsUpdated,
            errorMessage = result.error,
        )
    }

    private fun persistNotReadyRun(startedAt: Instant) {
        val completedAt = Instant.now()
        taskMonitor.recordSkipped(ScheduledTaskResult.NOT_READY, completedAt)
        runCatching {
            breedResearchRepository.create(
                BreedResearchRecord(
                    runId = UUID.randomUUID().toString(),
                    breedId = -1,
                    breedName = "UNKNOWN",
                    startedAt = startedAt,
                    completedAt = completedAt,
                    durationMillis = Duration.between(startedAt, completedAt).toMillis(),
                    outcome = AgentRunOutcome.NOT_READY,
                    report = null,
                    sourcesFound = emptyList(),
                    fieldsUpdated = emptyList(),
                    errorMessage = "Agent dependencies are not ready",
                ),
            )
        }.onFailure { ex ->
            log.error(ex) { "Failed to persist $TASK_NAME not-ready run" }
        }
    }

    private fun refreshConfigurationMetrics() {
        taskMonitor.setConfiguration(isEnabled(), koogBreedResearchAgent.isReady())
    }

    private fun isEnabled(): Boolean = properties.enabled && properties.scheduler.enabled

    private data class BreedResearchExecution(
        val outcome: AgentRunOutcome,
        val breedId: Int = -1,
        val breedName: String = "UNKNOWN",
        val report: String? = null,
        val sourcesFound: List<String> = emptyList(),
        val fieldsUpdated: List<String> = emptyList(),
        val errorMessage: String? = null,
    )

    /**
     * Parses save_breed_research JSON from the response.
     *
     * First attempts to parse the full response as JSON (the expected path).
     * If that fails, attempts to extract the JSON object containing "success".
     */
    private fun parseSaveResult(response: String): SaveBreedResearchResult? {
        val trimmed = response.trim()

        // Expected case: strategy returns the tool JSON directly.
        try {
            return json.decodeFromString<SaveBreedResearchResult>(trimmed)
        } catch (_: Exception) {
            // Fall through to extraction mode.
        }

        val saveResultJson = extractSaveResultJson(trimmed) ?: return null
        return try {
            json.decodeFromString<SaveBreedResearchResult>(saveResultJson)
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to parse extracted save_breed_research JSON" }
            null
        }
    }

    /**
     * Extracts the JSON result from save_breed_research tool from the agent's response.
     * The response may contain the tool result embedded in the agent's final message.
     */
    private fun extractSaveResultJson(response: String): String? {
        val successKeyIndex = response.indexOf("\"success\"")
        if (successKeyIndex < 0) {
            return null
        }

        val startIndex = response.lastIndexOf('{', successKeyIndex)
        if (startIndex < 0) {
            return null
        }

        var braceCount = 0
        var inString = false
        var escaping = false

        for (i in startIndex until response.length) {
            val char = response[i]

            if (escaping) {
                escaping = false
                continue
            }

            if (char == '\\' && inString) {
                escaping = true
                continue
            }

            if (char == '"') {
                inString = !inString
                continue
            }

            if (!inString) {
                when (char) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            return response.substring(startIndex, i + 1)
                        }
                    }
                }
            }
        }

        return null
    }

    companion object {
        private const val TASK_NAME = "breed-research"
    }
}
