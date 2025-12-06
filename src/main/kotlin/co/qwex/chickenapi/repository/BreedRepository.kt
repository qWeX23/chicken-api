package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.Breed

interface BreedRepository : SheetsRepository<Breed, Int> {
    fun getAllBreeds(): List<Breed>

    fun getBreedById(id: Int): Breed?

    override fun findAll(): List<Breed> = getAllBreeds()

    override fun findById(id: Int): Breed? = getBreedById(id)

    override fun append(entity: Breed) {
        throw UnsupportedOperationException("Append not supported for BreedRepository")
    }
}
