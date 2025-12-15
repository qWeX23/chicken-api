package co.qwex.chickenapi.ai.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.repository.BreedRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger {}
private val json = Json { prettyPrint = true }

/**
 * Tool for selecting the next breed to research based on updatedAt timestamp.
 * Prioritizes breeds that have never been updated (null updatedAt), selecting randomly among them.
 * Otherwise selects the breed with the oldest updatedAt timestamp.
 */
class GetNextBreedToResearchTool(
    private val breedRepository: BreedRepository,
) : SimpleTool<GetNextBreedToResearchTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("Set to true to get the next breed that needs research")
        val fetch: Boolean = true,
    )

    @Serializable
    data class Result(
        val breedId: Int,
        val breedName: String,
        val lastUpdated: String?,
        val reason: String,
    )

    override val argsSerializer = Args.serializer()
    override val name = "get_next_breed_to_research"
    override val description = """
        Retrieves the next chicken breed that needs research.
        Prioritizes breeds that have never been researched (null updatedAt).
        If all breeds have been researched, selects the one with the oldest update timestamp.
        Returns the breed ID, name, last update time, and selection reason.
    """.trimIndent()

    override suspend fun doExecute(args: Args): String {
        log.info { "Fetching next breed to research" }

        val allBreeds = breedRepository.getAllBreeds()
        if (allBreeds.isEmpty()) {
            log.warn { "No breeds found in repository" }
            return json.encodeToString(Result.serializer(), Result(
                breedId = -1,
                breedName = "NO_BREEDS_FOUND",
                lastUpdated = null,
                reason = "no_breeds_available"
            ))
        }

        // Find breeds with null updatedAt (never researched)
        val neverUpdatedBreeds = allBreeds.filter { it.updatedAt == null }

        val selectedBreed: Breed
        val reason: String

        if (neverUpdatedBreeds.isNotEmpty()) {
            // Randomly select from breeds that have never been updated
            selectedBreed = neverUpdatedBreeds.random()
            reason = "never_updated"
            log.info { "Selected breed '${selectedBreed.name}' (ID: ${selectedBreed.id}) - never been researched" }
        } else {
            // All breeds have been updated, select the oldest
            selectedBreed = allBreeds.minByOrNull { it.updatedAt!! }!!
            reason = "oldest_update"
            log.info { "Selected breed '${selectedBreed.name}' (ID: ${selectedBreed.id}) - oldest update: ${selectedBreed.updatedAt}" }
        }

        val result = Result(
            breedId = selectedBreed.id,
            breedName = selectedBreed.name,
            lastUpdated = selectedBreed.updatedAt?.toString(),
            reason = reason,
        )

        return json.encodeToString(Result.serializer(), result)
    }
}

/**
 * Tool for fetching the current details of a specific breed.
 */
class GetBreedDetailsTool(
    private val breedRepository: BreedRepository,
) : SimpleTool<GetBreedDetailsTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("The ID of the breed to fetch details for")
        val breedId: Int,
    )

    @Serializable
    data class Result(
        val id: Int,
        val name: String,
        val origin: String?,
        val eggColor: String?,
        val eggSize: String?,
        val temperament: String?,
        val description: String?,
        val numEggs: Int?,
        val currentSources: List<String>,
    )

    override val argsSerializer = Args.serializer()
    override val name = "get_breed_details"
    override val description = """
        Fetches the current information for a specific chicken breed by ID.
        Returns all known details including origin, egg characteristics, temperament,
        description, and any existing source URLs.
        Use this to understand what information we already have before researching.
    """.trimIndent()

    override suspend fun doExecute(args: Args): String {
        log.info { "Fetching details for breed ID: ${args.breedId}" }

        val breed = breedRepository.getBreedById(args.breedId)
        if (breed == null) {
            log.warn { "Breed not found with ID: ${args.breedId}" }
            return """{"error": "Breed not found with ID ${args.breedId}"}"""
        }

        val result = Result(
            id = breed.id,
            name = breed.name,
            origin = breed.origin,
            eggColor = breed.eggColor,
            eggSize = breed.eggSize,
            temperament = breed.temperament,
            description = breed.description,
            numEggs = breed.numEggs,
            currentSources = breed.sources ?: emptyList(),
        )

        log.info { "Retrieved details for breed '${breed.name}'" }
        return json.encodeToString(Result.serializer(), result)
    }
}

/**
 * Tool for saving the complete research findings for a breed.
 * This is the final tool that should be called to save research results.
 * Saves directly to the breed repository.
 */
