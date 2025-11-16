# Langfuse Local Development Setup

This directory contains the Docker Compose configuration for running Langfuse locally. Langfuse is an open-source LLM engineering platform that provides observability and analytics for the Koog OpenTelemetry exporter used by the chicken-api.

## Prerequisites

Before you begin, ensure you have the following installed:

- **Git** - for cloning repositories
- **Docker Desktop** or **Docker Engine** with **Docker Compose v2**
- **OpenSSL** - for generating secure secrets

### Port Requirements

The following ports must be available on your machine:

- `3000` - Langfuse web UI
- `3030` - Langfuse worker (localhost only)
- `5432` - PostgreSQL (localhost only)
- `6379` - Redis (localhost only)
- `8123` - ClickHouse HTTP (localhost only)
- `9000` - ClickHouse native protocol (localhost only)
- `9090` - MinIO API
- `9091` - MinIO console (localhost only)

## Quick Start

### 1. Generate Secrets

Create a `.env` file from the template and generate secure secrets:

```bash
cd scripts/langfuse
cp .env.example .env
```

Generate the required secrets using OpenSSL:

```bash
# Generate NEXTAUTH_SECRET (base64 encoded, 32 bytes)
openssl rand -base64 32

# Generate ENCRYPTION_KEY (hex encoded, 32 bytes = 64 hex characters)
openssl rand -hex 32

# Generate passwords for services (you can use any of these methods)
openssl rand -base64 20  # For PostgreSQL
openssl rand -base64 20  # For ClickHouse
openssl rand -base64 20  # For MinIO
openssl rand -base64 20  # For Redis
openssl rand -base64 12  # For SALT
```

Edit the `.env` file and replace all `CHANGEME_*` placeholders with the generated values.

**Important**: Make sure to update all three occurrences of the MinIO password:
- `MINIO_ROOT_PASSWORD`
- `LANGFUSE_S3_EVENT_UPLOAD_SECRET_ACCESS_KEY`
- `LANGFUSE_S3_MEDIA_UPLOAD_SECRET_ACCESS_KEY`
- `LANGFUSE_S3_BATCH_EXPORT_SECRET_ACCESS_KEY`

Also update the `DATABASE_URL` with the PostgreSQL password you generated.

### 2. Start Langfuse

Launch all services with Docker Compose:

```bash
docker compose up -d
```

### 3. Monitor Startup

Watch the logs to see when Langfuse is ready:

```bash
docker compose logs -f langfuse-web
```

Wait until you see a message indicating the service is ready (typically "✓ Ready").

### 4. Access Langfuse

Once the services are running, access the Langfuse web interface:

```
http://localhost:3000
```

On first access, you'll need to create an account. This will be your admin account for the local instance.

### 5. Create a Project and Get API Keys

1. Log into the Langfuse web UI at http://localhost:3000
2. Create a new project (or use the default project if auto-created)
3. Navigate to **Settings** → **API Keys**
4. Copy the **Public Key** and **Secret Key** - you'll need these for the chicken-api integration

## Integrating with Chicken API

To enable the chicken-api to send telemetry to your local Langfuse instance, set the following environment variables before starting the chicken-api:

```bash
export LANGFUSE_HOST=http://localhost:3000
export LANGFUSE_PUBLIC_KEY=pk-lf-... # Copy from Langfuse UI
export LANGFUSE_SECRET_KEY=sk-lf-... # Copy from Langfuse UI
```

If you're using a Koog agent that supports OpenTelemetry tracing, these environment variables will configure it to send traces to your local Langfuse instance.

## Common Operations

### Stop Langfuse

Stop all services but keep data:

```bash
docker compose down
```

### Restart Langfuse

```bash
docker compose restart
```

### View Logs

View logs for all services:

```bash
docker compose logs
```

View logs for a specific service:

```bash
docker compose logs langfuse-web
docker compose logs postgres
docker compose logs clickhouse
```

Follow logs in real-time:

```bash
docker compose logs -f
```

### Reset Everything

**Warning**: This will delete all data!

```bash
docker compose down -v
```

### Update Langfuse

Pull the latest images and restart:

```bash
docker compose pull
docker compose up -d
```

Or use the convenient one-liner:

```bash
docker compose up --pull always
```

## Architecture

The local Langfuse setup consists of the following services:

1. **langfuse-web** - Web UI and API server (port 3000)
2. **langfuse-worker** - Background worker for processing (port 3030)
3. **postgres** - PostgreSQL database for application data (port 5432)
4. **clickhouse** - ClickHouse database for analytics (ports 8123, 9000)
5. **minio** - S3-compatible object storage (ports 9090, 9091)
6. **redis** - Redis for caching and queues (port 6379)

## Troubleshooting

### Services Won't Start

Check that all required ports are available:

```bash
lsof -i :3000  # Check if port is in use
lsof -i :5432
lsof -i :9090
```

### Can't Access Web UI

1. Check that the web service is running:
   ```bash
   docker compose ps langfuse-web
   ```

2. Check logs for errors:
   ```bash
   docker compose logs langfuse-web
   ```

3. Verify the service is healthy:
   ```bash
   docker compose ps
   ```

### Data Persistence Issues

By default, all data is stored in Docker volumes. To inspect volumes:

```bash
docker volume ls | grep langfuse
```

To manually remove volumes:

```bash
docker volume rm scripts_langfuse_postgres_data
docker volume rm scripts_langfuse_clickhouse_data
docker volume rm scripts_langfuse_minio_data
```

### Reset to Factory Defaults

If you need to start fresh:

1. Stop and remove everything:
   ```bash
   docker compose down -v
   ```

2. Optionally, remove the .env file and regenerate secrets:
   ```bash
   rm .env
   cp .env.example .env
   # Generate new secrets as described above
   ```

3. Start again:
   ```bash
   docker compose up -d
   ```

## Security Notes

- The `.env` file contains sensitive credentials and is git-ignored by default
- Most services are bound to `127.0.0.1` (localhost) and are not accessible from other machines
- Only `langfuse-web` (port 3000) and `minio` (port 9090) are exposed to the network
- For production deployments, follow Langfuse's production deployment guide

## Additional Resources

- [Langfuse Documentation](https://langfuse.com/docs)
- [Langfuse GitHub Repository](https://github.com/langfuse/langfuse)
- [Langfuse Self-Hosting Guide](https://langfuse.com/docs/deployment/self-host)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

## File Structure

```
scripts/langfuse/
├── README.md                 # This file
├── docker-compose.yml        # Docker Compose configuration
├── .env.example              # Environment variable template
└── .env                      # Your local secrets (git-ignored)
```

## Version Information

This configuration is based on Langfuse's official docker-compose.yml from the main branch, compatible with Langfuse v3.x.
