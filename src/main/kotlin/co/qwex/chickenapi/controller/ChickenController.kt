package co.qwex.chickenapi.controller

import co.qwex.chickenapi.repository.ChickenRepository
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("api/v1/chickens")
class ChickenController(val repository: ChickenRepository) {
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
}

data class Chicken(
    val id: Int,
    val name: String,
    val breedId: Int,
    val imageUrl: String,
)
