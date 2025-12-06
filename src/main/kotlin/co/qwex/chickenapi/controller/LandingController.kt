package co.qwex.chickenapi.controller

import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.ChickenFactsRepository
import mu.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

private val log = KotlinLogging.logger {}
private val fallbackDailyFact = DailyFact(
    fact = "A hen's earlobe color often predicts the shade of the eggs she lays.",
    sourceUrl = "https://www.britannica.com/animal/chicken",
)

data class DailyFact(val fact: String, val sourceUrl: String? = null)

@Controller
class LandingController(
    private val chickenFactsRepository: ChickenFactsRepository,
) {
    @GetMapping("/")
    fun landing(model: Model): String {
        log.debug { "Rendering landing page" }
        val latestFact = chickenFactsRepository.fetchLatestChickenFact()
            ?.let(::toDailyFact)
            ?: fallbackDailyFact

        model.addAttribute("dailyFact", latestFact)
        return "index"
    }

    @GetMapping("/about")
    fun about(): String {
        log.debug { "Rendering about page" }
        return "about"
    }

    private fun toDailyFact(record: ChickenFactsRecord): DailyFact? {
        val fact = record.fact?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val sourceUrl = record.sourceUrl?.trim()?.takeIf { it.isNotBlank() }

        return DailyFact(fact = fact, sourceUrl = sourceUrl)
    }
}
