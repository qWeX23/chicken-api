package co.qwex.chickenapi.controller

import co.qwex.chickenapi.ai.KoogBreedResearchAgent
import co.qwex.chickenapi.model.BreedResearchRecord
import co.qwex.chickenapi.repository.BreedResearchRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.hateoas.CollectionModel
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

private val log = KotlinLogging.logger {}

data class BreedResearch(
    val runId: String,
    val breedId: Int,
    val breedName: String,
    val report: String?,
    val sources: List<String>,
    val fieldsUpdated: List<String>,
    val outcome: String,
    val completedAt: Instant,
    val durationMillis: Long,
)

@RestController
@RequestMapping("api/v1/breed-research")
class BreedResearchController(
    private val breedResearchRepository: BreedResearchRepository,
    private val breedResearchAgent: KoogBreedResearchAgent,
) {

    @Operation(
        summary = "Get all breed research records",
        description = "Retrieve all successful breed research records, sorted by most recent first.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "List of breed research records",
                content = [
                    Content(
                        mediaType = "application/hal+json",
                        schema = Schema(implementation = BreedResearch::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("", "/")
    fun getAllBreedResearch(): CollectionModel<EntityModel<BreedResearch>> {
        log.info { "Fetching all breed research records" }
        val research = breedResearchRepository.fetchAllSuccessfulResearch()
            .map { it.toBreedResearch() }
            .map { record ->
                EntityModel.of(record).apply {
                    add(linkTo(methodOn(BreedResearchController::class.java).getResearchById(record.runId)).withSelfRel())
                    add(linkTo(methodOn(BreedResearchController::class.java).getAllBreedResearch()).withRel("research"))
                    add(linkTo(methodOn(BreedResearchController::class.java).getBreedResearchHistory(record.breedId)).withRel("breed-history"))
                }
            }

        return CollectionModel.of(research).apply {
            add(linkTo(methodOn(BreedResearchController::class.java).getAllBreedResearch()).withSelfRel())
            add(linkTo(methodOn(BreedResearchController::class.java).getAgentStatus()).withRel("status"))
        }
    }

    @Operation(
        summary = "Get research history for a breed",
        description = "Retrieve all research records for a specific breed by ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Research history for the breed",
                content = [
                    Content(
                        mediaType = "application/hal+json",
                        schema = Schema(implementation = BreedResearch::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/breed/{breedId}")
    fun getBreedResearchHistory(
        @PathVariable breedId: Int,
    ): CollectionModel<EntityModel<BreedResearch>> {
        log.info { "Fetching research history for breed ID: $breedId" }
        val research = breedResearchRepository.fetchAllSuccessfulResearch()
            .filter { it.breedId == breedId }
            .map { it.toBreedResearch() }
            .map { record ->
                EntityModel.of(record).apply {
                    add(linkTo(methodOn(BreedResearchController::class.java).getResearchById(record.runId)).withSelfRel())
                    add(linkTo(methodOn(BreedResearchController::class.java).getBreedResearchHistory(breedId)).withRel("breed-history"))
                }
            }

        return CollectionModel.of(research).apply {
            add(linkTo(methodOn(BreedResearchController::class.java).getBreedResearchHistory(breedId)).withSelfRel())
            add(linkTo(methodOn(BreedResearchController::class.java).getAllBreedResearch()).withRel("all-research"))
            add(linkTo(methodOn(BreedResearchController::class.java).getAgentStatus()).withRel("status"))
        }
    }

    @Operation(
        summary = "Get research record by ID",
        description = "Retrieve a specific breed research record by its run ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Research record found",
                content = [
                    Content(
                        mediaType = "application/hal+json",
                        schema = Schema(implementation = BreedResearch::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "404", description = "Research record not found"),
        ],
    )
    @GetMapping("/{runId}")
    fun getResearchById(
        @PathVariable runId: String,
    ): EntityModel<BreedResearch> {
        log.info { "Fetching breed research with runId: $runId" }
        val research = breedResearchRepository.fetchAllSuccessfulResearch()
            .find { it.runId == runId }
            ?.toBreedResearch()
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Research record with ID $runId not found"
            )

        return EntityModel.of(research).apply {
            add(linkTo(methodOn(BreedResearchController::class.java).getResearchById(runId)).withSelfRel())
            add(linkTo(methodOn(BreedResearchController::class.java).getAllBreedResearch()).withRel("research"))
            add(linkTo(methodOn(BreedResearchController::class.java).getBreedResearchHistory(research.breedId)).withRel("breed-history"))
        }
    }

    @Operation(
        summary = "Get agent status",
        description = "Check if the breed research agent is ready to accept research requests.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Agent status",
            ),
        ],
    )
    @GetMapping("/status")
    fun getAgentStatus(): AgentStatus {
        return AgentStatus.forAgent("Breed Research Agent", breedResearchAgent.isReady())
    }

    private fun BreedResearchRecord.toBreedResearch() = BreedResearch(
        runId = runId,
        breedId = breedId,
        breedName = breedName,
        report = report,
        sources = sourcesFound,
        fieldsUpdated = fieldsUpdated,
        outcome = outcome.name,
        completedAt = completedAt,
        durationMillis = durationMillis,
    )
}
