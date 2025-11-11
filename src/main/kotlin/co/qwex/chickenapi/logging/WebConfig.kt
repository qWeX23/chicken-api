package co.qwex.chickenapi.logging

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web MVC configuration for request logging.
 * Registers the RequestLoggingInterceptor to capture request metadata.
 */
@Configuration
class WebConfig(
    private val requestLoggingInterceptor: RequestLoggingInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(requestLoggingInterceptor)
    }
}
