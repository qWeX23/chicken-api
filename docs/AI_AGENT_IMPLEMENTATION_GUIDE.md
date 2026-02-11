# Comprehensive Guide: Implementing New AI Agents in `chicken-api`

This guide explains, end-to-end, how to add a **new AI agent** to this codebase in a way that matches existing patterns (`KoogChickenFactsAgent`, `KoogBreedResearchAgent`) and production expectations.

It is intentionally practical and opinionated for this repository.

---

## 1) Understand the Existing Agent Architecture First

Before adding a new agent, study these current building blocks:

- **Agent services**
  - `src/main/kotlin/co/qwex/chickenapi/ai/KoogChickenFactsAgent.kt`
  - `src/main/kotlin/co/qwex/chickenapi/ai/KoogBreedResearchAgent.kt`
- **Reusable runtime container**
  - `src/main/kotlin/co/qwex/chickenapi/ai/AgentRuntime.kt`
- **Agent graph strategy examples**
  - `src/main/kotlin/co/qwex/chickenapi/ai/ChickenResearchStrategy.kt`
  - `src/main/kotlin/co/qwex/chickenapi/ai/BreedResearchStrategy.kt`
- **Tool examples**
  - `src/main/kotlin/co/qwex/chickenapi/ai/tools/WebTools.kt`
  - `src/main/kotlin/co/qwex/chickenapi/ai/tools/BreedResearchTools.kt`
- **Scheduling + orchestration**
  - `src/main/kotlin/co/qwex/chickenapi/service/ChickenFactResearcherScheduledTaskService.kt`
  - `src/main/kotlin/co/qwex/chickenapi/service/BreedResearcherScheduledTaskService.kt`
- **Configuration**
  - `src/main/kotlin/co/qwex/chickenapi/config/KoogAgentProperties.kt`
  - `src/main/kotlin/co/qwex/chickenapi/config/BreedResearchAgentProperties.kt`
  - `src/main/resources/application.properties`

If your new agent follows these patterns, it will integrate cleanly with scheduling, observability, and existing deployment assumptions.

---

## 2) Design Checklist (Do this before writing code)

Define the following clearly first:

1. **Business purpose**
   - What outcome does this agent produce?
   - Is it enrichment, moderation, classification, summarization, or generation?

2. **Input contract**
   - Does it choose work autonomously (like breed research), or get explicit input?
   - What data must exist in Sheets/repositories first?

3. **Output contract**
   - Plain text, structured JSON, or persisted domain object updates?
   - Which fields are required vs optional?

4. **Persistence model**
   - Where are results saved?
   - Does the tool save directly (current breed approach) or does service layer persist?

5. **Reliability model**
   - Max iterations, max tool calls, fallback behavior.
   - Parse-failure strategy and "no output" handling.

6. **Operational model**
   - On-demand endpoint, scheduled task, or both?
   - What readiness/status API is needed?

7. **Safety and quality controls**
   - Allowed tools.
   - Source/citation requirements.
   - Nullability rules for unverified fields.

A clear contract avoids prompt-only behavior drift and keeps the agent deterministic enough for production.

---

## 3) Recommended File/Package Layout for a New Agent

For a new agent called `FooAgent`, use this structure:

- `src/main/kotlin/co/qwex/chickenapi/ai/KoogFooAgent.kt`
- `src/main/kotlin/co/qwex/chickenapi/ai/FooStrategy.kt` *(if custom strategy needed)*
- `src/main/kotlin/co/qwex/chickenapi/ai/tools/FooTools.kt` *(or split by tool if large)*
- `src/main/kotlin/co/qwex/chickenapi/config/FooAgentProperties.kt`
- `src/main/kotlin/co/qwex/chickenapi/service/FooScheduledTaskService.kt` *(if scheduled)*
- `src/main/kotlin/co/qwex/chickenapi/controller/FooController.kt` *(if exposing API)*
- Repository/model files as needed under `model/`, `repository/`, `repository/db/`, `repository/sheets/`
- Tests in matching `src/test/kotlin/...` paths

---

## 4) Implement the Agent Service (`KoogFooAgent`)

Mirror the successful pattern already in place:

1. Annotate with `@Service`.
2. Inject strongly typed properties class (`FooAgentProperties`).
3. Inject a qualified `HttpClient` provider.
4. Build a reusable `AgentRuntime` in `@PostConstruct`.
5. Return early if disabled (`properties.enabled == false`).
6. Build:
   - `OllamaClient`
   - `SingleLLMPromptExecutor`
   - `LLModel` with `LLMCapability.Tools` and JSON schema support.
