package co.qwex.chickenapi.model

data class Breed(
    val id: Int,
    val name: String,
    val origin: String?,
    val eggColor: String?,
    val eggSize: String?,
    val temperament: String?,
    val description: String?,
    val imageUrl: String?,
)
