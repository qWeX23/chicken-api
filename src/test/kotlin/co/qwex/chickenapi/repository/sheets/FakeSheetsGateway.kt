package co.qwex.chickenapi.repository.sheets

class FakeSheetsGateway : SheetsGateway {
    private val sheets = mutableMapOf<String, MutableList<MutableList<Any?>>>()

    override fun getValues(range: SheetRange, renderOption: ValueRenderOption): List<List<Any?>> {
        val rows = sheets[range.sheet]?.map { it.toList() } ?: emptyList()
        if (range.rowStart == null) {
            return rows
        }
        val startIndex = (range.rowStart - 1).coerceAtLeast(0)
        val endIndex = range.rowEnd?.coerceAtMost(rows.size) ?: rows.size
        return if (startIndex >= rows.size) {
            emptyList()
        } else {
            rows.subList(startIndex, endIndex)
        }
    }

    override fun appendValues(range: SheetRange, rows: List<List<Any?>>, input: ValueInputOption) {
        val sheetRows = sheets.getOrPut(range.sheet) { mutableListOf() }
        rows.forEach { sheetRows.add(it.toMutableList()) }
    }

    override fun updateValues(range: SheetRange, rows: List<List<Any?>>, input: ValueInputOption) {
        val sheetRows = sheets.getOrPut(range.sheet) { mutableListOf() }
        val startIndex = (range.rowStart ?: 1) - 1
        rows.forEachIndexed { offset, row ->
            val targetIndex = startIndex + offset
            while (sheetRows.size <= targetIndex) {
                sheetRows.add(mutableListOf())
            }
            sheetRows[targetIndex] = row.toMutableList()
        }
    }

    override fun ensureTableExists(table: SheetTable<*>) {
        if (!sheets.containsKey(table.name)) {
            sheets[table.name] = mutableListOf(table.headerRow().toMutableList())
        }
    }

    fun seed(table: SheetTable<*>, rows: List<List<Any?>>) {
        ensureTableExists(table)
        appendValues(table.appendRange(), rows)
    }
}
