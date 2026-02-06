package co.qwex.chickenapi.repository.sheets

interface SheetTable<T> {
    val name: String
    val columns: SheetColumns
    val mapper: RowMapper<T>

    fun headerRow(): List<Any?>

    fun headerRange(): SheetRange =
        SheetRange.row(name, columns.min, columns.max, columns.headerRow)

    fun dataRange(): SheetRange =
        SheetRange(sheet = name, minColumn = columns.min, maxColumn = columns.max, rowStart = columns.dataStartRow)

    fun appendRange(): SheetRange =
        SheetRange.columns(name, columns.min, columns.max)

    fun rowRange(row: Int): SheetRange =
        SheetRange.row(name, columns.min, columns.max, row)
}
