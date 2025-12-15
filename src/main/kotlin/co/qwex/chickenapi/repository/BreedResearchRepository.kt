package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.BreedResearchRecord

interface BreedResearchRepository : DataRepository<BreedResearchRecord, String> {
    /**
     * Fetches the latest research record for a specific breed.
     *
     * @param breedId the ID of the breed to fetch research for
     * @return the most recent research record for this breed, or null if none exist
     */
    fun fetchLatestResearchForBreed(breedId: Int): BreedResearchRecord?

    /**
     * Fetches all successful research records.
     *
     * @return a list of all successful research records, sorted by completion time descending
     */
    fun fetchAllSuccessfulResearch(): List<BreedResearchRecord>

    override fun findAll(): List<BreedResearchRecord> = fetchAllSuccessfulResearch()

    override fun findById(id: String): BreedResearchRecord? {
        throw UnsupportedOperationException("findById not supported for BreedResearchRepository")
    }
}
