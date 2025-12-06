package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.Chicken

interface ChickenRepository : SheetsRepository<Chicken, Int> {
    fun getChickenById(id: Int): Chicken?

    override fun findById(id: Int): Chicken? = getChickenById(id)

    override fun findAll(): List<Chicken> {
        throw UnsupportedOperationException("findAll not supported for ChickenRepository")
    }

    override fun append(entity: Chicken) {
        throw UnsupportedOperationException("Append not supported for ChickenRepository")
    }
}