7. Register tools via `ToolRegistry`.
8. Configure prompt with `AIAgentConfig`.
9. On each run, create a new `AIAgent` instance (agents are single-use).
10. Add tracing feature for observability.
11. Catch and log failures, return `null` on failure.
12. Close clients in `@PreDestroy`.

### Logging standard
Use:

```kotlin
private val log = KotlinLogging.logger {}
```

This repository standard is explicitly required.

---

## 5) Create the Agent Strategy (Graph DSL)

Use a custom strategy when you need deterministic orchestration (recommended for most production agents):

- Start/reset state node.
- Main LLM node allowing tool calls.
- Tool execution node.
- Tool result capture node (if specific tool output must be extracted).
- Loop-back node to continue reasoning.
- Guardrails:
  - max tool calls
  - forced final action node ("save now")
- Return node that emits structured result JSON (or safe default).

`BreedResearchStrategy` is the best in-repo template for these mechanics.

### Why strategy matters
Without explicit strategy guards, agents can:

- Over-call tools,
- Fail to emit final structured output,
- Produce chatty but non-actionable responses.

---

## 6) Build Tools with Strict Typed Arguments

Tools should be explicit, small, and auditable.

### Tool conventions in this repo

- Extend `SimpleTool<Args>`.
- Define `@Serializable` argument (and result) classes.
- Add `@LLMDescription` per field.
- Set `name` and human-readable `description`.
- Return JSON strings for machine parsing in orchestration layers.
- Perform business validation in tool code, not only prompt text.

### Typical tool categories

1. **Selection tool** (`get_next_*`): choose unit of work.
2. **Read tool** (`get_*_details`): fetch current state/context.
3. **External evidence tool(s)** (`web_search`, `web_fetch`): collect references.
4. **Save/finalize tool** (`save_*`): validate + persist + return structured result.

For most agents, requiring exactly one final `save_*` tool call makes the run outcome parseable and reliable.

---

## 7) Define and Bind Configuration Properties

Create `@ConfigurationProperties(prefix = "koog.foo-agent")` with at least:

- `enabled`
- `baseUrl`
- `webToolsBaseUrl`
- `model`
- `webSearchMaxResults` *(if web search tools are used)*
- `maxAgentIterations`
- `maxToolCalls` *(if strategy enforces it)*

Then add defaults in `src/main/resources/application.properties`, following existing naming patterns.

If credentials are required, follow existing credential-validation patterns (`@RequireKoogCredentials`) where appropriate.

---

## 8) Add Data Model + Repository Integration

If the new agent writes domain data:

1. Extend/add model classes under `model/`.
2. Add repository interfaces in `repository/`.
3. Add Sheets-backed implementation in `repository/db/`.
4. Add/extend sheet table definitions and row mappers in `repository/sheets/`.
5. Ensure `create/update/find` behavior is deterministic and null-safe.
6. Ensure timestamp and source/citation fields are preserved.

### Practical tips for Sheets persistence

- Keep delimiters explicit when serializing list fields.
- Gracefully handle empty sheets, malformed rows, and partial data.
- Log parsing failures with enough context to debug data issues.

---

## 9) Orchestrate with a Scheduled Task Service (if autonomous)

If the agent runs automatically:

- Create a service like `FooScheduledTaskService`.
- Use `@Scheduled(fixedRateString = "${koog.foo-agent.scheduler.fixed-rate:PT5H}")` or suitable cadence.
- Flow:
  1. Log start.
  2. Skip if agent not ready.
  3. Generate run ID + timestamps.
  4. Invoke agent in `runBlocking`.
  5. Parse final tool JSON.
  6. Map to run outcome enum (`SUCCESS`, `NO_OUTPUT`, `FAILED`).
  7. Persist run/audit record.
  8. Log completion.

Persisting run metadata makes incidents diagnosable and trend tracking possible.

---

## 10) Expose APIs for Visibility and Operations

If operational visibility is needed:

- Add a controller for run history and/or results.
- Return HATEOAS links to be consistent with this API style.
- Add a `/status` endpoint returning `AgentStatus.forAgent(...)`.
- Include OpenAPI annotations (`@Operation`, `@ApiResponses`) as existing controllers do.

A status endpoint is critical for production debugging and readiness checks.

---

## 11) Testing Strategy (Required)

At minimum, add/extend tests for:

1. **Tool unit tests**
   - Argument validation
   - edge cases (missing records, invalid IDs, empty lists)
   - JSON output shape

2. **Strategy behavior tests**
   - tool call cap enforcement
   - forced save branch
   - final output extraction behavior

3. **Repository tests**
   - mapping to/from Sheets row format
   - null/empty handling
   - filtering/sorting correctness

