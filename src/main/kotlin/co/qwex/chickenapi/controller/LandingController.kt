package co.qwex.chickenapi.controller

import mu.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

private val log = KotlinLogging.logger {}

data class DailyFact(val fact: String, val sourceUrl: String? = null)

@Controller
class LandingController {
    @GetMapping("/")
    fun landing(model: Model): String {
        log.debug { "Rendering landing page" }
        model.addAttribute(
            "dailyFact",
            DailyFact(
                fact = "A hen's earlobe color often predicts the shade of the eggs she lays.",
                sourceUrl = "https://www.britannica.com/animal/chicken",
            ),
        )
        return "index"
    }

    @GetMapping("/about")
    fun about(): String {
        log.debug { "Rendering about page" }
        return "about"
    }
}
