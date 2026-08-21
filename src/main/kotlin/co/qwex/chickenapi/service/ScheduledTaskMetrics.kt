package co.qwex.chickenapi.service

import co.qwex.chickenapi.model.AgentRunOutcome
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ScheduledTaskResult(val metricValue: String) {
    SUCCESS("success"),
    NO_OUTPUT("no_output"),
    FAILURE("failure"),
    TIMEOUT("timeout"),
    NOT_READY("not_ready"),
    OVERLAP("overlap"),
    PERSISTENCE_FAILURE("persistence_failure"),
}

@Component
class ScheduledTaskMetrics(
    private val meterRegistry: MeterRegistry,
) {
    private val tasks = ConcurrentHashMap<String, ScheduledTaskMonitor>()

    fun register(task: String, timeout: Duration): ScheduledTaskMonitor =
        tasks.computeIfAbsent(task) {
            ScheduledTaskMonitor(task, timeout, meterRegistry)
        }
}

class ScheduledTaskMonitor internal constructor(
    private val task: String,
    timeout: Duration,
    meterRegistry: MeterRegistry,
) {
    private val enabled = AtomicInteger()
    private val ready = AtomicInteger()
    private val historyLoaded = AtomicInteger()
    private val inProgress = AtomicInteger()
    private val hasCompletion = AtomicInteger()
    private val lastRunSuccess = AtomicInteger()
    private val lastAttemptEpochSeconds = AtomicLong()
    private val lastCompletionEpochSeconds = AtomicLong()
    private val lastSuccessEpochSeconds = AtomicLong()
    private val lastDurationMillis = AtomicLong()
    private val timeoutSeconds = AtomicLong(timeout.seconds)
    private val lastResults = ScheduledTaskResult.entries.associateWith { AtomicInteger() }
    private val runCounters = ScheduledTaskResult.entries.associateWith { result ->
        Counter.builder("chicken.api.scheduled.task.runs")
            .description("Scheduled task executions by result")
            .tag("task", task)
            .tag("result", result.metricValue)
            .register(meterRegistry)
    }

    init {
        gauge(meterRegistry, "chicken.api.scheduled.task.enabled", enabled, "Whether the scheduled task is enabled")
        gauge(meterRegistry, "chicken.api.scheduled.task.ready", ready, "Whether the scheduled task dependencies initialized")
        gauge(meterRegistry, "chicken.api.scheduled.task.history.loaded", historyLoaded, "Whether persisted task history was restored")
        gauge(meterRegistry, "chicken.api.scheduled.task.in.progress", inProgress, "Whether the scheduled task is running")
        gauge(meterRegistry, "chicken.api.scheduled.task.has.completion", hasCompletion, "Whether a completed task result is known")
        gauge(meterRegistry, "chicken.api.scheduled.task.last.run.success", lastRunSuccess, "Whether the last completed task succeeded")
        gauge(meterRegistry, "chicken.api.scheduled.task.last.attempt.timestamp.seconds", lastAttemptEpochSeconds, "Unix timestamp of the last task attempt")
        gauge(meterRegistry, "chicken.api.scheduled.task.last.completion.timestamp.seconds", lastCompletionEpochSeconds, "Unix timestamp of the last task completion")
        gauge(meterRegistry, "chicken.api.scheduled.task.last.success.timestamp.seconds", lastSuccessEpochSeconds, "Unix timestamp of the last successful task completion")
        Gauge.builder("chicken.api.scheduled.task.last.duration.seconds", lastDurationMillis) { value -> value.get() / 1_000.0 }
            .description("Duration of the last completed task in seconds")
            .tag("task", task)
            .register(meterRegistry)
        gauge(meterRegistry, "chicken.api.scheduled.task.timeout.seconds", timeoutSeconds, "Configured task timeout in seconds")
        lastResults.forEach { (result, value) ->
            Gauge.builder("chicken.api.scheduled.task.last.result", value) { it.get().toDouble() }
                .description("One-hot result of the last completed task")
                .tag("task", task)
                .tag("result", result.metricValue)
                .register(meterRegistry)
        }
    }

    fun setConfiguration(isEnabled: Boolean, isReady: Boolean) {
        enabled.set(isEnabled.toMetricValue())
        ready.set(isReady.toMetricValue())
    }

    fun setHistoryLoaded(loaded: Boolean) {
        historyLoaded.set(loaded.toMetricValue())
    }

    fun restore(
        latestRunStartedAt: Instant?,
        latestRunCompletedAt: Instant?,
        latestRunDurationMillis: Long?,
        latestRunOutcome: AgentRunOutcome?,
        latestSuccessAt: Instant?,
    ) {
        latestRunStartedAt?.let { lastAttemptEpochSeconds.set(it.epochSecond) }
        latestRunCompletedAt?.let {
            lastCompletionEpochSeconds.set(it.epochSecond)
            hasCompletion.set(1)
        }
        latestRunDurationMillis?.let(lastDurationMillis::set)
        latestSuccessAt?.let { lastSuccessEpochSeconds.set(it.epochSecond) }
        latestRunOutcome?.let { outcome ->
            val result = outcome.toScheduledTaskResult()
            lastRunSuccess.set((result == ScheduledTaskResult.SUCCESS).toMetricValue())
            setLastResult(result)
        }
    }

    fun tryStart(startedAt: Instant): Boolean {
        if (!inProgress.compareAndSet(0, 1)) {
            runCounters.getValue(ScheduledTaskResult.OVERLAP).increment()
            return false
        }
        lastAttemptEpochSeconds.set(startedAt.epochSecond)
        return true
    }

    fun recordSkipped(result: ScheduledTaskResult, at: Instant) {
        require(result == ScheduledTaskResult.NOT_READY) { "Only non-running task results can be skipped" }
        lastAttemptEpochSeconds.set(at.epochSecond)
        lastCompletionEpochSeconds.set(at.epochSecond)
        lastDurationMillis.set(0)
        hasCompletion.set(1)
        lastRunSuccess.set(0)
        setLastResult(result)
        runCounters.getValue(result).increment()
    }

    fun complete(result: ScheduledTaskResult, startedAt: Instant, completedAt: Instant) {
        lastCompletionEpochSeconds.set(completedAt.epochSecond)
        lastDurationMillis.set(Duration.between(startedAt, completedAt).toMillis().coerceAtLeast(0))
        hasCompletion.set(1)
        lastRunSuccess.set((result == ScheduledTaskResult.SUCCESS).toMetricValue())
        if (result == ScheduledTaskResult.SUCCESS) {
            lastSuccessEpochSeconds.set(completedAt.epochSecond)
        }
        setLastResult(result)
        runCounters.getValue(result).increment()
        inProgress.set(0)
    }

    @Synchronized
    private fun setLastResult(result: ScheduledTaskResult) {
        lastResults.values.forEach { it.set(0) }
        lastResults.getValue(result).set(1)
    }

    private fun gauge(
        meterRegistry: MeterRegistry,
        name: String,
        value: AtomicInteger,
        description: String,
    ) {
        Gauge.builder(name, value) { it.get().toDouble() }
            .description(description)
            .tag("task", task)
            .register(meterRegistry)
    }

    private fun gauge(
        meterRegistry: MeterRegistry,
        name: String,
        value: AtomicLong,
        description: String,
    ) {
        Gauge.builder(name, value) { it.get().toDouble() }
            .description(description)
            .tag("task", task)
            .register(meterRegistry)
    }
}

internal fun AgentRunOutcome.toScheduledTaskResult(): ScheduledTaskResult = when (this) {
    AgentRunOutcome.SUCCESS -> ScheduledTaskResult.SUCCESS
    AgentRunOutcome.NO_OUTPUT -> ScheduledTaskResult.NO_OUTPUT
    AgentRunOutcome.FAILED -> ScheduledTaskResult.FAILURE
    AgentRunOutcome.TIMEOUT -> ScheduledTaskResult.TIMEOUT
    AgentRunOutcome.NOT_READY -> ScheduledTaskResult.NOT_READY
}

private fun Boolean.toMetricValue(): Int = if (this) 1 else 0
