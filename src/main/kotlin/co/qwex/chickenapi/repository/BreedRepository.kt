package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.Breed

interface BreedRepository : DataRepository<Breed, Int> {
    fun getAllBreeds(): List<Breed>

    fun getBreedById(id: Int): Breed?

    override fun findAll(): List<Breed> = getAllBreeds()

    override fun findById(id: Int): Breed? = getBreedById(id)

    override fun save(entity: Breed) {
        throw UnsupportedOperationException("Save not supported for BreedRepository")
    }
}
