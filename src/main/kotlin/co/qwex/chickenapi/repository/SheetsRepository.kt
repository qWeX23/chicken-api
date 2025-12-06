package co.qwex.chickenapi.repository

/**
 * A unified interface for Google Sheets-backed repositories.
 *
 * This interface defines the basic operations commonly used when
 * interacting with Google Sheets as a data store. Implementations
 * can override specific methods based on their needs.
 *
 * @param T the entity type managed by this repository
 * @param ID the type of the entity's identifier
 */
interface SheetsRepository<T, ID> {
    /**
     * Retrieves all entities from the sheet.
     *
     * @return a list of all entities, or an empty list if none exist
     */
    fun findAll(): List<T>

    /**
     * Retrieves an entity by its identifier.
     *
     * @param id the identifier of the entity to retrieve
     * @return the entity if found, or null if not found
     */
    fun findById(id: ID): T?

    /**
     * Appends a new entity to the sheet.
     *
     * @param entity the entity to append
     */
    fun append(entity: T)
}
