# Chicken API: First-Time Production Setup

This guide walks through a clean, first-time production deployment of `chicken-api` with Docker Compose.

The deployment expects:

- this API running from `docker-compose.yml`
- an external Docker network named `shared`
- an existing Ollama instance available on that `shared` network

## 1) Prerequisites

- Docker Engine + Docker Compose plugin installed
- Access to this repository on the production host
- A Google service account JSON key with access to the configured Google Sheet
- A valid Koog/Ollama API key

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
| `KOOG_AGENT_API_KEY` | Yes | API key for `koog.agent.api-key` |
| `CHICKEN_API_PORT` | No | Host port mapped to container `8080` (default `8080`) |
| `KOOG_AGENT_BASE_URL` | No | Ollama base URL on `shared` (default `http://ollama:11434`) |

Notes:

- `KOOG_AGENT_BASE_URL` should point to the Ollama service/container name reachable on `shared`.
- If your Ollama container is not named `ollama`, update this value.
- Do not commit `.env` or credentials files.

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
curl http://localhost:${CHICKEN_API_PORT:-8080}/actuator/health
```

Expected: JSON health response with `UP`.

Also verify API endpoint:

```bash
curl http://localhost:${CHICKEN_API_PORT:-8080}/api/v1/breeds/
```

## 6) Day-2 operations

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

### App cannot reach Ollama

- Verify Ollama container is connected to `shared`
- Verify `KOOG_AGENT_BASE_URL` uses Ollama's reachable container/service name
- From API container, test DNS/connectivity to Ollama host on port `11434`

### API key validation failure

- Ensure `KOOG_AGENT_API_KEY` is set and non-empty in `.env`
