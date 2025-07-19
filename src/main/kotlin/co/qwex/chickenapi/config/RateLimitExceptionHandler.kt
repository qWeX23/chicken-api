package co.qwex.chickenapi.config

import com.giffing.bucket4j.spring.boot.starter.exception.Bucket4jGeneralException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RateLimitExceptionHandler {
    @ExceptionHandler(Bucket4jGeneralException::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun handleRateLimit(): Map<String, String> =
        mapOf("message" to "Too many requests")
}
