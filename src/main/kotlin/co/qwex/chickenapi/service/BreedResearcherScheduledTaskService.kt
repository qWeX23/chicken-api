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

    @Scheduled(cron = "\${koog.breed-research-agent.scheduler.cron:0 0 */5 * * *}") // Default: every 5 hours
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

        if (response != null && response.isNotBlank()) {
            // Try to find and parse the save_breed_research result from the agent's response
            val saveResultJson = extractSaveResultJson(response)
            if (saveResultJson != null) {
                try {
                    val result = json.decodeFromString<SaveBreedResearchResult>(saveResultJson)
                    breedId = result.breedId
                    breedName = result.breedName
                    fieldsUpdated = result.fieldsUpdated
                    outcome = if (result.success) AgentRunOutcome.SUCCESS else AgentRunOutcome.FAILED
                    if (!result.success) {
                        failureReason = result.error
                    }
                    log.info { "Parsed save result: success=${result.success}, breed='$breedName', fields=$fieldsUpdated" }
                } catch (ex: Exception) {
                    log.warn(ex) { "Could not parse save_breed_research result, treating as success if agent completed" }
                    // If we can't parse but agent returned something, assume it worked
                    outcome = AgentRunOutcome.SUCCESS
                }
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
            report = null, // Report is now stored with the breed, not separately
            sourcesFound = emptyList(), // Sources are now stored with the breed
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
     * Extracts the JSON result from save_breed_research tool from the agent's response.
     * The response may contain the tool result embedded in the agent's final message.
     */
    private fun extractSaveResultJson(response: String): String? {
        // Look for JSON that matches our SaveBreedResearchResult structure
        val jsonPattern = """\{[^{}]*"success"\s*:\s*(true|false)[^{}]*"breedId"\s*:\s*\d+[^{}]*\}""".toRegex()
        val match = jsonPattern.find(response)
        if (match != null) {
            return match.value
        }

        // Try to find any JSON object with success field using brace matching
        val startIndex = response.indexOf("{\"success\"")
        if (startIndex >= 0) {
            var braceCount = 0
            var endIndex = startIndex
            for (i in startIndex until response.length) {
                when (response[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            endIndex = i + 1
                            break
                        }
                    }
                }
            }
            if (endIndex > startIndex) {
                return response.substring(startIndex, endIndex)
            }
        }

        return null
    }
}
