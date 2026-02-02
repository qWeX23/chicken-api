# Investigation: Why Breed Research Has Not Run

**Date**: 2025-12-20
**Branch**: `claude/investigate-breed-research-uyq5H`
**Status**: Updated with new findings

## Summary

The breed research agent runs but produces **no actionable output** due to a bug in `BreedResearchStrategy`. The strategy does not capture the `save_breed_research` tool result, causing the scheduled task to fail when parsing the agent's response.

## Root Causes

### 1. BreedResearchStrategy Does Not Capture Tool Results (Primary Cause - BUG)

**Symptom**: Log shows `"Breed research agent returned no actionable output."`

**Root Cause**: The `BreedResearchStrategy` returns the LLM's final assistant message instead of the `save_breed_research` tool result JSON.

**Comparison with Working Implementation**:

`ChickenResearchStrategy.kt` (WORKS) has a `captureToolResult` node:
```kotlin
// Lines 48-56: Captures the tool result
val captureToolResult by node<ReceivedToolResult, ReceivedToolResult>("capture_tool_result") { toolResult ->
    val result = toolResult.result.toString()
    if (result.contains("\"fact\"") && result.contains("\"sourceUrl\"")) {
        savedFactJson = result  // <-- Stores the JSON
    }
    toolResult
}

// Lines 67-72: Returns the captured JSON
val returnResult by node<String, String>("return_result") { _ ->
    savedFactJson ?: "{}"  // <-- Returns saved tool result, NOT LLM message
}
```

`BreedResearchStrategy.kt` (BROKEN) is missing this:
```kotlin
// Lines 53-56: Just passes through the LLM's message
val returnResult by node<String, String>("return_result") { message ->
    log.info { "Agent finished with message: ${message.take(100)}..." }
    message  // <-- Returns LLM's text, NOT the tool result JSON!
}
```

**What Happens**:
1. Agent calls `save_breed_research` tool → tool saves to database and returns JSON
2. JSON result is sent to LLM via `sendToolResult`
3. LLM responds with text like "I've saved the research for the Silkie breed"
4. Strategy returns this text message (not the JSON)
5. `extractSaveResultJson()` fails to find JSON in the text
6. Outcome is set to `NO_OUTPUT`

**Fix Required**: Add a `captureToolResult` node to `BreedResearchStrategy` that intercepts and stores the `save_breed_research` JSON result, similar to `ChickenResearchStrategy`.

---

### 2. Missing `OLLAMA_API_KEY` or Client Credentials (Secondary - If Agent Not Ready)

**Evidence**:
- `application.properties:31`: `koog.agent.api-key=${OLLAMA_API_KEY:}`
- `application.properties:32`: `koog.agent.client-id=${OLLAMA_CLIENT_ID:}`
- `application.properties:33`: `koog.agent.client-secret=${OLLAMA_CLIENT_SECRET:}`
- The syntax `${VAR:}` means: use the environment variable, or default to empty string
- Empty string fails the `isNotBlank()` check

**Code Path**:
```
KoogBreedResearchAgent.kt:55-63:
  val apiKey = agentProperties.apiKey?.takeIf { it.isNotBlank() }
  val hasCloudflareCredentials = clientId != null && clientSecret != null
  if (apiKey == null && !hasCloudflareCredentials) {
      log.warn { "koog.agent.api-key or koog.agent.client-id/client-secret is not set; Breed research agent will be skipped." }
  }
```

When API key is missing:
1. `promptExecutor` is never initialized (remains `null`)
2. `isReady()` returns `false` (line 135: `fun isReady(): Boolean = promptExecutor != null`)
3. Scheduled task checks `isReady()` and exits early (lines 57-60)

**Expected Log Output**:
```
WARN  - koog.agent.api-key or koog.agent.client-id/client-secret is not set; Breed research agent will be skipped.
INFO  - Breed research agent is not ready, skipping run.
```

### 2. Feature Recently Deployed

The breed research agent was merged on **December 15, 2025** (commit `fe83cc2`). With only 5 days since implementation:
- Deployment may not have occurred yet
- Limited opportunities for the midnight scheduler to trigger

### 3. No Manual Trigger Available

The scheduler runs only at midnight UTC daily (`0 0 0 * * *`). There is currently no REST endpoint to manually trigger breed research for testing or immediate execution.

