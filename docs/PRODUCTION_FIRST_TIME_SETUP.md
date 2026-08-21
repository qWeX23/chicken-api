# Chicken API: First-Time Production Setup

This guide walks through a clean, first-time production deployment of `chicken-api` with Docker Compose.

The deployment expects:

- this API running from `docker-compose.yml`
- an external Docker network named `shared`
- an Ollama Cloud account and dedicated API key for generation
- an existing local Ollama instance on `shared` for embeddings

## 1) Prerequisites

- Docker Engine + Docker Compose plugin installed
- Access to this repository on the production host
- A Google service account JSON key with access to the configured Google Sheet
- A dedicated Ollama Cloud API key from `https://ollama.com/settings/keys`

Quick checks:

```bash
docker --version
docker compose version
```

## 2) Prepare the shared external network

The compose file uses an external network called `shared`.

If it does not exist yet:

```bash
docker network create shared
```

Verify:

```bash
docker network ls | grep shared
```

## 3) Prepare environment file

Copy the production example file:

```bash
cp .env.production.example .env
```

Set values in `.env`:

| Variable | Required | Description |
|---|---|---|
| `GOOGLE_APPLICATION_CREDENTIALS_FILE` | Yes | Absolute host path to Google credentials JSON |
| `CHICKEN_API_PORT` | No | Host port mapped to container `8080` (default `8080`) |
| `KOOG_OLLAMA_BASE_URL` | No | Generation API base URL (default `https://ollama.com`) |
| `KOOG_OLLAMA_EMBEDDING_BASE_URL` | No | Local embedding endpoint (default `http://ollama:11434`) |
| `KOOG_AGENT_MODEL` | No | Direct Ollama Cloud model name (default `gpt-oss:120b`) |
| `KOOG_OLLAMA_WEB_TOOLS_PROVIDER` | No | Search provider (production default `searxng`) |
| `SEARXNG_BASE_URL` | No | Search service URL on `shared` (production default `http://searxng:8080`) |

Notes:

- Run `./scripts/set-ollama-cloud-key.sh` to validate and install the Cloud key. The key is stored in a mounted `0600` file, not `.env` or Docker environment metadata.
- The default production path uses local SearXNG, so the Cloud key is sent only to `https://ollama.com`.
- Ensure `nomic-embed-text` is installed in local Ollama before enabling the jobs.
- If your local Ollama container is not named `ollama`, update `KOOG_OLLAMA_EMBEDDING_BASE_URL`.
- Direct `ollama.com` model names do not use the local `-cloud` suffix.
- Do not commit `.env` or credentials files.

Install the dedicated key and validate provider readiness:

```bash
./scripts/set-ollama-cloud-key.sh
```

This enables both agent runtimes but keeps both schedulers disabled.

## 4) Start the API

Build and run:

```bash
docker compose --env-file .env up -d --build
```

Confirm service status:

```bash
docker compose ps
```

Tail logs:

```bash
docker compose logs -f chicken-api
```

## 5) Validate health and connectivity

Health endpoint:

```bash
curl http://localhost:${CHICKEN_API_PORT:-8080}/readyz
```

Expected: JSON health response with `UP`.

Prometheus metrics are exposed only on container port `8081` at `/actuator/prometheus`; the management port is not published to the host.

Also verify API endpoint:

```bash
curl http://localhost:${CHICKEN_API_PORT:-8080}/api/v1/breeds/
```

## 6) Day-2 operations

See [`SCHEDULED_JOBS_RUNBOOK.md`](SCHEDULED_JOBS_RUNBOOK.md) for schedules, metrics, alerts, and incident response.

Restart after config changes:

```bash
docker compose --env-file .env up -d
```

Rebuild on code changes:

```bash
docker compose --env-file .env up -d --build
```

Stop:

```bash
docker compose down
```

## Troubleshooting

### `network shared declared as external, but could not be found`

Create it:

```bash
docker network create shared
```

### App fails at startup with credential errors

- Confirm `GOOGLE_APPLICATION_CREDENTIALS_FILE` is an absolute path
- Confirm file exists and is readable on host
- Confirm service account has Google Sheets access

### App cannot reach Ollama Cloud

- Verify outbound HTTPS access to `https://ollama.com`
- Rerun `./scripts/set-ollama-cloud-key.sh` to validate the key and selected model
- Verify the model uses its direct API name, such as `gpt-oss:120b`, rather than `gpt-oss:120b-cloud`

### App cannot reach local embeddings

- Verify the local Ollama container is connected to `shared`
- Verify `KOOG_OLLAMA_EMBEDDING_BASE_URL` uses its reachable container/service name
- Verify `nomic-embed-text` appears in `http://ollama:11434/api/tags`

### API key validation failure

- Verify `secrets/ollama-api-key` exists with mode `0600`
- Rerun `./scripts/set-ollama-cloud-key.sh`; it validates the key before replacing the current secret

### Web search fails

- Verify `KOOG_OLLAMA_WEB_TOOLS_PROVIDER=searxng`
- Verify `SEARXNG_BASE_URL=http://searxng:8080`
