# Scheduled Jobs Runbook

Chicken API runs two singleton research tasks each day in `America/Chicago`:

| Task | Default schedule | Timeout |
|---|---|---|
| `chicken-facts` | 04:15 | 30 minutes |
| `breed-research` | 05:15 | 30 minutes |

Both tasks use `gpt-oss:120b` through the direct Ollama Cloud API, local `nomic-embed-text` for fact embeddings, SearXNG for web search, and Google Sheets for source data and run records. The schedules, timezone, timeout, and enable flags can be overridden with the `KOOG_AGENT_SCHEDULER_*` and `KOOG_BREED_RESEARCH_AGENT_SCHEDULER_*` environment variables.

The production Compose defaults keep both tasks disabled. Set each task's agent and scheduler enable flags to `true` only after its configured model/provider passes a manual run.

## Provider Activation

1. Create a dedicated key at `https://ollama.com/settings/keys`. Do not reuse Open WebUI's key.
2. Run `./scripts/set-ollama-cloud-key.sh`. It prompts without echo, validates the key and `gpt-oss:120b`, and atomically writes `secrets/ollama-api-key` with mode `0600`.
3. The script rebuilds Chicken API and enables both agent runtimes while leaving both schedulers disabled. Confirm both ready metrics become `1`.
4. For another direct Cloud model, use `./scripts/set-ollama-cloud-key.sh --model MODEL`. Direct `ollama.com` model names do not use the local `-cloud` suffix.
5. For one task at a time, set its cron to a near-future test time and enable its scheduler for one invocation.
6. After the invocation reaches a terminal state, disable it and verify the Google Sheets record and metrics. Only then restore the normal cron and enable the scheduler.

## Expected State

- `chicken_api_scheduled_task_enabled{task=...}` is `1` after the task is intentionally activated; it is `0` during provider maintenance.
- `chicken_api_scheduled_task_ready{task=...}` is `1` after the Cloud key is installed, even while its scheduler remains disabled.
- `chicken_api_scheduled_task_in_progress{task=...}` normally remains `0`.
- `chicken_api_scheduled_task_last_success_timestamp_seconds` advances every day.
- `chicken_api_scheduled_task_last_result{result="success"}` is `1` after the latest run.
- A terminal run record exists in the corresponding Google Sheet for success, no output, failure, timeout, or not-ready outcomes.

Prometheus scrapes `http://chicken-api:8081/actuator/prometheus`. The management port is internal-only. Grafana provisions the `Chicken API Daily Jobs` dashboard, and Alertmanager sends firing and resolved notifications to Discord.

## First Response

1. Check container and dependency health:

   ```bash
   docker compose ps
   docker exec chicken-api curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness
   docker exec chicken-api curl --fail --silent http://ollama:11434/api/tags
   docker exec chicken-api curl --fail --silent "http://searxng:8080/search?q=chicken&format=json"
   ```

2. Inspect task logs by task name and run ID:

   ```bash
   docker logs --since 36h chicken-api
   ```

3. Check the latest result, last attempt age, last success age, duration, and in-progress state on the Grafana dashboard.

4. Verify that `nomic-embed-text` appears in the local Ollama tags response. Cloud key/model access is validated by `set-ollama-cloud-key.sh` and reflected by the task ready metrics.

5. Verify Google Sheets credentials are mounted read-only and that the service account can read and append to the configured spreadsheet.

## Alert Meaning

| Alert | Action |
|---|---|
| Metrics missing | Check the direct Prometheus target and application startup. |
| Disabled | Confirm maintenance is intentional. Otherwise restore both the task's agent and scheduler enable flags after a successful manual run. |
| Not ready | Check model availability and Spring configuration initialization. |
| Failed | Find the latest run ID in logs and inspect the root exception. |
| Timed out | Check Ollama CPU/memory pressure, SearXNG latency, and Sheets latency. |
| No output | Inspect tool-call behavior and duplicate-retry logs. |
| Attempt missing | Confirm cron, timezone, container uptime, and scheduler threads. |
| Stale | Treat as missed business output even when HTTP health is green. |
| Stuck | Capture thread state before restarting; the timeout may be blocked in non-cancellable I/O. |
| Overlap | Check for duplicate containers, manual test schedules, or an unexpectedly long run. |

## Safety Notes

- The in-process single-flight guard prevents overlap in one JVM. Do not run multiple Chicken API replicas without a distributed lock.
- Google Sheets updates are not transactional with run-record appends. A persistence-failure alert can indicate that domain data changed without a matching audit row.
- Scheduled jobs do not automatically catch up after downtime. The 26-hour attempt and 30-hour success alerts detect missed windows.
- Do not retry append or update operations blindly; add durable idempotency before introducing write retries.
