package co.qwex.chickenapi.repository.sheets

fun interface RowMapper<T> {
    fun map(row: List<Any?>): T?
}
