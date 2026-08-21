package co.qwex.chickenapi.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduledTaskMetricsTests {
    @Test
    fun `records task state success and overlap`() {
        val registry = SimpleMeterRegistry()
        val monitor = ScheduledTaskMetrics(registry).register("test-task", Duration.ofMinutes(30))
        val startedAt = Instant.ofEpochSecond(100)
        val completedAt = Instant.ofEpochSecond(130)

        monitor.setConfiguration(isEnabled = true, isReady = true)
        assertTrue(monitor.tryStart(startedAt))
        assertFalse(monitor.tryStart(startedAt.plusSeconds(1)))
        monitor.complete(ScheduledTaskResult.SUCCESS, startedAt, completedAt)

        assertEquals(1.0, gauge(registry, "chicken.api.scheduled.task.enabled"))
        assertEquals(0.0, gauge(registry, "chicken.api.scheduled.task.in.progress"))
        assertEquals(130.0, gauge(registry, "chicken.api.scheduled.task.last.success.timestamp.seconds"))
        assertEquals(30.0, gauge(registry, "chicken.api.scheduled.task.last.duration.seconds"))
        assertEquals(
            1.0,
            registry.get("chicken.api.scheduled.task.runs")
                .tags("task", "test-task", "result", "overlap")
                .counter()
                .count(),
        )
    }

    private fun gauge(registry: SimpleMeterRegistry, name: String): Double =
        registry.get(name).tag("task", "test-task").gauge().value()
}
