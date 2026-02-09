package co.qwex.chickenapi.repository.sheets

import co.qwex.chickenapi.model.BreedResearchRecord

object BreedResearchTable : SheetTable<BreedResearchRecord> {
    override val name: String = "breed_research_runs"
    override val columns: SheetColumns = SheetColumns(min = "A", max = "L")
    override val mapper: RowMapper<BreedResearchRecord> = BreedResearchRowMapper()

    override fun headerRow(): List<Any?> = listOf(
        "runId",
        "breedId",
        "breedName",
        "startedAt",
        "completedAt",
        "durationMillis",
        "outcome",
        "reportLength",
        "report",
        "sourcesFound",
        "fieldsUpdated",
        "errorMessage",
    )
}
