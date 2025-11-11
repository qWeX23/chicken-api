package co.qwex.chickenapi.controller

import co.qwex.chickenapi.repository.BreedRepository
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.server.ResponseStatusException

private val log = KotlinLogging.logger {}

@Controller
@RequestMapping("/breeds")
class BreedPageController(
    private val breedRepository: BreedRepository,
) {

    @GetMapping
    fun breeds(model: Model): String {
        log.debug { "Rendering breeds page" }
        val breeds = breedRepository.getAllBreeds()
        model.addAttribute("breeds", breeds)
        return "breeds"
    }

    @GetMapping("/{id}/view")
    fun breedDetail(
        @PathVariable id: Int,
        model: Model,
    ): String {
        log.debug { "Rendering breed detail page for id=$id" }
        val breed = breedRepository.getBreedById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("breed", breed)
        return "breed"
    }
}
