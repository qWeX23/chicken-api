# Investigation: Web Search and Web Fetch 404s in Production

**Date**: 2026-05-18
**Branch**: `docs/web-tools-404-investigation`
**Status**: Diagnosis complete, remediation plan proposed

## Summary

The production agents are configured to send `web_search` and `web_fetch` requests to the same base URL used for the LLM backend. In production, that base URL is overridden to the local Ollama container at `http://ollama:11434`.

That is the wrong target for these tools. Ollama documents `web_search` and `web_fetch` as hosted web API endpoints on `https://ollama.com/api/...`, not local Ollama app endpoints on `http://localhost:11434/api/...`.

Because `web-tools-base-url` currently inherits the overridden local LLM base URL, production sends web tool calls to local Ollama, where both routes return `404 page not found`.

This failure is already happening in production. The breed research scheduler hit repeated `web_search` and `web_fetch` 404s on `2026-05-17`. The chicken facts agent shares the same web tool configuration, so it is also exposed to the same dependency mismatch when it attempts to use those tools.

There is also a second issue: when the hosted endpoints are tested directly with the currently configured `KOOG_AGENT_API_KEY`, both hosted web tool routes return `401 {"error": "unauthorized"}`. So production appears to have both a host-routing problem and a direct hosted web-tools authorization problem.

## Primary Root Cause

The code assumes the configured web tools host exposes these two routes:

- `POST /api/web_search`
- `POST /api/web_fetch`

Those routes do exist in Ollama's hosted web API, but production is sending them to standard local Ollama because `web-tools-base-url` inherits the overridden local `base-url`.

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

The result is that both agents inherit a web tools base URL that points to local Ollama unless explicitly overridden.

## Confirmed Intended Backend Behavior

Ollama's own documentation distinguishes between:

- local API base URL: `http://localhost:11434/api`
- hosted API base URL: `https://ollama.com/api`

The docs for web search explicitly document:

- `POST https://ollama.com/api/web_search`
- `POST https://ollama.com/api/web_fetch`

The docs do not describe those as local Ollama app endpoints.

This means the intended architecture can legitimately be:

- model execution through local Ollama, including cloud-backed models when local Ollama is signed in
- web tools through direct hosted Ollama web API calls

That distinction was the missing piece in the original diagnosis.

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

Hosted Ollama behavior was also checked directly:

1. `POST https://ollama.com/api/web_search`
   - Returns `401`
   - Body: `{"error": "unauthorized"}`
2. `POST https://ollama.com/api/web_fetch`
   - Returns `401`
   - Body: `{"error": "unauthorized"}`
3. `GET https://ollama.com/api/tags`
   - Returns `200` even without auth
   - Therefore it is not a valid authentication check for protected web tool routes

The configured `KOOG_AGENT_API_KEY` is present in the running container, so the `401` result points to a direct hosted API authorization problem rather than a missing environment variable.

This proves two separate runtime facts:

1. The local LLM host is reachable, but it is the wrong host for web tools.
2. The hosted Ollama web tools routes exist, but the current direct API authorization is not accepted there.

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

## Important Secondary Findings

### 1. Hosted web-tools auth is also failing

Even after correcting the host, the current `KOOG_AGENT_API_KEY` still appears unauthorized for direct use against:

- `https://ollama.com/api/web_search`
- `https://ollama.com/api/web_fetch`

Possible explanations include:

- the configured key is invalid or stale
- the key is valid for some hosted API usage but not this capability
- the account backing the key does not currently have access to web search/fetch

This needs to be verified before assuming a host-only fix will fully restore the tool chain.

### 2. The chicken facts pipeline has other independent failures

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

## Existing Services on This Box

There is a `searxng` container on the same Docker `shared` network, and it responds successfully from `chicken-api` with HTTP 200 on:

- `http://searxng:8080/search?q=chicken&format=json`

That may still be useful if the team later decides to build its own adapter, but it is not the primary intended backend for these tools if the goal is to use Ollama's documented hosted web search and web fetch APIs.

## Recommended Troubleshooting and Remediation Plan

### Phase 0: Contain production risk

