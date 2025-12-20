# Investigation: Why Breed Research Has Not Run

**Date**: 2025-12-20
**Branch**: `claude/investigate-breed-research-uyq5H`

## Summary

The breed research agent has not executed because the `OLLAMA_API_KEY` environment variable is not set. Without this API key, the agent cannot initialize its LLM connection and marks itself as "not ready", causing the scheduled task to skip execution.

## Root Causes

### 1. Missing `OLLAMA_API_KEY` Environment Variable (Primary Cause)

**Evidence**:
- `application.properties:39`: `koog.breed-research-agent.api-key=${OLLAMA_API_KEY:}`
- The syntax `${OLLAMA_API_KEY:}` means: use the environment variable, or default to empty string
- Empty string fails the `isNotBlank()` check

**Code Path**:
```
KoogBreedResearchAgent.kt:62-64:
  val apiKey = properties.apiKey?.takeIf { it.isNotBlank() }
  if (apiKey == null) {
      log.warn { "koog.breed-research-agent.api-key is not set; Breed research agent will be skipped." }
  }
```

When API key is missing:
1. `promptExecutor` is never initialized (remains `null`)
2. `isReady()` returns `false` (line 135: `fun isReady(): Boolean = promptExecutor != null`)
3. Scheduled task checks `isReady()` and exits early (lines 57-60)

**Expected Log Output**:
```
WARN  - koog.breed-research-agent.api-key is not set; Breed research agent will be skipped.
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
- [ ] `OLLAMA_API_KEY` environment variable is set
- [ ] LLM backend is accessible at configured base URL

## Required Configuration

### Environment Variables

| Variable | Purpose | Required |
|----------|---------|----------|
| `OLLAMA_API_KEY` | API key for LLM authentication | **Yes** |

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

### Immediate Actions

1. **Set `OLLAMA_API_KEY`** in the deployment environment
2. **Verify LLM connectivity** - ensure the Ollama-compatible endpoint is reachable
3. **Review application logs** for startup warnings about missing API key

### Future Improvements

1. **Add manual trigger endpoint** - Allow on-demand breed research for testing:
   ```kotlin
   @PostMapping("/api/v1/breed-research/trigger")
   fun triggerResearch(): ResponseEntity<String>
   ```

2. **Health check for agent readiness** - Expose `isReady()` status in actuator health endpoint

3. **Startup validation** - Fail fast if critical configuration is missing rather than silently skipping

## Files Analyzed

| File | Purpose |
|------|---------|
| `src/main/resources/application.properties` | Configuration defaults |
| `src/main/kotlin/.../ai/KoogBreedResearchAgent.kt` | Agent initialization and execution |
| `src/main/kotlin/.../service/BreedResearcherScheduledTaskService.kt` | Scheduled task runner |
| `src/main/kotlin/.../config/BreedResearchAgentProperties.kt` | Configuration properties class |
| `src/main/kotlin/.../ChickenApiApplication.kt` | `@EnableScheduling` verification |
