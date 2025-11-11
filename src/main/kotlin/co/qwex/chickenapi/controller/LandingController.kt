package co.qwex.chickenapi.controller

import mu.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

private val log = KotlinLogging.logger {}

@Controller
class LandingController {
    @GetMapping("/")
    fun landing(): String {
        log.debug { "Rendering landing page" }
        return "index"
    }

    @GetMapping("/about")
    fun about(): String {
        log.debug { "Rendering about page" }
        return "about"
    }
}
