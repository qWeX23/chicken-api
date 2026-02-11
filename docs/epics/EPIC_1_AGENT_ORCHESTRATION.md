# EPIC 1 — Agent Job Runner + Orchestration (Foundation)

**Title:** Epic: Agent orchestration + scheduled job runner (facts/breeds/papers/content)  
**Labels:** epic, infra, agent  
**Milestone:** M1 - Data Expansion

## Goal
Create a reliable foundation to run Chicken agents on a schedule, with repeatable inputs/outputs, logging, and failure visibility.

## Scope
- Unified agent interface (inputs, outputs, status)
- Scheduler (cron-like) + manual runs
- Run logs + error visibility
- Idempotency patterns (avoid duplicates)

## Non-goals
- Full distributed orchestration
- Complex retry matrices for all failure classes in v1

## Technical design

### Core abstractions
- `AgentDefinition`: stable identifier, schedule config, enable flag, owner metadata.
- `AgentRunRequest`: trigger type (`SCHEDULED`, `MANUAL`, `BACKFILL`), parameters, correlation id.
- `AgentRunResult`: outcome, counters, emitted artifacts, warnings/errors.
- `RunRecord`: persisted run envelope with timing, status, diagnostics, and links to outputs.

### Execution flow
1. Scheduler or manual endpoint emits `AgentRunRequest`.
2. Orchestrator acquires run lock (`agent_name + idempotency_key`).
3. Agent executes with bounded runtime and standardized logging context.
4. Result is persisted as `RunRecord`.
5. Post-run hooks emit notifications for failures and SLA breaches.

### Idempotency strategy
- `idempotency_key = hash(agent_name + normalized_input + business_date)`.
- Persist key in run ledger with terminal status.
- Skip/merge duplicate requests when a successful run with same key exists.

### Storage sketch (starter)
- `agent_runs` table/sheet:
  - run_id
  - agent_name
  - trigger_type
  - idempotency_key
  - started_at / ended_at
  - status (`SUCCESS`, `FAILED`, `NO_OP`, `CANCELLED`)
  - input_json
  - output_summary_json
  - error_class / error_message

### Observability
- Structured logs for lifecycle events (`run_started`, `tool_call`, `run_completed`, `run_failed`).
- Basic dashboard metrics:
  - runs/day by agent
  - success rate
  - p95 duration
  - consecutive failures

## API/ops interfaces (v1)
- `POST /api/v1/agents/{agentName}/runs` → trigger manual run.
- `GET /api/v1/agents/runs?agentName=&status=&since=` → run history.
- `GET /api/v1/agents/health` → latest status snapshot for dashboard widgets.

## Acceptance criteria
- Can run each agent on demand + on a schedule.
- Every run produces a structured run record.
- Failed runs are visible and actionable.
- Agents can be safely re-run without duplicate effects.

## Child issues
- [ ] Define `Agent` contract (inputs/outputs + metadata)
- [ ] Implement `RunRecord` persistence (DB or Sheets log)
- [ ] Add scheduler (cron config + enable/disable per agent)
- [ ] Add idempotency helpers (hash keys, dedupe rules)
- [ ] Add standardized logging + run summary
- [ ] Add manual run endpoint/CLI command
- [ ] Add basic health dashboard view (even minimal)

## Risks and mitigations
- **Run collisions:** enforce locking and idempotency keys.
- **Silent failures:** require terminal run status write in finally block.
- **Operational drift:** maintain agent registry in one source of truth.
