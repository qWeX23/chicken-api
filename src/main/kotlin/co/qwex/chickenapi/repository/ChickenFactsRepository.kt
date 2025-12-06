package co.qwex.chickenapi.repository

import co.qwex.chickenapi.model.ChickenFactsRecord

interface ChickenFactsRepository : SheetsRepository<ChickenFactsRecord, String> {
    /**
     * Fetches the latest successful chicken fact from the repository.
     *
     * @return the most recent successful chicken fact record, or null if none exist
     */
    fun fetchLatestChickenFact(): ChickenFactsRecord?

    /**
     * Fetches all successful chicken facts from the repository.
     *
     * @return a list of all successful chicken fact records, sorted by completion time descending
     */
    fun fetchAllSuccessfulChickenFacts(): List<ChickenFactsRecord>

    override fun findAll(): List<ChickenFactsRecord> = fetchAllSuccessfulChickenFacts()

    override fun findById(id: String): ChickenFactsRecord? {
        throw UnsupportedOperationException("findById not supported for ChickenFactsRepository")
    }
}
