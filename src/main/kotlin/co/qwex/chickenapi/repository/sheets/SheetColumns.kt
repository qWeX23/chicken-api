package co.qwex.chickenapi.repository.sheets

data class SheetColumns(
    val min: String,
    val max: String,
    val headerRow: Int = 1,
    val dataStartRow: Int = 2,
)
