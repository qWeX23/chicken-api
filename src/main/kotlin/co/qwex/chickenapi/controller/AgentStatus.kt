package co.qwex.chickenapi.controller

/**
 * Shared DTO for agent status responses across all agent controllers.
 * Provides a consistent response format for checking agent readiness.
 */
data class AgentStatus(
    val ready: Boolean,
    val status: String,
    val message: String,
) {
    companion object {
        fun operational(agentName: String) = AgentStatus(
            ready = true,
            status = "operational",
            message = "$agentName is ready"
        )

        fun unavailable(agentName: String) = AgentStatus(
            ready = false,
            status = "unavailable",
            message = "$agentName is not configured. Check API key."
        )

        fun forAgent(agentName: String, isReady: Boolean) =
            if (isReady) operational(agentName) else unavailable(agentName)
    }
}
