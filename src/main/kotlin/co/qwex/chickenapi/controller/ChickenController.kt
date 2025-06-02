package co.qwex.chickenapi.controller

import co.qwex.chickenapi.repository.ChickenRepository
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
    ): Chicken {
        val chickenById = repository.getChickenById(id)
        chickenById ?: throw IllegalArgumentException("Chicken with ID $id not found")
        return chickenById.let {
            Chicken(
                id = it.id,
                name = it.name,
                breedId = it.breedId,
                imageUrl = it.imageUrl,
            )
        }
    }
}

data class Chicken(
    val id: Int,
    val name: String,
    val breedId: Int,
    val imageUrl: String,
)
