package co.qwex.chickenapi.repository.sheets

import co.qwex.chickenapi.model.ChickenFactsRecord

object ChickenFactsTable : SheetTable<ChickenFactsRecord> {
    override val name: String = "chicken_facts"
    override val columns: SheetColumns = SheetColumns(min = "A", max = "K")
    override val mapper: RowMapper<ChickenFactsRecord> = ChickenFactsRowMapper()

    override fun headerRow(): List<Any?> = listOf(
        "runId",
        "startedAt",
        "completedAt",
        "durationMillis",
        "outcome",
        "factLength",
        "fact",
        "sourceUrl",
        "factEmbedding",
        "errorMessage",
        "updatedAt",
    )
}
