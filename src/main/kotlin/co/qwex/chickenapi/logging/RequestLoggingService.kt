package co.qwex.chickenapi.logging

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/**
 * Service that asynchronously records request logs to Google Sheets.
 * Each request is appended as a new row with timestamp, method, path, status, duration,
 * client IP, user agent, and request ID.
 */
@Service
open class RequestLoggingService(
    private val sheets: Sheets,
    @Value("\${google.sheets.db.spreadsheetId}") private val spreadsheetId: String,
    @Value("\${google.sheets.db.requestLogSheetName:$DEFAULT_REQUEST_LOG_SHEET}") private val sheetName: String,
) {
    @Async
    open fun recordRequest(entry: RequestLogEntry) {
        try {
            val valueRange = ValueRange().setValues(
                listOf(
                    listOf(
                        entry.timestamp,
                        entry.method,
                        entry.path,
                        entry.status,
                        entry.durationMs,
                        entry.clientIp,
                        entry.userAgent,
                        entry.requestId,
                    ),
                ),
            )

            sheets.spreadsheets().values()
                .append(spreadsheetId, "'$sheetName'!$SHEET_RANGE", valueRange)
                .setValueInputOption("USER_ENTERED")
                .execute()
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to append request log entry" }
        }
    }

    companion object {
        private const val DEFAULT_REQUEST_LOG_SHEET = "request_logs"
        private const val SHEET_RANGE = "A1:H1"
    }
}

/**
 * Data class representing a single request log entry.
 */
data class RequestLogEntry(
    val timestamp: String,
    val method: String,
    val path: String,
    val status: Int,
    val durationMs: Long,
    val clientIp: String?,
    val userAgent: String?,
    val requestId: String?,
)
