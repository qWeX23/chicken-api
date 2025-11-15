package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogChickenFactsAgent
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Service for managing chicken facts operations.
 * Encapsulates the business logic for fetching chicken facts from the cloud agent.
 */
@Service
class ChickenFactsService(
    private val koogChickenFactsAgent: KoogChickenFactsAgent,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Fetches chicken facts from the cloud agent.
     * Returns null if the agent is not ready or if no facts are available.
     * @throws Exception if the cloud agent encounters an error during fact retrieval
     */
    suspend fun fetchChickenFacts(): String? {
        log.info { "Fetching chicken facts from cloud agent" }

        if (!koogChickenFactsAgent.isReady()) {
            log.warn { "Cloud agent is not ready to process requests" }
            return null
        }

        val facts = koogChickenFactsAgent.fetchChickenFacts()

        if (facts.isNullOrBlank()) {
            log.warn { "Cloud agent returned no facts" }
            return null
        }

        log.info { "Successfully fetched chicken facts from cloud agent" }
        return facts
    }

    /**
     * Checks if the cloud agent is ready to process requests.
     */
    fun isAgentReady(): Boolean = koogChickenFactsAgent.isReady()
}
