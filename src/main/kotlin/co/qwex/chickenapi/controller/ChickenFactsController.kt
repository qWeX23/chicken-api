package co.qwex.chickenapi.controller

import co.qwex.chickenapi.ai.KoogChickenFactsAgent
import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.ChickenFactsRepository
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

data class ChickenFact(
    val runId: String,
    val fact: String,
    val sourceUrl: String?,
    val embedding: List<Double>?,
    val outcome: String,
    val completedAt: Instant,
    val durationMillis: Long,
)

@RestController
@RequestMapping("api/v1/facts")
class ChickenFactsController(
    private val chickenFactsRepository: ChickenFactsRepository,
    private val chickenFactsAgent: KoogChickenFactsAgent,
) {

    @Operation(
        summary = "Get all chicken facts",
        description = "Retrieve all successfully researched chicken facts, sorted by most recent first.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "List of chicken facts",
                content = [
                    Content(
                        mediaType = "application/hal+json",
                        schema = Schema(implementation = ChickenFact::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("", "/")
    fun getAllChickenFacts(): CollectionModel<EntityModel<ChickenFact>> {
        log.info { "Fetching all chicken facts" }
        val facts = chickenFactsRepository.fetchAllSuccessfulChickenFacts()
            .map { it.toChickenFact() }
            .map { fact ->
                EntityModel.of(fact).apply {
                    add(linkTo(methodOn(ChickenFactsController::class.java).getAllChickenFacts()).withSelfRel())
                    add(linkTo(methodOn(ChickenFactsController::class.java).getFactById(fact.runId)).withRel("self"))
                }
            }

        return CollectionModel.of(facts).apply {
            add(linkTo(methodOn(ChickenFactsController::class.java).getAllChickenFacts()).withSelfRel())
            add(linkTo(methodOn(ChickenFactsController::class.java).getAgentStatus()).withRel("status"))
        }
    }

    @Operation(
        summary = "Get chicken fact by ID",
        description = "Retrieve a specific chicken fact by its run ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Chicken fact found",
                content = [
                    Content(
                        mediaType = "application/hal+json",
                        schema = Schema(implementation = ChickenFact::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "404", description = "Fact not found"),
        ],
    )
    @GetMapping("/{runId}")
    fun getFactById(
        @PathVariable runId: String,
    ): EntityModel<ChickenFact> {
        log.info { "Fetching chicken fact with runId: $runId" }
        val fact = chickenFactsRepository.fetchAllSuccessfulChickenFacts()
            .find { it.runId == runId }
            ?.toChickenFact()
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Fact with ID $runId not found"
            )

        return EntityModel.of(fact).apply {
            add(linkTo(methodOn(ChickenFactsController::class.java).getFactById(runId)).withSelfRel())
            add(linkTo(methodOn(ChickenFactsController::class.java).getAllChickenFacts()).withRel("facts"))
        }
    }

    @Operation(
        summary = "Get agent status",
        description = "Check if the chicken facts agent is ready to accept research requests.",
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
        return AgentStatus.forAgent("Chicken Facts Agent", chickenFactsAgent.isReady())
    }

    private fun ChickenFactsRecord.toChickenFact() = ChickenFact(
        runId = runId,
        fact = fact ?: "",
        sourceUrl = sourceUrl,
        embedding = factEmbedding,
        outcome = outcome.name,
        completedAt = completedAt,
        durationMillis = durationMillis,
    )
}