1. Decide whether to temporarily disable the affected schedulers until the dependency is corrected.
2. If failed runs are acceptable during remediation, leave them enabled but do not assume the AI pipelines are healthy.

### Phase 1: Correct the host split explicitly

1. Stop inheriting `web-tools-base-url` from the local LLM base URL in production.
2. Set explicit web tools hosts for both agents:
   - `KOOG_AGENT_WEB_TOOLS_BASE_URL=https://ollama.com`
   - `KOOG_BREED_RESEARCH_AGENT_WEB_TOOLS_BASE_URL=https://ollama.com`
3. Leave `KOOG_AGENT_BASE_URL=http://ollama:11434` if local Ollama remains the intended LLM host.

### Phase 2: Validate direct hosted web-tools authorization

1. Test the configured API key directly against:
   - `POST https://ollama.com/api/web_search`
   - `POST https://ollama.com/api/web_fetch`
2. If `401 unauthorized` persists, verify:
   - the key is current and correctly copied
   - the account behind the key has access to Ollama web search/fetch
   - production is using the expected key value
3. Do not assume `GET /api/tags` is a valid auth test because it succeeds without authentication.

### Phase 3: Fix configuration boundaries in code and docs

1. Add explicit production configuration for the web tools host.
2. Introduce and document distinct environment variables such as:
   - `KOOG_AGENT_WEB_TOOLS_BASE_URL`
   - `KOOG_BREED_RESEARCH_AGENT_WEB_TOOLS_BASE_URL`
3. Update `docker-compose.yml`, `.env.production.example`, and deployment docs so web tools no longer implicitly inherit the LLM base URL in production.

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

### Phase 6: Re-verify after the host and auth fixes

1. Test the configured web tools backend directly from inside `chicken-api`.
2. Confirm both routes return the expected JSON contracts.
3. Run one controlled breed research execution and inspect logs end to end.
4. Run one controlled chicken facts execution and inspect logs end to end.
5. Verify data persistence in the corresponding repositories.

### Phase 7: Consider fallback architecture only if hosted Ollama is not viable

1. If hosted Ollama web tools cannot be used for product or access reasons, then evaluate a custom adapter.
2. In that fallback path, `searxng` becomes relevant as an internal search provider.
3. That would be a design change, not just a production config correction.

### Phase 8: Triage remaining AI issues separately

After the 404 dependency issue is resolved, re-evaluate the chicken facts workflow for:

- stuck-node behavior
- structured output parsing failures
- upstream Ollama instability

Those should be handled as separate bugs so the web tools outage does not mask them.

## Recommended First Change

The safest first change is to explicitly separate the two production dependencies that are currently conflated:

- local Ollama for model execution
- hosted `ollama.com` for `web_search` and `web_fetch`

Immediately after that, validate the configured direct API key against the hosted web tool routes, because the current runtime evidence suggests a second authorization problem beyond the wrong host.

## Files and Runtime Areas Reviewed

- `src/main/kotlin/co/qwex/chickenapi/ai/tools/WebTools.kt`
- `src/main/kotlin/co/qwex/chickenapi/ai/KoogChickenFactsAgent.kt`
- `src/main/kotlin/co/qwex/chickenapi/ai/KoogBreedResearchAgent.kt`
- `src/main/kotlin/co/qwex/chickenapi/config/KoogAgentProperties.kt`
- `src/main/kotlin/co/qwex/chickenapi/config/BreedResearchAgentProperties.kt`
- `src/main/resources/application.properties`
- `docker-compose.yml`
- `https://docs.ollama.com/api`
- `https://docs.ollama.com/cloud`
- `https://docs.ollama.com/capabilities/web-search`
- `https://docs.ollama.com/api/authentication`
- production `docker compose` service status and logs
- direct runtime checks from inside the `chicken-api` container

## Conclusion

This is primarily a production configuration boundary bug, not an API routing bug inside the Spring application itself. The app is correctly attempting to call its configured web tools routes, but production currently points those calls at local Ollama instead of Ollama's hosted web API. There also appears to be a second issue with direct authorization to the hosted web tool endpoints. The immediate next step is to separate the web tools dependency from the local LLM dependency and then validate the hosted web-tools credential path end to end.
