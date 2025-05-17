package co.qwex.chickenapi.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController()
@RequestMapping("api/v1")
class BreedController {
    @GetMapping("breeds/")
    fun getAllBreeds(): List<Breed> {
        return emptyList()
    }
}

data class Breed(
    val name: String,
    val origin: String?,
    val eggColor: String?,
    val eggSize: String?,
    val temperament: String?,
    val description: String?,
    val imageUrl: String?,
)
