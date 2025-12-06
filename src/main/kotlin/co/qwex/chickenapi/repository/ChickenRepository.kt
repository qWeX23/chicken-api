package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.Chicken

interface ChickenRepository : DataRepository<Chicken, Int> {
    fun getChickenById(id: Int): Chicken?

    override fun findById(id: Int): Chicken? = getChickenById(id)

    override fun findAll(): List<Chicken> {
        throw UnsupportedOperationException("findAll not supported for ChickenRepository")
    }

    override fun save(entity: Chicken) {
        throw UnsupportedOperationException("Save not supported for ChickenRepository")
    }
}
