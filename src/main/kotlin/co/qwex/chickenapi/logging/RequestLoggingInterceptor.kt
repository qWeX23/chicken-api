package co.qwex.chickenapi.logging

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val REQUEST_START_TIME_ATTRIBUTE = "co.qwex.chickenapi.logging.REQUEST_START_TIME"
private val logger = KotlinLogging.logger {}

@Component
class RequestLoggingInterceptor(
    private val requestLoggingService: RequestLoggingService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute(REQUEST_START_TIME_ATTRIBUTE, System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val startTime = request.getAttribute(REQUEST_START_TIME_ATTRIBUTE) as? Long ?: return
        val durationMs = System.currentTimeMillis() - startTime
        val logEntry = RequestLogEntry(
            timestamp = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            method = request.method,
            path = buildFullPath(request),
            status = response.status,
            durationMs = durationMs,
            clientIp = extractClientIp(request),
            userAgent = request.getHeader("User-Agent"),
        )

        requestLoggingService.recordRequest(logEntry)
    }

    private fun buildFullPath(request: HttpServletRequest): String {
        val query = request.queryString
        return if (query.isNullOrBlank()) {
            request.requestURI
        } else {
            "${'$'}{request.requestURI}?${'$'}query"
        }
    }

    private fun extractClientIp(request: HttpServletRequest): String? {
        val header = request.getHeader("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim()
        return header?.takeIf { it.isNotBlank() } ?: request.remoteAddr
    }
}
