package co.qwex.chickenapi.controller

import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.db.ChickenFactsSheetRepository
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

private val log = KotlinLogging.logger {}

data class ChickenFact(
    val id: String,
    val fact: String,
    val sourceUrl: String?,
    val completedAt: Instant,
    val durationMillis: Long,
)

@RestController
@RequestMapping("api/v1/facts")
class ChickenFactsController(
    private val chickenFactsSheetRepository: ChickenFactsSheetRepository,
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
        val facts = chickenFactsSheetRepository.fetchAllSuccessfulChickenFacts()
            .map { it.toChickenFact() }
            .map { fact ->
                EntityModel.of(fact).apply {
                    add(linkTo(methodOn(ChickenFactsController::class.java).getAllChickenFacts()).withRel("facts"))
                }
            }

        return CollectionModel.of(facts).apply {
            add(linkTo(methodOn(ChickenFactsController::class.java).getAllChickenFacts()).withSelfRel())
        }
    }

    private fun ChickenFactsRecord.toChickenFact() = ChickenFact(
        id = runId,
        fact = fact ?: "",
        sourceUrl = sourceUrl,
        completedAt = completedAt,
        durationMillis = durationMillis,
    )
}
