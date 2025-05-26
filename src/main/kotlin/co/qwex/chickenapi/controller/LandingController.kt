package co.qwex.chickenapi.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class LandingController {
    @GetMapping("/")
    fun landing(): String {
        return "index"
    }
}
