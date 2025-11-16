# Langfuse Setup Checklist

Use this checklist to verify your Langfuse local setup is complete and working correctly.

## Prerequisites ✓

- [ ] Git installed (`git --version`)
- [ ] Docker installed (`docker --version`)
- [ ] Docker Compose v2 installed (`docker compose version`)
- [ ] OpenSSL installed (`openssl version`)

### Port Availability ✓

Verify these ports are available (not in use):

- [ ] Port 3000 (Langfuse web UI)
- [ ] Port 3030 (Langfuse worker - localhost only)
- [ ] Port 5432 (PostgreSQL - localhost only)
- [ ] Port 6379 (Redis - localhost only)
- [ ] Port 8123 (ClickHouse HTTP - localhost only)
- [ ] Port 9000 (ClickHouse native - localhost only)
- [ ] Port 9090 (MinIO API)
- [ ] Port 9091 (MinIO console - localhost only)

Check with: `lsof -i :<port>` or `netstat -tuln | grep <port>`

## Setup Steps ✓

- [ ] 1. Navigate to `scripts/langfuse` directory
- [ ] 2. Run `./generate-secrets.sh` to create `.env` file
- [ ] 3. Verify `.env` file has no `CHANGEME` placeholders
- [ ] 4. Review `.env` file and customize if needed
- [ ] 5. Run `docker compose config --quiet` to validate configuration
- [ ] 6. Run `docker compose up -d` to start all services
- [ ] 7. Monitor logs with `docker compose logs -f langfuse-web`
- [ ] 8. Wait for "Ready" message in logs

## Verification ✓

### Services Running

Check all services are healthy:

```bash
docker compose ps
```

All services should show status as "Up" or "healthy".

- [ ] langfuse-web (Up)
- [ ] langfuse-worker (Up)
- [ ] postgres (Up, healthy)
- [ ] clickhouse (Up, healthy)
- [ ] minio (Up, healthy)
- [ ] redis (Up, healthy)

### Web UI Access

- [ ] Open http://localhost:3000 in browser
- [ ] Web UI loads successfully
- [ ] Can create account or sign in
- [ ] Dashboard is accessible

### MinIO Console (Optional)

- [ ] Open http://localhost:9091 in browser
- [ ] Can log in with MINIO_ROOT_USER and MINIO_ROOT_PASSWORD from `.env`
- [ ] See `langfuse` bucket

## Chicken API Integration ✓

- [ ] Create project in Langfuse UI
- [ ] Generate API keys (Settings → API Keys)
- [ ] Copy Public Key (pk-lf-...)
- [ ] Copy Secret Key (sk-lf-...)
- [ ] Set `LANGFUSE_HOST=http://localhost:3000`
- [ ] Set `LANGFUSE_PUBLIC_KEY=<your-public-key>`
- [ ] Set `LANGFUSE_SECRET_KEY=<your-secret-key>`
- [ ] Start chicken-api application
- [ ] Verify traces appear in Langfuse UI

## Troubleshooting ✓

If you encounter issues:

1. **Check logs for errors:**
   ```bash
   docker compose logs
   ```

2. **Restart services:**
   ```bash
   docker compose restart
   ```

3. **Full reset (deletes all data):**
   ```bash
   docker compose down -v
   ./generate-secrets.sh
   docker compose up -d
   ```

4. **Check service health:**
   ```bash
   docker compose ps
   docker inspect <container-name>
   ```

## Common Issues

### Port Already in Use

If you get "port already in use" errors:

```bash
# Find process using the port
lsof -i :3000

# Stop the process or change the port mapping in docker-compose.yml
```

### Services Not Starting

Check Docker resources:
- Ensure Docker has enough memory (recommend 4GB+)
- Check disk space
- Restart Docker Desktop/Engine

### Cannot Access Web UI

1. Check if service is running: `docker compose ps langfuse-web`
2. Check logs: `docker compose logs langfuse-web`
3. Try: `curl http://localhost:3000`
4. Verify firewall isn't blocking the port

### Database Connection Errors

1. Wait longer for services to be healthy (check with `docker compose ps`)
2. Check PostgreSQL logs: `docker compose logs postgres`
3. Verify credentials in `.env` match between services

## Cleanup ✓

To remove Langfuse completely:

- [ ] Stop services: `docker compose down`
- [ ] Remove volumes: `docker compose down -v`
- [ ] Remove .env file: `rm .env` (optional)

## Reference

- Main Setup Guide: [README.md](README.md)
- Integration Guide: [CHICKEN_API_INTEGRATION.md](CHICKEN_API_INTEGRATION.md)
- Langfuse Docs: https://langfuse.com/docs
