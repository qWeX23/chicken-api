package co.qwex.chickenapi.controller

import co.qwex.chickenapi.service.ChickenFactsService
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.hateoas.RepresentationModel
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller that exposes chicken facts functionality.
 * Delegates to ChickenFactsService for business logic.
 */
@RestController
@RequestMapping("api/v1/chicken-facts")
class ChickenFactsController(
    private val chickenFactsService: ChickenFactsService,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Fetches chicken facts via the service layer.
     * Returns interesting, recent facts about chickens with source citations.
     */
    @GetMapping
    fun getChickenFacts(): ResponseEntity<ChickenFactsResponse> {
        log.info { "Received request to fetch chicken facts" }

        if (!chickenFactsService.isAgentReady()) {
            log.warn { "Cloud agent is not ready to process requests" }
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                    ChickenFactsResponse(
                        facts = null,
                        error = "Cloud agent is not available. Please check configuration.",
                    ).apply {
                        add(linkTo(methodOn(ChickenFactsController::class.java).getChickenFacts()).withSelfRel())
                    },
                )
        }

        return try {
            val facts = runBlocking {
                chickenFactsService.fetchChickenFacts()
            }

            if (facts == null) {
                log.warn { "No chicken facts available" }
                ResponseEntity
                    .status(HttpStatus.NO_CONTENT)
                    .body(
                        ChickenFactsResponse(
                            facts = null,
                            error = "No facts available at this time.",
                        ).apply {
                            add(linkTo(methodOn(ChickenFactsController::class.java).getChickenFacts()).withSelfRel())
                        },
                    )
            } else {
                log.info { "Successfully retrieved chicken facts" }
                ResponseEntity.ok(
                    ChickenFactsResponse(
                        facts = facts,
                        error = null,
                    ).apply {
                        add(linkTo(methodOn(ChickenFactsController::class.java).getChickenFacts()).withSelfRel())
                    },
                )
            }
        } catch (ex: Exception) {
            log.error(ex) { "Error fetching chicken facts" }
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    ChickenFactsResponse(
                        facts = null,
                        error = "Failed to fetch chicken facts: ${ex.message}",
                    ).apply {
                        add(linkTo(methodOn(ChickenFactsController::class.java).getChickenFacts()).withSelfRel())
                    },
                )
        }
    }

    /**
     * Response model for chicken facts endpoint with HATEOAS support.
     */
    data class ChickenFactsResponse(
        val facts: String?,
        val error: String?,
    ) : RepresentationModel<ChickenFactsResponse>()
}
