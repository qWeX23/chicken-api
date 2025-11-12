package co.qwex.chickenapi.service

import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScheduledTaskService {

    private val log = KotlinLogging.logger {}

    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    fun performScheduledTask() {
        log.info { "Scheduled task started at: ${LocalDateTime.now()}" }
        // Add your scheduled task logic here
        log.info { "Scheduled task completed at: ${LocalDateTime.now()}" }
    }
}
