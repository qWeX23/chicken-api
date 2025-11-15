package co.qwex.chickenapi.service

import co.qwex.chickenapi.ai.KoogChickenFactsAgent
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScheduledTaskService(
    private val koogChickenFactsAgent: KoogChickenFactsAgent,
) {

    private val log = KotlinLogging.logger {}

    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    fun performScheduledTask() {
        log.info { "Scheduled task started at: ${LocalDateTime.now()}" }
        if (!koogChickenFactsAgent.isReady()) {
            log.info { "Koog agent is not ready, skipping run." }
            return
        }

        runBlocking {
            val response = koogChickenFactsAgent.fetchChickenFacts()
            if (response.isNullOrBlank()) {
                log.warn { "Koog agent returned no chicken facts." }
            } else {
                log.info { "Koog agent output:\n$response" }
            }
        }
        log.info { "Scheduled task completed at: ${LocalDateTime.now()}" }
    }
}
