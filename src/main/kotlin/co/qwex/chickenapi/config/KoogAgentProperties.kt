package co.qwex.chickenapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

data class AgentSchedulerProperties(
    val enabled: Boolean = true,
    val cron: String = "0 15 4 * * *",
    val zone: String = "America/Chicago",
    val timeout: Duration = Duration.ofMinutes(30),
)

/**
 * Configuration holder for the Koog chicken facts agent.
 *
 * Values can be overridden via `application.properties` or environment variables.
 */
@ConfigurationProperties(prefix = "koog.agent")
data class KoogAgentProperties(
    val enabled: Boolean = true,
    val model: String = "gpt-oss:120b",
    val contextLength: Int = 8_192,
    val prompt: String = "Find an interesting, fun, or quirky fact about chickens. Look for trivia, surprising behaviors, historical tidbits, or amusing chicken stories rather than scientific research papers. Cite your sources. Format your response as a markdown. stay on topic about chickens. only return one fact.",
    val webSearchMaxResults: Int = 3,
    val maxAgentIterations: Int = 256,
    val retrySearchBudgetPerDuplicate: Int = 3,
    val excludedSearchDomains: Set<String> = setOf("wikipedia.org"),
    val preferredSearchDomains: Set<String> = emptySet(),
    val scheduler: AgentSchedulerProperties = AgentSchedulerProperties(),
)
