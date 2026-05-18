# Investigation: Web Search and Web Fetch 404s in Production

**Date**: 2026-05-18
**Branch**: `docs/web-tools-404-investigation`
**Status**: Diagnosis complete, remediation plan proposed

## Summary

The production agents are configured to send `web_search` and `web_fetch` requests to the same base URL used for the LLM backend. On this box, that resolves to the local Ollama container at `http://ollama:11434`.

That host is reachable and serving standard Ollama endpoints, but it does not implement `POST /api/web_search` or `POST /api/web_fetch`. Both routes return `404 page not found`, which causes the AI web tools to fail during scheduled runs.

This failure is already happening in production. The breed research scheduler hit repeated `web_search` and `web_fetch` 404s on `2026-05-17`. The chicken facts agent shares the same web tool configuration, so it is also exposed to the same dependency mismatch when it attempts to use those tools.

## Primary Root Cause

The code assumes the configured web tools host exposes these two routes:

- `POST /api/web_search`
- `POST /api/web_fetch`

In production, those requests are sent to standard Ollama, which does not provide either endpoint.

## Configuration Chain That Produces the Failure

### Code path

- `src/main/kotlin/co/qwex/chickenapi/ai/tools/WebTools.kt`
  - `WebSearchTool` posts to `"$baseUrl/api/web_search"`
  - `WebFetchTool` posts to `"$baseUrl/api/web_fetch"`

### Agent wiring

- `src/main/kotlin/co/qwex/chickenapi/ai/KoogChickenFactsAgent.kt`
  - `sanitizedWebToolsBaseUrl` is derived from `properties.webToolsBaseUrl`
- `src/main/kotlin/co/qwex/chickenapi/ai/KoogBreedResearchAgent.kt`
  - `sanitizedWebToolsBaseUrl` is derived from `properties.webToolsBaseUrl`

### Default properties

- `src/main/resources/application.properties`
  - `koog.agent.web-tools-base-url=${koog.agent.base-url}`
  - `koog.breed-research-agent.web-tools-base-url=${koog.breed-research-agent.base-url}`

### Production compose wiring

- `docker-compose.yml`
  - `KOOG_AGENT_BASE_URL=${KOOG_AGENT_BASE_URL:-http://ollama:11434}`

The result is that both agents inherit a web tools base URL that points to Ollama unless explicitly overridden.

## Production Evidence

### Container and network state

- `chicken-api` is healthy and running on this host.
- `ollama` is running and reachable from `chicken-api`.
- `searxng` is also running on the same shared Docker network.

### Direct endpoint checks from inside `chicken-api`

The following behavior was verified without changing or restarting production services:

1. `GET http://ollama:11434/api/tags`
   - Succeeds and returns the expected Ollama model list.
2. `POST http://ollama:11434/api/web_search`
   - Returns `404`
   - Body: `404 page not found`
3. `POST http://ollama:11434/api/web_fetch`
   - Returns `404`
   - Body: `404 page not found`

This proves the issue is not basic connectivity to the LLM host. The host is reachable, but the requested routes do not exist there.

### Application log evidence

Production logs show the failure during the scheduled breed research run on `2026-05-17`:

- `Breed Research scheduled task started at: 2026-05-17T02:50:40...`
- `web_search failed with status 404 Not Found`
- `Tool "web_search" failed to execute ...`
- `web_fetch failed with status 404 Not Found`
- `Tool "web_fetch" failed to execute ...`

The logged stack traces point to:

- `co.qwex.chickenapi.ai.tools.WebSearchTool.doExecute(WebTools.kt:62)`
- `co.qwex.chickenapi.ai.tools.WebFetchTool.doExecute(WebTools.kt:154)`

## Scope of Impact

### Confirmed impacted

- Breed research scheduled runs

### Very likely impacted

- Chicken facts runs whenever the agent chooses to use `web_search` or `web_fetch`

### Not implicated by this issue

- General API health
- Standard read endpoints such as breeds and facts retrieval
- Basic Ollama connectivity for normal model requests

## Important Secondary Finding

The chicken facts pipeline has additional independent failures in production logs, including:

- agent graph stuck-node failures
- JSON parse failures
- at least one upstream Ollama internal server error

Those are separate issues. Fixing the 404 web tool dependency is necessary, but it may not be sufficient to make all AI flows fully healthy.

## Why the Current Setup Is Fragile

1. The web tools base URL defaults to the LLM base URL.
2. Production compose exposes only `KOOG_AGENT_BASE_URL`, not a separate web tools host.
3. There is no startup validation that checks whether the configured web tools host actually implements `/api/web_search` and `/api/web_fetch`.
4. Scheduled jobs discover the mismatch only at runtime after they are already executing in production.