4. **Service tests**
   - outcome mapping from raw agent response
   - parse failures to `NO_OUTPUT`/`FAILED`
   - run record persistence behavior

5. **Controller tests**
   - response payloads, links, status endpoint behavior

Use current test styles under `src/test/kotlin/co/qwex/chickenapi/` as templates.

---

## 12) Prompt Engineering Guidelines for This Repo

Prompts should:

- Encode workflow order explicitly (e.g., "call `get_next`, then `get_details`, then research, then `save`").
- Define hard output quality standards.
- Tell the model what to do when information is uncertain (use `null`, don’t guess).
- Require at least one source URL for externally-derived claims.
- Make the final save step mandatory.

Avoid relying on prompt text alone for correctness; enforce important constraints in strategy and tool code.

---

## 13) Reliability & Failure-Handling Patterns

Adopt these safeguards by default:

- `isReady()` checks before scheduled runs.
- Null-safe handling for client/provider setup.
- Strategy-level tool call caps.
- Explicit parse fallback behavior in orchestration service.
- Run outcome taxonomy (`SUCCESS`, `NO_OUTPUT`, `FAILED`).
- Structured logging with run IDs and entity IDs.

When parsing model output, always assume malformed or partial responses can happen.

---

## 14) Security and Compliance Considerations

- Never log secrets (API keys, bearer tokens).
- Keep credentials in env vars/config, not hardcoded.
- Validate outbound URL usage if creating new web tools.
- Be explicit about allowed data sources and quality thresholds.
- Avoid silently writing unverifiable claims to persisted records.

---

## 15) Step-by-Step Implementation Workflow (Copy/Paste Checklist)

1. Define purpose, input/output contract, and persistence model.
2. Add/extend domain model(s) and repository interfaces.
3. Implement Sheets repository + table mapping if needed.
4. Create tools (`select`, `details`, `research`, `save`).
5. Implement strategy with guardrails (max tool calls + forced save).
6. Implement `KoogFooAgent` service using `AgentRuntime` pattern.
7. Add properties class and application defaults.
8. Add scheduled service and run record logging (if autonomous).
9. Add controller endpoints (`history`, `byId`, `status`) if needed.
10. Add comprehensive tests.
11. Run formatting and tests:
    - `JAVA_HOME=/root/.local/share/mise/installs/java/21.0.2 ./gradlew spotlessApply`
    - `JAVA_HOME=/root/.local/share/mise/installs/java/21.0.2 ./gradlew test --no-daemon`
12. Update docs/README if introducing new operational endpoints or env vars.

---

## 16) Definition of Done for a New Agent

A new agent is done only when all are true:

- [ ] Agent can initialize and report readiness.
- [ ] Agent run produces deterministic structured output.
- [ ] Final output is persisted correctly.
- [ ] Run audit/history is persisted (for autonomous runs).
- [ ] Status/visibility endpoints are available (if operationally required).
- [ ] Unit/integration tests cover happy path + failure path.
- [ ] `spotlessApply` and `test --no-daemon` pass on Java 21.
- [ ] Documentation includes config keys, behavior, and operational troubleshooting notes.

---

## 17) Common Pitfalls (and How to Avoid Them)

1. **Prompt-only workflows with no strategy enforcement**
   - Fix: add graph guardrails + forced save node.

2. **Non-parseable final output**
   - Fix: enforce JSON return from final tool and parse centrally.

3. **Too many tool calls / runaway loops**
   - Fix: track call count in strategy state.

4. **Silent no-op runs**
   - Fix: classify `NO_OUTPUT`, log, and persist run records.

5. **Weak source quality**
   - Fix: codify source requirements in both prompt and tool validation.

6. **Missing operational visibility**
   - Fix: add `/status` and history endpoints with HATEOAS links.

---

## 18) Suggested Next Improvements (Optional)

If you plan major agent expansion, consider:

- Standardizing run metadata into a reusable `AgentRunRecord` abstraction.
- Shared parser utility for extracting final JSON tool output.
- Unified base class for Koog agent services to reduce duplicated setup.
- Tool-level retries/backoff wrappers for transient network failures.
- Feature flags for per-agent rollout and easy disablement.

---

## 19) Quick Reference: Existing Agent Patterns to Reuse

- **Use this when you need autonomous enrichment with deterministic save:**
  - `KoogBreedResearchAgent` + `BreedResearchStrategy` + `SaveBreedResearchTool`
- **Use this when you need lighter trivia/fact generation:**
  - `KoogChickenFactsAgent` + `ChickenResearchStrategy` + `SaveChickenFactTool`

Reusing these patterns will keep your implementation consistent with repository standards and reduce onboarding cost for future contributors.
