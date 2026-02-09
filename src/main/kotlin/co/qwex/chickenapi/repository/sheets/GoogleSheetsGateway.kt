package co.qwex.chickenapi.repository.sheets

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

interface SheetsClient {
    fun listSheetTitles(spreadsheetId: String): Set<String>

    fun addSheet(spreadsheetId: String, sheetName: String)

    fun getValues(spreadsheetId: String, a1Range: String, renderOption: String): List<List<Any?>>

    fun appendValues(spreadsheetId: String, a1Range: String, valueRange: ValueRange, inputOption: String)

    fun updateValues(spreadsheetId: String, a1Range: String, valueRange: ValueRange, inputOption: String)
}

class GoogleSheetsClient(private val sheets: Sheets) : SheetsClient {
    override fun listSheetTitles(spreadsheetId: String): Set<String> {
        val spreadsheet = sheets.spreadsheets()
            .get(spreadsheetId)
            .setFields("sheets.properties.title")
            .execute()

        return spreadsheet
            .getSheets()
            ?.mapNotNull { it.properties?.title }
            ?.toSet()
            ?: emptySet()
    }

    override fun addSheet(spreadsheetId: String, sheetName: String) {
        val request = Request()
            .setAddSheet(AddSheetRequest().setProperties(SheetProperties().setTitle(sheetName)))

        sheets.spreadsheets()
            .batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(request)))
            .execute()
    }

    override fun getValues(spreadsheetId: String, a1Range: String, renderOption: String): List<List<Any?>> =
        sheets.spreadsheets()
            .values()
            .get(spreadsheetId, a1Range)
            .setValueRenderOption(renderOption)
            .execute()
            .getValues()
            ?.map { row -> row.toList() }
            ?: emptyList()

    override fun appendValues(spreadsheetId: String, a1Range: String, valueRange: ValueRange, inputOption: String) {
        sheets.spreadsheets()
            .values()
            .append(spreadsheetId, a1Range, valueRange)
            .setValueInputOption(inputOption)
            .execute()
    }

    override fun updateValues(spreadsheetId: String, a1Range: String, valueRange: ValueRange, inputOption: String) {
        sheets.spreadsheets()
            .values()
            .update(spreadsheetId, a1Range, valueRange)
            .setValueInputOption(inputOption)
            .execute()
    }
}

class GoogleSheetsGateway(
    private val client: SheetsClient,
    private val spreadsheetId: String,
) : SheetsGateway {
    private val log = KotlinLogging.logger {}
    private val knownSheets = ConcurrentHashMap.newKeySet<String>()

    override fun getValues(range: SheetRange, renderOption: ValueRenderOption): List<List<Any?>> =
        client.getValues(spreadsheetId, range.toA1Range(), renderOption.apiValue)

    override fun appendValues(range: SheetRange, rows: List<List<Any?>>, input: ValueInputOption) {
        val valueRange = ValueRange().setValues(rows)
        client.appendValues(spreadsheetId, range.toA1Range(), valueRange, input.apiValue)
    }

    override fun updateValues(range: SheetRange, rows: List<List<Any?>>, input: ValueInputOption) {
        val valueRange = ValueRange().setValues(rows)
        client.updateValues(spreadsheetId, range.toA1Range(), valueRange, input.apiValue)
    }

    override fun ensureTableExists(table: SheetTable<*>) {
        if (knownSheets.contains(table.name)) {
            return
        }

        if (!refreshKnownSheets().contains(table.name)) {
            createSheet(table)
        }

        knownSheets.add(table.name)
    }

    private fun refreshKnownSheets(): Set<String> {
        val titles = client.listSheetTitles(spreadsheetId)
        knownSheets.addAll(titles)
        return titles
    }

    private fun createSheet(table: SheetTable<*>) {
        try {
            client.addSheet(spreadsheetId, table.name)
        } catch (ex: GoogleJsonResponseException) {
            if (isAlreadyExistsError(ex)) {
                log.info { "Sheet ${table.name} already exists while creating." }
                return
            }
            log.warn(ex) { "Failed to create sheet ${table.name}." }
            throw ex
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to create sheet ${table.name}." }
            throw ex
        }

        updateValues(table.headerRange(), listOf(table.headerRow()))
        log.info { "Created sheet ${table.name} with headers." }
    }

    private fun isAlreadyExistsError(ex: GoogleJsonResponseException): Boolean {
        val detailsMessage = ex.details?.message?.lowercase().orEmpty()
        val exceptionMessage = ex.message?.lowercase().orEmpty()
        return "already exists" in detailsMessage || "already exists" in exceptionMessage
    }
}
