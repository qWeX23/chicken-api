package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.Chicken

interface ChickenRepository {
    fun getChickenById(id: Int): Chicken?
}
