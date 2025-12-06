package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.Breed

interface BreedRepository : DataRepository<Breed, Int> {
    fun getAllBreeds(): List<Breed>

    fun getBreedById(id: Int): Breed?

    override fun findAll(): List<Breed> = getAllBreeds()

    override fun findById(id: Int): Breed? = getBreedById(id)

    override fun create(entity: Breed) {
        throw UnsupportedOperationException("Create not supported for BreedRepository")
    }
}
