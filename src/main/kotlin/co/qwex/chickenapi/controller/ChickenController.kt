package co.qwex.chickenapi.controller

import co.qwex.chickenapi.repository.ChickenRepository
import co.qwex.chickenapi.controller.BreedController
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
    ): ChickenResponse {
        val chickenById = repository.getChickenById(id)
        chickenById ?: throw IllegalArgumentException("Chicken with ID $id not found")
        return chickenById.let {
            ChickenResponse(
                id = it.id,
                name = it.name,
                breedId = it.breedId,
                imageUrl = it.imageUrl,
            ).apply {
                add(linkTo(ChickenController::class.java).slash(id).withSelfRel())
                add(linkTo(BreedController::class.java).slash(it.breedId).withRel("breed"))
            }
        }
    }
}

data class ChickenResponse(
    val id: Int,
    val name: String,
    val breedId: Int,
    val imageUrl: String,
) : org.springframework.hateoas.RepresentationModel<ChickenResponse>()
