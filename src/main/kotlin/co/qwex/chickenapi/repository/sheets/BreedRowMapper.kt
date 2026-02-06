package co.qwex.chickenapi.repository.sheets

import co.qwex.chickenapi.model.Breed
import mu.KotlinLogging

private const val SOURCES_DELIMITER = "|"

class BreedRowMapper : RowMapper<Breed> {
    private val log = KotlinLogging.logger {}

    override fun map(row: List<Any?>): Breed? {
        val idValue = row.stringAt(0)
        if (idValue.equals("id", ignoreCase = true)) {
            return null
        }

        val id = row.intAt(0)
        if (id == null) {
            log.debug { "Skipping breed row with invalid id: $row" }
            return null
        }

        return Breed(
            id = id,
            name = row.stringAt(1).orEmpty(),
            origin = row.stringAt(2),
            eggColor = row.stringAt(3),
            eggSize = row.stringAt(4),
            temperament = row.stringAt(5),
            description = row.stringAt(6, trim = false),
            imageUrl = row.stringAt(7),
            numEggs = row.intAt(8),
            updatedAt = row.instantAt(9),
            sources = row.stringListAt(10, SOURCES_DELIMITER),
        )
    }
}
