package co.qwex.chickenapi.controller

import co.qwex.chickenapi.repository.ChickenRepository
import co.qwex.chickenapi.service.PendingChicken
import co.qwex.chickenapi.service.ReviewQueue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@RestController
@RequestMapping("api/v1/chickens")
class ChickenController(
    val repository: ChickenRepository,
    private val reviewQueue: ReviewQueue,
) {
    @Operation(
        summary = "Get chicken by ID",
        description = "Retrieve a single chicken by its identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Chicken found",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = Chicken::class),
                        examples = [
                            ExampleObject(
                                value = "{\"id\":1,\"name\":\"Clucky\",\"breedId\":1,\"imageUrl\":\"http://example.com/chicken.jpg\"}",
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(responseCode = "404", description = "Chicken not found"),
        ],
    )
    @GetMapping("{id}")
    fun getChickenById(
        @PathVariable id: Int,
    ): EntityModel<Chicken> {
        val chickenById = repository.getChickenById(id)
        chickenById ?: throw IllegalArgumentException("Chicken with ID $id not found")
        return chickenById.let {
            val model = EntityModel.of(
                Chicken(
                    id = it.id,
                    name = it.name,
                    breedId = it.breedId,
                    imageUrl = it.imageUrl,
                ),
            )
            model.add(linkTo(ChickenController::class.java).slash(id).withSelfRel())
            model.add(linkTo(BreedController::class.java).slash(it.breedId).withRel("breed"))
            model
        }
    }

    @Operation(
        summary = "Submit chicken for review",
        description = "Propose a new chicken entry to be reviewed and added.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "202", description = "Chicken accepted for review"),
            ApiResponse(responseCode = "400", description = "Invalid chicken data"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submitChickenForReview(
        @OpenApiRequestBody(
            required = true,
            description = "Chicken to review",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PendingChicken::class),
                    examples = [
                        ExampleObject(
                            value = "{\"name\":\"Clucky\",\"breedId\":1,\"imageUrl\":\"http://example.com/chicken.jpg\"}",
                        ),
                    ],
                ),
            ],
        )
        @RequestBody chicken: PendingChicken,
    ) {
        reviewQueue.addChicken(chicken)
    }
}

data class Chicken(
    val id: Int,
    val name: String,
    val breedId: Int,
    val imageUrl: String,
)
