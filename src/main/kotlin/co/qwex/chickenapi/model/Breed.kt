package co.qwex.chickenapi.model

import java.time.Instant

data class Breed(
    val id: Int,
    val name: String,
    val origin: String?,
    val eggColor: String?,
    val eggSize: String?,
    val temperament: String?,
    val description: String?,
    val imageUrl: String?,
    val numEggs: Int?,
    val updatedAt: Instant? = null,
    val sources: List<String>? = null,
)