class SaveBreedResearchTool(
    private val breedRepository: BreedRepository,
) : SimpleTool<SaveBreedResearchTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("The breed ID being researched")
        val breedId: Int,

        @property:LLMDescription(
            "A compelling 2-3 sentence description of what makes this breed unique and special. " +
                "Should highlight the breed's most distinctive traits, personality, and appeal to chicken keepers. " +
                "Write in an engaging style that would help someone decide if this breed is right for them. " +
                "Do NOT include URLs or citations in the description - put those in the sources field instead.",
        )
        val description: String,

        @property:LLMDescription("Updated/verified origin (country or region). Use null if unable to verify.")
        val origin: String? = null,

        @property:LLMDescription("Updated/verified egg color (e.g., 'Brown', 'White', 'Blue', 'Green', 'Cream'). Use null if unable to verify.")
        val eggColor: String? = null,

        @property:LLMDescription("Updated/verified egg size: Small, Medium, Large, or Extra-Large. Use null if unable to verify.")
        val eggSize: String? = null,

        @property:LLMDescription("Brief temperament description (e.g., 'Docile and friendly', 'Active and flighty', 'Calm and broody'). Use null if unable to verify.")
        val temperament: String? = null,

        @property:LLMDescription("Updated/verified average annual egg production number (e.g., 250 for good layers, 150 for moderate). Use null if unable to verify.")
        val numEggs: Int? = null,

        @property:LLMDescription("List of source URLs used to verify the information. Include at least one authoritative source.")
        val sources: List<String>,
    )

    @Serializable
    data class Result(
        val success: Boolean,
        val breedId: Int,
        val breedName: String,
        val fieldsUpdated: List<String>,
        val error: String? = null,
        // The actual data that was saved (null on failure)
        val savedData: SavedBreedData? = null,
    )

    @Serializable
    data class SavedBreedData(
        val description: String,
        val origin: String?,
        val eggColor: String?,
        val eggSize: String?,
        val temperament: String?,
        val numEggs: Int?,
        val sources: List<String>,
    )

    override val argsSerializer = Args.serializer()
    override val name = "save_breed_research"
    override val description = """
        Saves the research findings for a chicken breed to the database.
        Call this tool after researching the breed with web_search and web_fetch.

        Required:
        - description: A compelling 2-3 sentence summary of what makes this breed unique
        - sources: At least one URL you used for research

        Optional (provide if you found verified information):
        - origin: Country or region of origin
        - eggColor: Color of eggs (Brown, White, Blue, etc.)
        - eggSize: Small, Medium, Large, or Extra-Large
        - temperament: Brief personality description
        - numEggs: Average annual egg production
    """.trimIndent()

    override suspend fun doExecute(args: Args): String {
        log.info { "Saving breed research for breed ID: ${args.breedId}" }
        log.info { "Description length: ${args.description.length} chars, Sources: ${args.sources.size}" }

        val existingBreed = breedRepository.getBreedById(args.breedId)
        if (existingBreed == null) {
            log.error { "Breed not found with ID: ${args.breedId}" }
            return json.encodeToString(
                Result.serializer(),
                Result(
                    success = false,
                    breedId = args.breedId,
                    breedName = "UNKNOWN",
                    fieldsUpdated = emptyList(),
                    error = "Breed not found with ID ${args.breedId}",
                ),
            )
        }

        // Determine which fields are being updated
        val fieldsUpdated = mutableListOf<String>()
        if (args.description != existingBreed.description) fieldsUpdated.add("description")
        if (args.origin != null && args.origin != existingBreed.origin) fieldsUpdated.add("origin")
        if (args.eggColor != null && args.eggColor != existingBreed.eggColor) fieldsUpdated.add("eggColor")
        if (args.eggSize != null && args.eggSize != existingBreed.eggSize) fieldsUpdated.add("eggSize")
        if (args.temperament != null && args.temperament != existingBreed.temperament) fieldsUpdated.add("temperament")
        if (args.numEggs != null && args.numEggs != existingBreed.numEggs) fieldsUpdated.add("numEggs")
        if (args.sources.isNotEmpty()) fieldsUpdated.add("sources")

        // Build updated breed
        val updatedBreed = Breed(
            id = existingBreed.id,
            name = existingBreed.name,
            origin = args.origin ?: existingBreed.origin,
            eggColor = args.eggColor ?: existingBreed.eggColor,
            eggSize = args.eggSize ?: existingBreed.eggSize,
            temperament = args.temperament ?: existingBreed.temperament,
            description = args.description, // Always use new description
            imageUrl = existingBreed.imageUrl,
            numEggs = args.numEggs ?: existingBreed.numEggs,
            updatedAt = null, // Will be set by repository
            sources = args.sources.ifEmpty { existingBreed.sources },
        )

        return try {
            breedRepository.update(updatedBreed)
            log.info { "Successfully updated breed '${existingBreed.name}' with fields: $fieldsUpdated" }
            json.encodeToString(
                Result.serializer(),
                Result(
                    success = true,
                    breedId = args.breedId,
                    breedName = existingBreed.name,
                    fieldsUpdated = fieldsUpdated,
                    savedData = SavedBreedData(
                        description = updatedBreed.description ?: "",
                        origin = updatedBreed.origin,
                        eggColor = updatedBreed.eggColor,
                        eggSize = updatedBreed.eggSize,
                        temperament = updatedBreed.temperament,
                        numEggs = updatedBreed.numEggs,
                        sources = updatedBreed.sources ?: emptyList(),
                    ),
                ),
            )
        } catch (ex: Exception) {
            log.error(ex) { "Failed to update breed ${args.breedId}" }
            json.encodeToString(
                Result.serializer(),
                Result(
                    success = false,
                    breedId = args.breedId,
                    breedName = existingBreed.name,
                    fieldsUpdated = emptyList(),
                    error = "Failed to save: ${ex.message}",
                ),
            )
        }
    }
}
