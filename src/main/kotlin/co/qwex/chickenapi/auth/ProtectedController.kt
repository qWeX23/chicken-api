package co.qwex.chickenapi.auth

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/protected")
class ProtectedController {
    @GetMapping
    fun user(@AuthenticationPrincipal jwt: Jwt): Map<String, Any?> {
        return mapOf(
            "user_id" to jwt.subject,
            "email" to jwt.claims["email"]
        )
    }
}
