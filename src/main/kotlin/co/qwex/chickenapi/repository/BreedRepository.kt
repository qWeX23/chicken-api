package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.Breed

interface BreedRepository {
    fun getAllBreeds(): List<Breed>
}