## Verification Checklist

- [x] `@EnableScheduling` is present (`ChickenApiApplication.kt:17`)
- [x] Agent is enabled (`koog.breed-research-agent.enabled=true`)
- [x] Cron expression is valid (`0 0 0 * * *` = midnight daily)
- [x] All required tools are registered in `ToolRegistry`
- [x] Google Sheets persistence is implemented
- [x] `OLLAMA_API_KEY` environment variable is set (agent runs)
- [x] LLM backend is accessible (agent executes)
- [ ] **BUG**: `BreedResearchStrategy` captures `save_breed_research` result

## Required Configuration

### Environment Variables

| Variable | Purpose | Required |
|----------|---------|----------|
| `OLLAMA_API_KEY` | API key for LLM authentication (optional if using client credentials) | **Yes** |
| `OLLAMA_CLIENT_ID` | Cloudflare Access client ID | Optional |
| `OLLAMA_CLIENT_SECRET` | Cloudflare Access client secret | Optional |

### Application Properties (with defaults)

| Property | Default | Description |
|----------|---------|-------------|
| `koog.breed-research-agent.enabled` | `true` | Master enable/disable flag |
| `koog.breed-research-agent.base-url` | `https://ollama.com` | LLM API base URL |
| `koog.breed-research-agent.model` | `gpt-oss:120b` | Model identifier |
| `koog.breed-research-agent.scheduler.cron` | `0 0 0 * * *` | Cron schedule (midnight UTC) |
| `koog.breed-research-agent.web-search-max-results` | `3` | Max web search results |
| `koog.breed-research-agent.max-tool-calls` | `8` | Max tool invocations per run |

## Recommendations

### Immediate Actions (Required to Fix)

1. **Fix `BreedResearchStrategy` to capture tool results** - Add a `captureToolResult` node:
   ```kotlin
   // Add variable to store result
   var savedResearchJson: String? = null

   // Add capture node after executeTool
   val captureToolResult by node<ReceivedToolResult, ReceivedToolResult>("capture_tool_result") { toolResult ->
       val result = toolResult.result.toString()
       if (result.contains("\"success\"") && result.contains("\"breedId\"")) {
           savedResearchJson = result
           log.info { "Captured save_breed_research result" }
       }
       toolResult
   }

   // Update returnResult to use captured JSON
   val returnResult by node<String, String>("return_result") { _ ->
       savedResearchJson ?: run {
           log.warn { "No saved research found, returning empty result" }
           "{}"
       }
   }

   // Update edge: executeTool forwardTo captureToolResult
   // Update edge: captureToolResult forwardTo sendToolResult
   ```

### Secondary Actions (If Agent Not Ready)

1. **Verify `OLLAMA_API_KEY` or Cloudflare Access credentials** are set in the deployment environment
2. **Verify LLM connectivity** - ensure the Ollama-compatible endpoint is reachable

### Future Improvements

1. **Add manual trigger endpoint** - Allow on-demand breed research for testing
2. **Health check for agent readiness** - Expose `isReady()` status in actuator health endpoint
3. **Startup validation** - Fail fast if critical configuration is missing

## Files Analyzed

| File | Purpose |
|------|---------|
| `src/main/kotlin/.../ai/BreedResearchStrategy.kt` | **BUG LOCATION** - Missing tool result capture |
| `src/main/kotlin/.../ai/ChickenResearchStrategy.kt` | Working reference implementation |
| `src/main/kotlin/.../service/BreedResearcherScheduledTaskService.kt` | Scheduled task and JSON extraction |
| `src/main/kotlin/.../ai/KoogBreedResearchAgent.kt` | Agent initialization and execution |
| `src/main/kotlin/.../ai/tools/BreedResearchTools.kt` | Tool implementations |
| `src/main/resources/application.properties` | Configuration defaults |
| `src/main/kotlin/.../ChickenApiApplication.kt` | `@EnableScheduling` verification |

## Conclusion

The agent IS running successfully and saving data to Google Sheets (via `SaveBreedResearchTool`), but the scheduled task cannot parse the result because `BreedResearchStrategy` returns the LLM's conversational response instead of the tool's JSON output. This is a code bug, not a configuration issue.
