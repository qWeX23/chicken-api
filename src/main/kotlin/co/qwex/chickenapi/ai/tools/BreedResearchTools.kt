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
 */
class SaveBreedResearchTool : SimpleTool<SaveBreedResearchTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("The breed ID being researched")
        val breedId: Int,

        @property:LLMDescription("Full research report (2-4 paragraphs) about what makes this breed unique, covering history, characteristics, temperament, and interesting facts")
        val report: String,

        @property:LLMDescription("Updated/verified origin (country or region). Use null if unable to verify.")
        val origin: String? = null,

        @property:LLMDescription("Updated/verified egg color. Use null if unable to verify.")
        val eggColor: String? = null,

        @property:LLMDescription("Updated/verified egg size (small, medium, large, extra-large). Use null if unable to verify.")
        val eggSize: String? = null,

        @property:LLMDescription("Updated/verified temperament description. Use null if unable to verify.")
        val temperament: String? = null,

        @property:LLMDescription("Updated/enriched description of the breed. Use null if unable to improve.")
        val description: String? = null,

        @property:LLMDescription("Updated/verified annual egg production number. Use null if unable to verify.")
        val numEggs: Int? = null,

        @property:LLMDescription("List of source URLs that were used to verify information. Must include at least one source.")
        val sources: List<String>,
    )

    override val argsSerializer = Args.serializer()
    override val name = "save_breed_research"
    override val description = """
        Saves the complete research findings for a chicken breed.
        This tool should be called once you have:
        1. Thoroughly researched the breed using web_search and web_fetch
        2. Written a comprehensive report (2-4 paragraphs) about what makes it unique
        3. Verified existing data points and found any corrections needed
        4. Collected source URLs for all facts

        The report should cover: history/origin, physical characteristics, temperament,
        egg production, and any unique or interesting facts about the breed.

        Include source URLs for every piece of information you report.
    """.trimIndent()

    override suspend fun doExecute(args: Args): String {
        log.info { "Saving breed research for breed ID: ${args.breedId}" }
        log.info { "Report length: ${args.report.length} chars, Sources: ${args.sources.size}" }

        // The actual saving/updating will happen in the scheduled task service
        // This tool just validates and returns the structured data
        return json.encodeToString(Args.serializer(), args)
    }
}
