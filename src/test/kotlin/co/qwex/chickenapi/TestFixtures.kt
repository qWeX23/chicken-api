package co.qwex.chickenapi

import co.qwex.chickenapi.model.Breed

object TestFixtures {
    val silkie = Breed(1, "Silkie", "China", "White", "Small", "Docile", "Fluffy", "img", 200)
    val orpington = Breed(2, "Orpington", "UK", "Brown", "Large", "Friendly", "Big", "img2", 180)

    fun breedList() = listOf(silkie, orpington)
}
