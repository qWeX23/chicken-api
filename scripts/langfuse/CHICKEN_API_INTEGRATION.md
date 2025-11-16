# Chicken API Integration with Langfuse

This guide explains how to configure the chicken-api to send telemetry data to your local Langfuse instance.

## Overview

Langfuse is used to collect OpenTelemetry traces from the Koog agent running in the chicken-api. This provides visibility into:

- LLM interactions and prompts
- Agent decision-making processes
- Tool usage and results
- Performance metrics
- Error tracking

## Prerequisites

1. Langfuse must be running locally (see [README.md](README.md) for setup instructions)
2. You must have created a project in Langfuse and obtained API keys

## Quick Setup

### 1. Start Langfuse

If you haven't already, start Langfuse:

```bash
cd scripts/langfuse
./generate-secrets.sh  # Only needed once
docker compose up -d
```

Wait for the services to be ready:

```bash
docker compose logs -f langfuse-web
```

### 2. Create API Keys

1. Open http://localhost:3000 in your browser
2. Sign up or log in
3. Create a new project or use the default project
4. Navigate to **Settings** → **API Keys**
5. Copy your **Public Key** (starts with `pk-lf-`)
6. Copy your **Secret Key** (starts with `sk-lf-`)

### 3. Configure Chicken API

Set these environment variables before starting the chicken-api:

```bash
# Langfuse endpoint
export LANGFUSE_HOST=http://localhost:3000

# Your project's API keys (replace with actual values from step 2)
export LANGFUSE_PUBLIC_KEY=pk-lf-your-public-key-here
export LANGFUSE_SECRET_KEY=sk-lf-your-secret-key-here

# Optional: Enable Koog agent tracing (if not already enabled)
export KOOG_AGENT_ENABLED=true
```

### 4. Start Chicken API

Start your chicken-api application as usual. If the Koog agent is configured correctly, it will automatically send traces to Langfuse.

## Verifying the Integration

### Check Chicken API Logs

When the chicken-api starts, you should see log messages indicating the Koog agent is initialized:

```
Koog chicken facts agent initialized with model gpt-oss:120b using base https://ollama.com
```

### Check Langfuse UI

1. Navigate to http://localhost:3000
2. Go to your project dashboard
3. You should see traces appearing when the Koog agent runs
4. Click on any trace to see detailed execution information

### Test the Agent

Trigger the Koog agent by waiting for the scheduled task, or manually invoke it if you have an endpoint for that. Check the Langfuse UI to see the trace data.

## Environment Variables Reference

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `LANGFUSE_HOST` | URL of your Langfuse instance | `http://localhost:3000` |
| `LANGFUSE_PUBLIC_KEY` | Project public key from Langfuse UI | `pk-lf-abc123...` |
| `LANGFUSE_SECRET_KEY` | Project secret key from Langfuse UI | `sk-lf-xyz789...` |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KOOG_AGENT_ENABLED` | Enable/disable the Koog agent | `true` |
| `KOOG_AGENT_MODEL` | LLM model to use | `gpt-oss:120b` |
| `KOOG_AGENT_BASE_URL` | Ollama API endpoint | `https://ollama.com` |

## Troubleshooting

### No Traces in Langfuse

1. **Check API keys**: Verify your `LANGFUSE_PUBLIC_KEY` and `LANGFUSE_SECRET_KEY` are correct
2. **Check connectivity**: Ensure Langfuse is running: `docker compose ps`
3. **Check logs**: Look for errors in chicken-api logs related to Koog or Langfuse
4. **Verify agent is running**: Check that the Koog agent is actually executing

### Connection Refused Errors

If you see connection errors:

1. Verify Langfuse is running: `docker compose ps`
2. Check the `LANGFUSE_HOST` environment variable
3. Ensure you're using `http://` (not `https://`) for local development
4. Try `curl http://localhost:3000` to verify connectivity

### Authentication Errors

If you see 401/403 errors:

1. Verify your API keys are correct
2. Check that the keys belong to an active project
3. Regenerate keys in the Langfuse UI if needed

### Agent Not Producing Traces

If the agent runs but no traces appear:

1. Check that the Koog agent has tracing enabled
2. Verify the `OLLAMA_API_KEY` is set (required for the Koog agent)
3. Review the agent configuration in `application.properties`

## Advanced Configuration

### Using a Different Langfuse Port

If you need to run Langfuse on a different port:

1. Edit `docker-compose.yml` and change the port mapping for `langfuse-web`
2. Update `LANGFUSE_HOST` to use the new port

### Running Langfuse in Production

For production deployments, refer to:
- [Langfuse Self-Hosting Guide](https://langfuse.com/docs/deployment/self-host)
- [Security Best Practices](https://langfuse.com/docs/deployment/security)

### Using Langfuse Cloud

Instead of running Langfuse locally, you can use Langfuse Cloud:

1. Sign up at https://cloud.langfuse.com
2. Create a project and get API keys
3. Set `LANGFUSE_HOST=https://cloud.langfuse.com`

## Data Privacy

When running Langfuse locally:

- All data stays on your machine
- No data is sent to external services (except LLM providers like Ollama)
- You have complete control over data retention and access

## Additional Resources

- [Langfuse Documentation](https://langfuse.com/docs)
- [OpenTelemetry Overview](https://opentelemetry.io/docs/)
- [Koog Agents Documentation](https://github.com/koog-ai/agents)
- [Main Langfuse Setup README](README.md)
