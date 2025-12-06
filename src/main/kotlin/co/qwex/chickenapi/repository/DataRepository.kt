package co.qwex.chickenapi.repository

/**
 * A unified interface for data repositories.
 *
 * This interface defines the basic CRUD-like operations for data access,
 * abstracting away the underlying storage technology. Implementations can
 * use Google Sheets, SQL databases, NoSQL stores, or any other backend.
 *
 * @param T the entity type managed by this repository
 * @param ID the type of the entity's identifier
 */
interface DataRepository<T, ID> {
    /**
     * Retrieves all entities from the data store.
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
     * Creates a new entity in the data store.
     *
     * @param entity the entity to create
     */
    fun create(entity: T)

    /**
     * Updates an existing entity in the data store.
     *
     * @param entity the entity to update
     */
    fun update(entity: T) {
        throw UnsupportedOperationException("Update not supported for this repository")
    }
}
