package co.qwex.chickenapi.model

import java.time.Instant

data class Chicken(
    val id: Int,
    val name: String,
    val breedId: Int,
    val imageUrl: String,
    val updatedAt: Instant? = null,
)
