package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogBreedResearchAgent
import co.qwex.chickenapi.model.AgentRunOutcome
import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.model.BreedResearchRecord
import co.qwex.chickenapi.repository.BreedRepository
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

@Serializable
data class BreedResearchJson(
    val breedId: Int,
    val report: String,
    val origin: String? = null,
    val eggColor: String? = null,
    val eggSize: String? = null,
    val temperament: String? = null,
    val description: String? = null,
    val numEggs: Int? = null,
    val sources: List<String>,
)

@Service
class BreedResearcherScheduledTaskService(
    private val koogBreedResearchAgent: KoogBreedResearchAgent,
    private val breedRepository: BreedRepository,
    private val breedResearchRepository: BreedResearchRepository,
) {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    @Scheduled(fixedRate = 86400000) // Every 24 hours
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

        // Parse JSON output from agent
        var researchData: BreedResearchJson? = null
        var outcome = AgentRunOutcome.FAILED
        var breedId: Int = -1
        var breedName: String = "UNKNOWN"
        val fieldsUpdated = mutableListOf<String>()

        if (response != null && response.isNotBlank()) {
            try {
                researchData = json.decodeFromString<BreedResearchJson>(response)
                breedId = researchData.breedId
                outcome = AgentRunOutcome.SUCCESS
                log.info { "Successfully parsed breed research for breed ID: $breedId" }

                // Get the breed to find its name and update it
                val existingBreed = breedRepository.getBreedById(breedId)
                if (existingBreed != null) {
                    breedName = existingBreed.name

                    // Determine which fields are being updated
                    if (researchData.origin != null && researchData.origin != existingBreed.origin) {
                        fieldsUpdated.add("origin")
                    }
                    if (researchData.eggColor != null && researchData.eggColor != existingBreed.eggColor) {
                        fieldsUpdated.add("eggColor")
                    }
                    if (researchData.eggSize != null && researchData.eggSize != existingBreed.eggSize) {
                        fieldsUpdated.add("eggSize")
                    }
                    if (researchData.temperament != null && researchData.temperament != existingBreed.temperament) {
                        fieldsUpdated.add("temperament")
                    }
                    if (researchData.description != null && researchData.description != existingBreed.description) {
                        fieldsUpdated.add("description")
                    }
                    if (researchData.numEggs != null && researchData.numEggs != existingBreed.numEggs) {
                        fieldsUpdated.add("numEggs")
                    }
                    if (researchData.sources.isNotEmpty()) {
                        fieldsUpdated.add("sources")
                    }

                    // Update the breed with new research data
                    val updatedBreed = Breed(
                        id = existingBreed.id,
                        name = existingBreed.name,
                        origin = researchData.origin ?: existingBreed.origin,
                        eggColor = researchData.eggColor ?: existingBreed.eggColor,
                        eggSize = researchData.eggSize ?: existingBreed.eggSize,
                        temperament = researchData.temperament ?: existingBreed.temperament,
                        description = researchData.description ?: existingBreed.description,
                        imageUrl = existingBreed.imageUrl,
                        numEggs = researchData.numEggs ?: existingBreed.numEggs,
                        updatedAt = null, // Will be set by repository
                        sources = researchData.sources.ifEmpty { existingBreed.sources },
                    )

                    try {
                        breedRepository.update(updatedBreed)
                        log.info { "Updated breed '${breedName}' with fields: $fieldsUpdated" }
                    } catch (ex: Exception) {
                        log.error(ex) { "Failed to update breed $breedId in repository" }
                        failureReason = "Failed to update breed: ${ex.message}"
                        outcome = AgentRunOutcome.FAILED
                    }
                } else {
                    log.error { "Breed with ID $breedId not found in repository" }
                    failureReason = "Breed not found: $breedId"
                    outcome = AgentRunOutcome.FAILED
                }
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
            AgentRunOutcome.SUCCESS -> log.info { "Breed research agent successfully researched breed '$breedName'" }
            AgentRunOutcome.NO_OUTPUT -> log.warn { "Breed research agent returned no output." }
            AgentRunOutcome.FAILED -> log.error { "Breed research agent failed.$failureDetails" }
        }

        // Create the research record
        val record = BreedResearchRecord(
            runId = runId,
            breedId = breedId,
            breedName = breedName,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMillis = Duration.between(startedAt, completedAt).toMillis(),
            outcome = outcome,
            report = researchData?.report,
            sourcesFound = researchData?.sources ?: emptyList(),
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
}
