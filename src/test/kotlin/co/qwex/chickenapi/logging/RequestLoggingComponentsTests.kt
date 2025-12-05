package co.qwex.chickenapi.logging

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RequestLoggingComponentsTests {

    private val filter = RequestLoggingFilter()

    @Test
    fun `filter sets request attribute and response header`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        val generatedId = request.getAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE) as? String
        assertNotNull(generatedId)
        assertEquals(generatedId, response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
    }

    @Test
    fun `interceptor uses generated request id attribute`() {
        val requestLoggingService = CapturingRequestLoggingService()
        val interceptor = RequestLoggingInterceptor(requestLoggingService)

        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        // Run through filter to set request ID attribute
        val filterChain = MockFilterChain()
        filter.doFilter(request, response, filterChain)

        interceptor.preHandle(request, response, Any())
        interceptor.afterCompletion(request, response, Any(), null)

        val generatedId = request.getAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE) as? String
        assertNotNull(generatedId)

        assertEquals(generatedId, requestLoggingService.lastEntry?.requestId)
    }
}

private class CapturingRequestLoggingService : RequestLoggingService(
    sheets = Mockito.mock(com.google.api.services.sheets.v4.Sheets::class.java, Mockito.RETURNS_DEEP_STUBS),
    spreadsheetId = "test-sheet",
    sheetName = "request_logs",
) {
    var lastEntry: RequestLogEntry? = null

    override fun recordRequest(entry: RequestLogEntry) {
        lastEntry = entry
    }
}
