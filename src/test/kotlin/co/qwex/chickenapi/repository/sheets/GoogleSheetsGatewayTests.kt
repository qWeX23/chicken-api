package co.qwex.chickenapi.repository.sheets

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.services.sheets.v4.model.ValueRange
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GoogleSheetsGatewayTests {
    @Test
    fun `ensureTableExists creates sheet and headers when missing`() {
        val client = TestSheetsClient()
        val gateway = GoogleSheetsGateway(client, "sheet-id")

        gateway.ensureTableExists(BreedsTable)

        assertEquals(setOf("breeds"), client.sheetTitles)
        assertEquals(listOf(BreedsTable.headerRow()), client.updatedHeaders["'breeds'!A1:K1"])
    }

    @Test
    fun `ensureTableExists skips creation when sheet already present`() {
        val client = TestSheetsClient(sheetTitles = mutableSetOf("breeds"))
        val gateway = GoogleSheetsGateway(client, "sheet-id")

        gateway.ensureTableExists(BreedsTable)

        assertTrue(client.addedSheets.isEmpty())
        assertTrue(client.updatedHeaders.isEmpty())
    }

    @Test
    fun `ensureTableExists tolerates already exists error`() {
        val client = TestSheetsClient(throwAlreadyExists = true)
        val gateway = GoogleSheetsGateway(client, "sheet-id")

        gateway.ensureTableExists(BreedsTable)

        assertTrue(client.updatedHeaders.isEmpty())
    }

    @Test
    fun `ensureTableExists rethrows unexpected errors`() {
        val client = TestSheetsClient(throwUnexpected = true)
        val gateway = GoogleSheetsGateway(client, "sheet-id")

        assertFailsWith<RuntimeException> {
            gateway.ensureTableExists(BreedsTable)
        }
    }

    private class TestSheetsClient(
        val sheetTitles: MutableSet<String> = mutableSetOf(),
        private val throwAlreadyExists: Boolean = false,
        private val throwUnexpected: Boolean = false,
    ) : SheetsClient {
        val addedSheets = mutableListOf<String>()
        val updatedHeaders = mutableMapOf<String, List<List<Any?>>>()

        override fun listSheetTitles(spreadsheetId: String): Set<String> = sheetTitles

        override fun addSheet(spreadsheetId: String, sheetName: String) {
            if (throwUnexpected) {
                throw RuntimeException("boom")
            }
            if (throwAlreadyExists) {
                throw alreadyExistsException()
            }
            sheetTitles.add(sheetName)
            addedSheets.add(sheetName)
        }

        override fun getValues(spreadsheetId: String, a1Range: String, renderOption: String): List<List<Any?>> =
            emptyList()

        override fun appendValues(spreadsheetId: String, a1Range: String, valueRange: ValueRange, inputOption: String) {
        }

        override fun updateValues(spreadsheetId: String, a1Range: String, valueRange: ValueRange, inputOption: String) {
            updatedHeaders[a1Range] = valueRange.getValues().map { it.toList() }
        }

        private fun alreadyExistsException(): GoogleJsonResponseException {
            val builder = HttpResponseException.Builder(400, "Bad Request", HttpHeaders())
                .setMessage("Already exists")
            val error = GoogleJsonError()
            error.message = "Already exists"
            return GoogleJsonResponseException(builder, error)
        }
    }
}
