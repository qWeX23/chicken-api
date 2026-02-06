package co.qwex.chickenapi.repository.sheets

data class SheetRange(
    val sheet: String,
    val minColumn: String,
    val maxColumn: String,
    val rowStart: Int? = null,
    val rowEnd: Int? = null,
) {
    fun toA1Range(): String {
        val sheetPrefix = "${quoteSheetName(sheet)}!"
        return when {
            rowStart == null && rowEnd == null -> "$sheetPrefix$minColumn:$maxColumn"
            rowStart != null && rowEnd == null -> "$sheetPrefix$minColumn$rowStart:$maxColumn"
            rowStart != null && rowEnd != null -> "$sheetPrefix$minColumn$rowStart:$maxColumn$rowEnd"
            else -> "$sheetPrefix$minColumn:$maxColumn"
        }
    }

    override fun toString(): String = toA1Range()

    companion object {
        fun columns(sheet: String, min: String, max: String): SheetRange =
            SheetRange(sheet = sheet, minColumn = min, maxColumn = max)

        fun row(sheet: String, min: String, max: String, row: Int): SheetRange =
            SheetRange(sheet = sheet, minColumn = min, maxColumn = max, rowStart = row, rowEnd = row)

        fun headerRow(sheet: String, min: String, max: String): SheetRange =
            row(sheet, min, max, 1)
    }

    private fun quoteSheetName(sheet: String): String {
        val escaped = sheet.replace("'", "''")
        return "'$escaped'"
    }
}
