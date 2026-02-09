package co.qwex.chickenapi.repository.sheets

import co.qwex.chickenapi.model.Chicken

object ChickensTable : SheetTable<Chicken> {
    override val name: String = "chickens"
    override val columns: SheetColumns = SheetColumns(min = "A", max = "E")
    override val mapper: RowMapper<Chicken> = ChickenRowMapper()

    override fun headerRow(): List<Any?> = listOf(
        "id",
        "breedId",
        "name",
        "imageUrl",
        "updatedAt",
    )
}
