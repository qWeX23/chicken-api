package co.qwex.chickenapi.controller

import co.qwex.chickenapi.ai.KoogChickenFactsAgent
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
 * REST controller that exposes the Koog cloud agent functionality for chicken facts.
 * Delegates requests to the cloud-based AI agent to fetch interesting chicken facts.
 */
@RestController
@RequestMapping("api/v1/chicken-facts")
class ChickenFactsController(
    private val koogChickenFactsAgent: KoogChickenFactsAgent,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Fetches chicken facts by delegating to the cloud agent.
     * Returns interesting, recent facts about chickens with source citations.
     */
    @GetMapping
    fun getChickenFacts(): ResponseEntity<ChickenFactsResponse> {
        log.info { "Received request to fetch chicken facts from cloud agent" }

        if (!koogChickenFactsAgent.isReady()) {
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
                koogChickenFactsAgent.fetchChickenFacts()
            }

            if (facts.isNullOrBlank()) {
                log.warn { "Cloud agent returned no facts" }
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
                log.info { "Successfully fetched chicken facts from cloud agent" }
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
            log.error(ex) { "Error fetching chicken facts from cloud agent" }
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
