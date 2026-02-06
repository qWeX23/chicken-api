package co.qwex.chickenapi.repository.sheets

enum class ValueRenderOption(val apiValue: String) {
    FORMATTED_VALUE("FORMATTED_VALUE"),
    UNFORMATTED_VALUE("UNFORMATTED_VALUE"),
    FORMULA("FORMULA"),
}

enum class ValueInputOption(val apiValue: String) {
    USER_ENTERED("USER_ENTERED"),
    RAW("RAW"),
}

interface SheetsGateway {
    fun getValues(
        range: SheetRange,
        renderOption: ValueRenderOption = ValueRenderOption.FORMATTED_VALUE,
    ): List<List<Any?>>

    fun appendValues(
        range: SheetRange,
        rows: List<List<Any?>>,
        input: ValueInputOption = ValueInputOption.USER_ENTERED,
    )

    fun updateValues(
        range: SheetRange,
        rows: List<List<Any?>>,
        input: ValueInputOption = ValueInputOption.USER_ENTERED,
    )

    fun ensureTableExists(table: SheetTable<*>)
}
