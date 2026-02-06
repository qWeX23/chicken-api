package co.qwex.chickenapi.repository.sheets

import co.qwex.chickenapi.model.Breed

object BreedsTable : SheetTable<Breed> {
    override val name: String = "breeds"
    override val columns: SheetColumns = SheetColumns(min = "A", max = "K")
    override val mapper: RowMapper<Breed> = BreedRowMapper()

    override fun headerRow(): List<Any?> = listOf(
        "id",
        "name",
        "origin",
        "eggColor",
        "eggSize",
        "temperament",
        "description",
        "imageUrl",
        "numEggs",
        "updatedAt",
        "sources",
    )
}
