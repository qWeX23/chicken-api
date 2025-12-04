package co.qwex.chickenapi.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Filter that generates and tracks request IDs for distributed tracing.
 * Sets MDC context for structured logging and adds X-Request-ID header to responses.
 */
@Component
class RequestLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER) ?: UUID.randomUUID().toString()
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId)
        MDC.put(REQUEST_ID_MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        val start = System.currentTimeMillis()
        log.info { "Incoming ${request.method} ${request.requestURI}" }
        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - start
            log.info { "Completed ${request.method} ${request.requestURI} ${response.status} in ${duration}ms" }
            MDC.remove(REQUEST_ID_MDC_KEY)
        }
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-ID"
        const val REQUEST_ID_MDC_KEY = "requestId"
        const val REQUEST_ID_ATTRIBUTE = "co.qwex.chickenapi.logging.REQUEST_ID"
    }
}
