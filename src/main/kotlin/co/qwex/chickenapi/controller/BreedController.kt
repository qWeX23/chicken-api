package co.qwex.chickenapi.controller

import mu.KotlinLogging
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

@RestController()
@RequestMapping("api/v1")
class BreedController(
    private val breedRepository: co.qwex.chickenapi.repository.BreedRepository,
) {
    @GetMapping("breeds/")
    fun getAllBreeds(): List<Breed> {
        log.info { "Fetching all breeds" }
       val breeds = breedRepository.getAllBreeds()
        log.info { "Fetched ${breeds.size} breeds" }
        return breeds.map { breed ->
            Breed(
                name = breed.name,
                origin = breed.origin,
                eggColor = breed.eggColor,
                eggSize = breed.eggSize,
                temperament = breed.temperament,
                description = breed.description,
                imageUrl = breed.imageUrl,
            )
        }
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
