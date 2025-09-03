package co.qwex.chickenapi.controller

import co.qwex.chickenapi.service.PendingBreed
import co.qwex.chickenapi.service.ReviewQueue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.apache.commons.text.similarity.LevenshteinDistance
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

private val log = KotlinLogging.logger {}

@RestController()
@RequestMapping("api/v1/breeds/")
class BreedController(
    private val breedRepository: co.qwex.chickenapi.repository.BreedRepository,
    private val reviewQueue: ReviewQueue,
) {

    @Operation(
        summary = "List chicken breeds",
        description = "Retrieve all known chicken breeds. Optionally filter by name.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Breeds retrieved",
                content = [
                    Content(
                        mediaType = "application/json",
                        array = ArraySchema(schema = Schema(implementation = Breed::class)),
                        examples = [
                            ExampleObject(
                                value = "[{\"name\":\"Silkie\",\"origin\":\"China\"}]",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping()
    fun getAllBreeds(
        @RequestParam(required = false) name: String?,
    ): List<EntityModel<Breed>> {
        log.info { "Fetching all breeds" }
        val breeds = breedRepository.getAllBreeds().map { breed ->
            val model = EntityModel.of(
                Breed(
                    name = breed.name,
                    origin = breed.origin,
                    eggColor = breed.eggColor,
                    eggSize = breed.eggSize,
                    temperament = breed.temperament,
                    description = breed.description,
                    imageUrl = breed.imageUrl,
                    eggNumber = breed.numEggs,
                ),
            )
            model.add(linkTo(BreedController::class.java).slash(breed.id).withSelfRel())
            model
        }
        log.info { "Fetched ${breeds.size} breeds" }

        if (name.isNullOrBlank()) return breeds
        val distance = LevenshteinDistance(2)
        log.info { "Filtered to ${breeds.size} breeds" }
        return breeds.filter {
            val breedName = it.content?.name ?: ""
            distance.apply(breedName.lowercase(), name.lowercase()) != -1
        }
    }

    @Operation(
        summary = "Get breed by ID",
        description = "Retrieve details for a specific breed by its identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Breed found",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = Breed::class),
                        examples = [
                            ExampleObject(
                                value = "{\"name\":\"Silkie\",\"origin\":\"China\"}",
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(responseCode = "404", description = "Breed not found"),
        ],
    )
    @GetMapping("{id}")
    fun getBreedById(
        @PathVariable id: Int,
    ): EntityModel<Breed> {
        log.info { "Fetching breed with ID $id" }
        val breed = breedRepository.getBreedById(id)
        log.info { "Fetched breed with ID $id: $breed" }
        breed ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Breed with ID $id not found")
        return breed.let {
            val model = EntityModel.of(
                Breed(
                    name = it.name,
                    origin = it.origin,
                    eggColor = it.eggColor,
                    eggSize = it.eggSize,
                    eggNumber = it.numEggs,
                    temperament = it.temperament,
                    description = it.description,
                    imageUrl = it.imageUrl,
                ),
            )
            model.add(linkTo(BreedController::class.java).slash(id).withSelfRel())
            model
        }
    }

    @Operation(
        summary = "Submit breed for review",
        description = "Propose a new chicken breed to be reviewed and added to the catalogue.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "202", description = "Breed accepted for review"),
            ApiResponse(responseCode = "400", description = "Invalid breed data"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submitBreedForReview(
        @OpenApiRequestBody(
            required = true,
            description = "Breed to review",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PendingBreed::class),
                    examples = [
                        ExampleObject(
                            value = "{\"name\":\"Silkie\",\"origin\":\"China\",\"eggColor\":\"White\"}",
                        ),
                    ],
                ),
            ],
        )
        @RequestBody breed: PendingBreed,
    ) {
        reviewQueue.addBreed(breed)
    }
}

data class Breed(
    val name: String,
    val origin: String?,
    val eggColor: String?,
    val eggSize: String?,
    val eggNumber: Int?,
    val temperament: String?,
    val description: String?,
    val imageUrl: String?,
)
