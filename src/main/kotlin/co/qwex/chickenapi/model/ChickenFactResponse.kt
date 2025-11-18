package co.qwex.chickenapi.model

import kotlinx.serialization.Serializable

@Serializable
data class ChickenFactResponse(
    val fact: String,
    val sourceUrl: String,
)
