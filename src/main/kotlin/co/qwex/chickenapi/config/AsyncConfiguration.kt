package co.qwex.chickenapi.config

import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class AsyncConfiguration {
    private val log = KotlinLogging.logger {}

    @Bean("requestLoggingExecutor")
    fun requestLoggingExecutor(meterRegistry: MeterRegistry): ThreadPoolTaskExecutor {
        val droppedCounter = meterRegistry.counter("chicken.api.request.log.dropped")
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 2
            queueCapacity = 500
            setThreadNamePrefix("request-log-")
            setWaitForTasksToCompleteOnShutdown(false)
            setRejectedExecutionHandler { _, _ ->
                droppedCounter.increment()
                log.warn { "Dropping request log because the bounded executor is full" }
            }
        }
    }
}
