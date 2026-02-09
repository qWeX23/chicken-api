package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogBreedResearchAgent
import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.BreedResearchRecord
import co.qwex.chickenapi.repository.BreedResearchRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
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
) {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    @Scheduled(fixedRateString = "\${koog.breed-research-agent.scheduler.fixed-rate:PT5H}") // Default: every 5 hours
    fun runDailyBreedResearchTask() {
        log.info { "Breed Research scheduled task started at: ${LocalDateTime.now()}" }
        if (!koogBreedResearchAgent.isReady()) {
            log.info { "Breed research agent is not ready, skipping run." }
            return
        }

        val runId = UUID.randomUUID().toString()
        val startedAt = Instant.now()

        var failureReason: String? = null
        val response = try {
            runBlocking {
                koogBreedResearchAgent.researchBreed()
            }
        } catch (ex: Exception) {
            failureReason = ex.message
            log.error(ex) { "Breed research agent invocation threw an exception" }
            null
        }

        val completedAt = Instant.now()

        // Parse result from agent - the tool now saves directly, we just track the outcome
        var outcome = AgentRunOutcome.FAILED
        var breedId = -1
        var breedName = "UNKNOWN"
        var fieldsUpdated = emptyList<String>()
        var report: String? = null
        var sourcesFound = emptyList<String>()

        if (response != null && response.isNotBlank()) {
            // Parse save_breed_research JSON result from the agent response.
            val result = parseSaveResult(response)
            if (result != null) {
                breedId = result.breedId
                breedName = result.breedName
                fieldsUpdated = result.fieldsUpdated
                report = result.savedData?.description
                sourcesFound = result.savedData?.sources ?: emptyList()
                outcome = if (result.success) AgentRunOutcome.SUCCESS else AgentRunOutcome.FAILED
                if (!result.success) {
                    failureReason = result.error
                }
                log.info { "Parsed save result: success=${result.success}, breed='$breedName', fields=$fieldsUpdated" }
            } else {
                // Agent returned a response but we couldn't find save result - agent may have just chatted
                log.warn { "Agent response did not contain save_breed_research result" }
                outcome = AgentRunOutcome.NO_OUTPUT
            }
        } else if (response == null) {
            outcome = AgentRunOutcome.FAILED
        } else {
            outcome = AgentRunOutcome.NO_OUTPUT
        }

        val failureDetails = failureReason?.let { " Reason: $it" }.orEmpty()

        when (outcome) {
            AgentRunOutcome.SUCCESS -> log.info { "Breed research agent successfully researched breed '$breedName'" }
            AgentRunOutcome.NO_OUTPUT -> log.warn { "Breed research agent returned no actionable output." }
            AgentRunOutcome.FAILED -> log.error { "Breed research agent failed.$failureDetails" }
        }

        // Create the research record for tracking
        val record = BreedResearchRecord(
            runId = runId,
            breedId = breedId,
            breedName = breedName,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = Duration.between(startedAt, completedAt).toMillis(),
            outcome = outcome,
            report = report,
            sourcesFound = sourcesFound,
            fieldsUpdated = fieldsUpdated,
            errorMessage = when {
                outcome != AgentRunOutcome.FAILED -> null
                !failureReason.isNullOrBlank() -> failureReason
                response == null -> "Agent returned null response"
                else -> null
            },
        )

        try {
            breedResearchRepository.create(record)
            log.info { "Persisted breed research record for run $runId" }
        } catch (ex: Exception) {
            log.error(ex) { "Failed to persist breed research run ${record.runId} to Google Sheets." }
        }

        log.info { "Breed Research scheduled task completed at: ${LocalDateTime.now()}" }
    }

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
}
