package co.qwex.chickenapi.controller.auth

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthenticatedController {
    @GetMapping("/hello")
    fun hello(): String = "hello authenticated world"
}
