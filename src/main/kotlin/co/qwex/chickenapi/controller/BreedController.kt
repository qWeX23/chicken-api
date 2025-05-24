package co.qwex.chickenapi.controller

import mu.KotlinLogging
import org.apache.commons.text.similarity.LevenshteinDistance
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

@RestController()
@RequestMapping("api/v1/breeds/")
class BreedController(
    private val breedRepository: co.qwex.chickenapi.repository.BreedRepository,
) {

    @GetMapping()
    fun getAllBreeds(
        @RequestParam(required = false) name: String?,
    ): List<Breed> {
        log.info { "Fetching all breeds" }
        val breeds = breedRepository.getAllBreeds().map { breed ->
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
        log.info { "Fetched ${breeds.size} breeds" }

        log.info { breedRepository.getBreedById(2) }

        if (name.isNullOrBlank()) return breeds
        val distance = LevenshteinDistance(2) // max distance for fuzziness
        return breeds.filter {
            distance.apply(it.name.lowercase(), name.lowercase()) != -1
        }
    }

    @GetMapping("{id}")
    fun getBreedById(
        @PathVariable id: Int,
    ): Breed? {
        log.info { "Fetching breed with ID $id" }
        val breed = breedRepository.getBreedById(id)
        log.info { "Fetched breed with ID $id: $breed" }
        return breed?.let {
            Breed(
                name = it.name,
                origin = it.origin,
                eggColor = it.eggColor,
                eggSize = it.eggSize,
                temperament = it.temperament,
                description = it.description,
                imageUrl = it.imageUrl,
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