## Candidate Existing Backend on This Box

There is a `searxng` container on the same Docker `shared` network, and it responds successfully from `chicken-api` with HTTP 200 on:

- `http://searxng:8080/search?q=chicken&format=json`

That makes it a plausible search backend candidate, but it does not match the application's expected web tools contract by itself. The current code expects:

- `POST /api/web_search` with the app's `WebSearchRequest` and `WebSearchResponse` shape
- `POST /api/web_fetch` with the app's `WebFetchRequest` and `WebFetchResponse` shape

So `searxng` can help, but it still needs an adapter or explicit integration work.

## Recommended Troubleshooting and Remediation Plan

### Phase 0: Contain production risk

1. Decide whether to temporarily disable the affected schedulers until the dependency is corrected.
2. If failed runs are acceptable during remediation, leave them enabled but do not assume the AI pipelines are healthy.

### Phase 1: Confirm the intended architecture

1. Identify the intended provider for `web_search` and `web_fetch`.
2. Confirm whether those routes are supposed to come from:
   - a dedicated internal adapter service
   - a hosted external service
   - or a future feature that has not been deployed yet
3. Do not keep assuming the LLM host should also serve web tools unless that is explicitly true.

### Phase 2: Fix configuration boundaries

1. Add explicit production configuration for the web tools host.
2. Introduce and document distinct environment variables such as:
   - `KOOG_AGENT_WEB_TOOLS_BASE_URL`
   - `KOOG_BREED_RESEARCH_AGENT_WEB_TOOLS_BASE_URL`
3. Update `docker-compose.yml`, `.env.production.example`, and deployment docs so web tools no longer implicitly inherit the LLM base URL in production.

### Phase 3: Provide a real web tools backend

1. If an existing compatible backend already exists, point production at it.
2. If no compatible backend exists, implement one of these paths:
   - a small in-app adapter exposing `/api/web_search` and `/api/web_fetch`
   - a separate internal service exposing that contract
3. If `searxng` is chosen for search:
   - map app `WebSearchRequest` to SearxNG query parameters
   - transform SearxNG JSON into `WebSearchResponse`
4. For `web_fetch`:
   - implement a page fetch and content extraction step
   - return the app's expected `title`, `content`, and `links` fields

### Phase 4: Add production safety checks

1. Validate the configured web tools host at startup when agents are enabled.
2. If validation fails, either:
   - mark the agent not ready
   - or fail startup with a clear configuration error
3. Add a health indicator or status check for the web tools dependency.

### Phase 5: Improve operability

1. Log the resolved `webToolsBaseUrl` for both agents at startup.
2. Add an admin or debug path to trigger a single controlled agent run without waiting for the 5-hour scheduler.
3. Keep scheduler runs observable with clear failure reasons tied to dependency checks.

### Phase 6: Re-verify after the 404 fix

1. Test the configured web tools backend directly from inside `chicken-api`.
2. Confirm both routes return the expected JSON contracts.
3. Run one controlled breed research execution and inspect logs end to end.
4. Run one controlled chicken facts execution and inspect logs end to end.
5. Verify data persistence in the corresponding repositories.

### Phase 7: Triage remaining AI issues separately

After the 404 dependency issue is resolved, re-evaluate the chicken facts workflow for:

- stuck-node behavior
- structured output parsing failures
- upstream Ollama instability

Those should be handled as separate bugs so the web tools outage does not mask them.

## Recommended First Change

The safest first change is to stop treating the LLM base URL and the web tools base URL as the same production dependency. Even if the final backend is still undecided, separating those configuration paths removes the current false assumption and makes the deployment intent explicit.

## Files and Runtime Areas Reviewed

- `src/main/kotlin/co/qwex/chickenapi/ai/tools/WebTools.kt`
- `src/main/kotlin/co/qwex/chickenapi/ai/KoogChickenFactsAgent.kt`
- `src/main/kotlin/co/qwex/chickenapi/ai/KoogBreedResearchAgent.kt`
- `src/main/kotlin/co/qwex/chickenapi/config/KoogAgentProperties.kt`
- `src/main/kotlin/co/qwex/chickenapi/config/BreedResearchAgentProperties.kt`
- `src/main/resources/application.properties`
- `docker-compose.yml`
- production `docker compose` service status and logs
- direct runtime checks from inside the `chicken-api` container

## Conclusion

This is a production configuration and dependency mismatch, not an API routing bug inside the Spring application itself. The app is correctly attempting to call its configured web tools routes, but production points those calls at standard Ollama, which does not implement them. The immediate next step is to separate the web tools dependency from the LLM dependency and wire the agents to a real backend that satisfies the expected contract.
