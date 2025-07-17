package co.qwex.chickenapi.controller

import co.qwex.chickenapi.repository.ChickenRepository
import co.qwex.chickenapi.service.PendingChicken
import co.qwex.chickenapi.service.ReviewQueue
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

@RestController
@RequestMapping("api/v1/chickens")
class ChickenController(
    val repository: ChickenRepository,
    private val reviewQueue: ReviewQueue,
) {
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

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submitChickenForReview(@RequestBody chicken: PendingChicken) {
        reviewQueue.addChicken(chicken)
    }
}

data class Chicken(
    val id: Int,
    val name: String,
    val breedId: Int,
    val imageUrl: String,
)
