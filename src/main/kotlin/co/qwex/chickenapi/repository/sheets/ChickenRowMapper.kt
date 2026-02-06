package co.qwex.chickenapi.repository.sheets

import co.qwex.chickenapi.model.Chicken
import mu.KotlinLogging

class ChickenRowMapper : RowMapper<Chicken> {
    private val log = KotlinLogging.logger {}

    override fun map(row: List<Any?>): Chicken? {
        val idValue = row.stringAt(0)
        if (idValue.equals("id", ignoreCase = true)) {
            return null
        }

        val id = row.intAt(0)
        val breedId = row.intAt(1)
        val name = row.stringAt(2)
        if (id == null || breedId == null || name == null) {
            log.debug { "Skipping chicken row with missing required fields: $row" }
            return null
        }

        return Chicken(
            id = id,
            breedId = breedId,
            name = name,
            imageUrl = row.stringAt(3, trim = false).orEmpty(),
            updatedAt = row.instantAt(4),
        )
    }
}
