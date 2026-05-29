# Migration Plan: Phoenix To Self-Hosted Langfuse

## Context

`chicken-api` is a Kotlin Spring Boot app using Koog agents for chicken fact and breed research workflows.

Current observability state:

- Koog agent traces are exported to Arize Phoenix through Koog `OpenTelemetry`.
- Phoenix config lives under `koog.tracing.phoenix.*`.
- Phoenix is started from `docker-compose.yml` as the `phoenix` service.
- The app sends OTLP HTTP traces to Phoenix in Docker.
- Phoenix receives spans, but the UI does not render Koog input/output well because Koog emits generic GenAI/Koog attributes, not Phoenix/OpenInference-native attributes.

Historical Phoenix trace data is not required. Do not migrate it. The target is Langfuse-only observability for new traces going forward.

## Goals

- Fully deprecate and remove Phoenix from this repository.
- Send all new Koog agent traces to self-hosted Langfuse.
- Use Koog's built-in Langfuse OpenTelemetry exporter/adapter.
- Keep tracing optional and controlled through Spring/application environment config.
- Ensure both Koog agents publish distinguishable traces in Langfuse.
- Keep the rollout simple: no dual-write and no historical Phoenix migration.
- Treat Koog Langfuse exporter API verification as a phase-0 gate before deleting Phoenix code.

## Non-Goals

- Do not migrate existing Phoenix traces.
- Do not preserve Phoenix Docker volumes or data.
- Do not keep Phoenix as a fallback exporter.
- Do not introduce vendor-neutral trace fanout unless a future need appears.
- Do not rework agent behavior, prompts, tools, scheduling, or Google Sheets persistence.

## Target Architecture

Runtime architecture after migration:

- `chicken-api` runs Koog agents.
- Each agent installs Koog `OpenTelemetry`.
- Koog `OpenTelemetry` uses the Langfuse exporter/adapter.
- Langfuse receives traces directly from the app using Langfuse host and API keys.
- Self-hosted Langfuse runs in Docker Compose with Langfuse web, worker, Postgres, ClickHouse, Redis, and object storage.

Trace tagging target:

- environment: active Spring profile, usually `prod` or `local`
- application: `chicken-api`
- agent tags: `chicken-facts-agent` and `breed-research-agent`

## Affected Files

Expected edits during implementation:

- `docker-compose.yml`: remove `phoenix`; add Langfuse stack or reference a Langfuse compose file; update `chicken-api` env.
- `src/main/resources/application.properties`: replace `koog.tracing.phoenix.*` with `koog.tracing.langfuse.*`.
- `src/main/kotlin/co/qwex/chickenapi/config/PhoenixTelemetryConfig.kt`: delete.
- `src/main/kotlin/co/qwex/chickenapi/config/PhoenixTracingProperties.kt`: delete.
- `src/main/kotlin/co/qwex/chickenapi/config/LangfuseTracingProperties.kt`: add.
- `src/main/kotlin/co/qwex/chickenapi/ai/KoogChickenFactsAgent.kt`: replace Phoenix exporter injection with Langfuse exporter config.
- `src/main/kotlin/co/qwex/chickenapi/ai/KoogBreedResearchAgent.kt`: replace Phoenix exporter injection with Langfuse exporter config.
- `build.gradle.kts`: remove Phoenix-only direct OTLP exporter dependencies if no longer needed after compilation.
- `.env` or deployment environment: replace Phoenix env vars with Langfuse env vars.
- `.env.production.example`: document required Langfuse app and stack env vars with placeholders.
- `README.md`: replace Phoenix setup instructions with Langfuse setup instructions.
- `docs/investigations/KOOG_SUBGRAPH_TRACE_HIERARCHY.md`: either update Phoenix-specific language for Langfuse or mark it as historical Phoenix behavior.

## Deployment Plan

Replace the current Phoenix service with self-hosted Langfuse.

Recommended approach:

- Keep `chicken-api` in the existing app compose file.
- Add the official Langfuse v3 self-host stack either directly in `docker-compose.yml` or in `docker-compose.langfuse.yml`. Use the official compose file as the source of truth; do not hand-roll a partial stack from this plan alone.
- Pin Langfuse image tags to a specific v3 patch version during cutover instead of floating on `:3`. Example target shape: `docker.io/langfuse/langfuse:<pinned-3.x>` and `docker.io/langfuse/langfuse-worker:<pinned-3.x>`.
- Preserve the official Langfuse healthchecks and `depends_on` health conditions so `langfuse-web` and `langfuse-worker` wait for Postgres, ClickHouse, Redis, and MinIO.
- Put all services on the existing Docker network so `chicken-api` can reach `http://langfuse-web:3000`. If Langfuse is in `docker-compose.langfuse.yml`, every Langfuse service still needs to join the same external `shared` network.
- Persist Langfuse state with named Docker volumes.
- Expose only `langfuse-web` publicly unless object storage console access is required.
- Bind Postgres, Redis, ClickHouse, and MinIO admin/console ports to localhost or do not publish them unless explicitly needed. MinIO's S3 API endpoint may need controlled external access only if Langfuse media uploads require browser-reachable signed URLs.

Langfuse services to add:

- `langfuse-web`, image `docker.io/langfuse/langfuse:<pinned-3.x>`
- `langfuse-worker`, image `docker.io/langfuse/langfuse-worker:<pinned-3.x>`
- `postgres`
- `clickhouse`
- `redis`
- object storage, commonly `minio` for Docker Compose self-hosting
- healthchecks for Postgres, ClickHouse, Redis, and MinIO
- `depends_on` health conditions for `langfuse-web` and `langfuse-worker`

Important deployment env vars:

```env
NEXTAUTH_URL=https://langfuse.example.com
NEXTAUTH_SECRET=<generate-secure-secret>
SALT=<generate-secure-secret>
ENCRYPTION_KEY=<openssl rand -hex 32>
TELEMETRY_ENABLED=false

POSTGRES_PASSWORD=<secure-password>
DATABASE_URL=postgresql://postgres:<secure-password>@postgres:5432/postgres

CLICKHOUSE_USER=clickhouse
CLICKHOUSE_PASSWORD=<secure-password>
CLICKHOUSE_MIGRATION_URL=clickhouse://clickhouse:9000
CLICKHOUSE_URL=http://clickhouse:8123
CLICKHOUSE_CLUSTER_ENABLED=false

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_AUTH=<secure-password>
REDIS_TLS_ENABLED=false

MINIO_ROOT_USER=minio
MINIO_ROOT_PASSWORD=<secure-password>
LANGFUSE_S3_EVENT_UPLOAD_BUCKET=langfuse
LANGFUSE_S3_EVENT_UPLOAD_REGION=auto
LANGFUSE_S3_EVENT_UPLOAD_ACCESS_KEY_ID=<minio-root-user-or-dedicated-user>
LANGFUSE_S3_EVENT_UPLOAD_SECRET_ACCESS_KEY=<minio-root-password-or-dedicated-password>
LANGFUSE_S3_EVENT_UPLOAD_ENDPOINT=http://minio:9000
LANGFUSE_S3_EVENT_UPLOAD_FORCE_PATH_STYLE=true
LANGFUSE_S3_EVENT_UPLOAD_PREFIX=events/
LANGFUSE_S3_MEDIA_UPLOAD_BUCKET=langfuse
LANGFUSE_S3_MEDIA_UPLOAD_REGION=auto
LANGFUSE_S3_MEDIA_UPLOAD_ACCESS_KEY_ID=<minio-root-user-or-dedicated-user>
LANGFUSE_S3_MEDIA_UPLOAD_SECRET_ACCESS_KEY=<minio-root-password-or-dedicated-password>
LANGFUSE_S3_MEDIA_UPLOAD_ENDPOINT=<browser-reachable-minio-api-endpoint>
LANGFUSE_S3_MEDIA_UPLOAD_FORCE_PATH_STYLE=true
LANGFUSE_S3_MEDIA_UPLOAD_PREFIX=media/
LANGFUSE_S3_BATCH_EXPORT_ENABLED=false

LANGFUSE_INIT_ORG_ID=chicken-api
LANGFUSE_INIT_ORG_NAME=Chicken API
LANGFUSE_INIT_PROJECT_ID=chicken-api
LANGFUSE_INIT_PROJECT_NAME=chicken-api
LANGFUSE_INIT_PROJECT_PUBLIC_KEY=<generated-or-provided-public-key>
LANGFUSE_INIT_PROJECT_SECRET_KEY=<generated-or-provided-secret-key>
LANGFUSE_INIT_USER_EMAIL=<admin-email>
LANGFUSE_INIT_USER_NAME=<admin-name>
LANGFUSE_INIT_USER_PASSWORD=<secure-password>
```

Apply the shared Langfuse runtime env block to both `langfuse-web` and `langfuse-worker`. `langfuse-web` additionally needs `NEXTAUTH_SECRET` and the optional `LANGFUSE_INIT_*` bootstrap variables. The MinIO service must create the `langfuse` bucket before serving requests; the official compose does this by creating `/data/langfuse` in the MinIO command. For local testing, the media upload endpoint is commonly `http://localhost:9090`; for production, use the externally reachable S3/MinIO API URL if media uploads are enabled.

If API keys are auto-generated instead of supplied through `LANGFUSE_INIT_PROJECT_PUBLIC_KEY` and `LANGFUSE_INIT_PROJECT_SECRET_KEY`, capture the generated project keys from the Langfuse UI after first startup and set the matching `LANGFUSE_PUBLIC_KEY` and `LANGFUSE_SECRET_KEY` for `chicken-api`.

`NEXTAUTH_URL` should be the browser/public URL for Langfuse, usually the reverse-proxied HTTPS URL in production. This is separate from the app's internal `LANGFUSE_HOST`, which should remain `http://langfuse-web:3000` when `chicken-api` talks to Langfuse over the Docker network.

`chicken-api` Docker environment after cutover:

```yaml
environment:
  SPRING_PROFILES_ACTIVE: prod
  GOOGLE_APPLICATION_CREDENTIALS: /run/secrets/google-credentials.json
  KOOG_OLLAMA_BASE_URL: ${KOOG_OLLAMA_BASE_URL:-${KOOG_AGENT_BASE_URL:-http://ollama:11434}}
  KOOG_OLLAMA_WEB_TOOLS_BASE_URL: ${KOOG_OLLAMA_WEB_TOOLS_BASE_URL:-https://ollama.com}
  KOOG_OLLAMA_API_KEY: ${KOOG_OLLAMA_API_KEY:-${KOOG_AGENT_API_KEY:-${OLLAMA_API_KEY:-}}}
  KOOG_AGENT_MODEL: ${KOOG_AGENT_MODEL:-gpt-oss:120b-cloud}
  KOOG_TRACING_LANGFUSE_ENABLED: ${KOOG_TRACING_LANGFUSE_ENABLED:-true}
  KOOG_TRACING_LANGFUSE_VERBOSE: ${KOOG_TRACING_LANGFUSE_VERBOSE:-true}
  LANGFUSE_HOST: ${LANGFUSE_HOST:-http://langfuse-web:3000}
  LANGFUSE_PUBLIC_KEY: ${LANGFUSE_PUBLIC_KEY:?Set LANGFUSE_PUBLIC_KEY}
  LANGFUSE_SECRET_KEY: ${LANGFUSE_SECRET_KEY:?Set LANGFUSE_SECRET_KEY}
```

Remove these Phoenix env vars:

```env
KOOG_TRACING_PHOENIX_ENABLED
KOOG_TRACING_PHOENIX_VERBOSE
PHOENIX_COLLECTOR_ENDPOINT
PHOENIX_PROJECT_NAME
PHOENIX_PORT
```

## Application Config Changes

Replace current Phoenix properties:

```properties
koog.tracing.phoenix.enabled=false
koog.tracing.phoenix.endpoint=${PHOENIX_COLLECTOR_ENDPOINT:http://localhost:6006/v1/traces}
koog.tracing.phoenix.project-name=${PHOENIX_PROJECT_NAME:chicken-api}
koog.tracing.phoenix.service-name=chicken-api-agents
koog.tracing.phoenix.service-version=0.0.1
koog.tracing.phoenix.deployment-environment=${SPRING_PROFILES_ACTIVE:local}
koog.tracing.phoenix.verbose=${KOOG_TRACING_PHOENIX_VERBOSE:false}
```

With Langfuse properties:

```properties
koog.tracing.langfuse.enabled=${KOOG_TRACING_LANGFUSE_ENABLED:false}
koog.tracing.langfuse.host=${LANGFUSE_HOST:http://localhost:3000}
koog.tracing.langfuse.public-key=${LANGFUSE_PUBLIC_KEY:}
koog.tracing.langfuse.secret-key=${LANGFUSE_SECRET_KEY:}
koog.tracing.langfuse.service-name=chicken-api-agents
koog.tracing.langfuse.service-version=0.0.1
koog.tracing.langfuse.deployment-environment=${SPRING_PROFILES_ACTIVE:local}
koog.tracing.langfuse.verbose=${KOOG_TRACING_LANGFUSE_VERBOSE:false}
```

Add a Langfuse properties class:

```kotlin
@ConfigurationProperties(prefix = "koog.tracing.langfuse")
data class LangfuseTracingProperties(
    val enabled: Boolean = false,
    val host: String = "http://localhost:3000",
    val publicKey: String = "",
    val secretKey: String = "",
    val serviceName: String = "chicken-api-agents",
    val serviceVersion: String = "0.0.1",
    val deploymentEnvironment: String = "local",
    val verbose: Boolean = false,
)
```

## Koog Code Changes

Current agents inject Phoenix-specific config and exporter beans:

```kotlin
private val phoenixTracingProperties: PhoenixTracingProperties,
private val phoenixSpanExporterProvider: ObjectProvider<OtlpHttpSpanExporter>,
private val phoenixResourceAttributesProvider: ObjectProvider<Map<String, Any>>,
```

Replace with:

```kotlin
private val langfuseTracingProperties: LangfuseTracingProperties,
```

Use Koog's Langfuse integration. Verify the exact package and function signature against Koog `1.0.0` before implementation.

Expected import shape:

```kotlin
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
```

Expected installation shape:

```kotlin
if (langfuseTracingProperties.enabled) {
    install(OpenTelemetry) {
        setServiceInfo(
            langfuseTracingProperties.serviceName,
            langfuseTracingProperties.serviceVersion,
        )
        setVerbose(langfuseTracingProperties.verbose)
        addLangfuseExporter(
            host = langfuseTracingProperties.host,
            publicKey = langfuseTracingProperties.publicKey,
            secretKey = langfuseTracingProperties.secretKey,
        )
    }
}
```

Before implementing this replacement, verify the exact package and function signature against the checked-in Koog dependency. If Koog `1.0.0` does not provide a compatible Langfuse exporter, stop and decide whether to upgrade Koog, keep the generic OpenTelemetry exporter against a Langfuse-supported endpoint, or defer the migration. Do not delete Phoenix classes until a minimal Langfuse exporter compile check passes.

Agent-specific attributes/tags should be added if the Koog Langfuse API exposes a supported way to do it. Keep these goals:

- `chicken-facts-agent` traces are distinguishable.
- `breed-research-agent` traces are distinguishable.
- environment is visible as `local`, `prod`, or the active Spring profile.

Preserve the existing `llm.application` resource attribute if the OpenTelemetry DSL still supports `addResourceAttributes()` alongside `addLangfuseExporter()`. If Langfuse does not surface that attribute usefully, use the nearest supported Langfuse field such as trace name, tags, metadata, or separate project keys. This is not optional because distinguishable agent traces are a stated migration goal.

Validate Langfuse credentials when tracing is enabled. If `enabled=true` and either public or secret key is blank, fail fast with a clear startup error or skip exporter installation with an error log. Prefer failing fast in Docker/prod so a misconfigured deployment does not silently run without traces.

Keep the existing Koog `Tracing` feature and `TraceFeatureMessageLogWriter` unless production logs become too noisy.

## Build Dependency Changes

Current dependencies include Koog OpenTelemetry plus direct OpenTelemetry OTLP exporter dependencies.

Plan:

- Keep `ai.koog:agents-features-opentelemetry`.
- Remove direct OTLP exporter dependencies if they were only needed for Phoenix.
- Run compilation after removal.
- Re-add only dependencies required by Koog Langfuse exporter if compilation or runtime proves they are not transitive.

## Verification Plan

Local verification:

1. Start Langfuse stack.
2. Create or initialize the `chicken-api` Langfuse project.
3. Export `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY`.
4. Start `chicken-api` with `KOOG_TRACING_LANGFUSE_ENABLED=true`.
5. Trigger chicken facts agent execution.
6. Trigger breed research agent execution.
7. Confirm traces appear in Langfuse with agent-specific identifiers.
8. Confirm no container or log references to Phoenix remain.
9. Confirm app healthcheck still passes.

Code verification:

```bash
JAVA_HOME=/root/.local/share/mise/installs/java/21.0.2 ./gradlew spotlessApply
JAVA_HOME=/root/.local/share/mise/installs/java/21.0.2 ./gradlew test --no-daemon
```

Manual Langfuse checks:

- Trace appears for `chicken-facts-agent`.
- Trace appears for `breed-research-agent`.
- Environment is set to `prod` or `local`.
- Tool calls and LLM calls are visible.
- Errors from failed agent runs appear as failed spans.
- Prompt/response visibility is acceptable for current data-sensitivity expectations.

Infrastructure verification:

- `langfuse-web` reaches `Ready` after initial migrations.
- `langfuse-worker` stays healthy and processes ingestion jobs.
- Postgres, ClickHouse, Redis, and MinIO healthchecks are green.
- MinIO contains the expected Langfuse bucket/prefixes.
- `chicken-api` can reach `http://langfuse-web:3000` on the shared Docker network.
- Only intended ports are reachable externally.

## Rollout Plan

1. Deploy Langfuse stack first without changing `chicken-api`.
2. Log in to Langfuse and confirm project/API keys.
3. Deploy app changes with `KOOG_TRACING_LANGFUSE_ENABLED=false`.
4. Confirm app starts with no Phoenix dependency.
5. Enable `KOOG_TRACING_LANGFUSE_ENABLED=true` in local or staging-like environment.
6. Trigger both Koog workflows and verify Langfuse traces.
7. Enable in production.
8. Monitor application logs and Langfuse ingestion.
9. Remove Phoenix container, image, and any unneeded Phoenix volumes after successful cutover.

No dual-write period is required because Phoenix historical data is not being migrated.

## Phoenix Removal Checklist

- [ ] Remove `phoenix` service from `docker-compose.yml`.
- [ ] Remove Phoenix env vars from `chicken-api` service.
- [ ] Delete `PhoenixTelemetryConfig.kt`.
- [ ] Delete `PhoenixTracingProperties.kt`.
- [ ] Remove Phoenix imports from Koog agents.
- [ ] Remove `ObjectProvider<OtlpHttpSpanExporter>` injection from Koog agents.
- [ ] Remove Phoenix property block from `application.properties`.
- [ ] Remove `PHOENIX_*` and `KOOG_TRACING_PHOENIX_*` from `.env` and deployment secrets.
- [ ] Remove direct OTLP exporter dependencies if no longer needed.
- [ ] Search repo for `Phoenix`, `phoenix`, `Arize`, `PHOENIX`, and `KOOG_TRACING_PHOENIX`.
- [ ] Stop and remove old Phoenix container.
- [ ] Delete Phoenix volumes only after confirming historical traces are intentionally discarded.

## Risks And Open Questions

- Langfuse self-hosting has more operational dependencies than Phoenix: Postgres, ClickHouse, Redis, and object storage.
- Langfuse API keys must be treated as secrets and not committed.
- Trace payloads may include prompts, responses, tool inputs, and tool outputs; confirm this is acceptable for the deployed Langfuse instance.
- Koog `addLangfuseExporter()` package and exact function signature should be verified against `koogVersion = "1.0.0"` during implementation.
- Retention and backup policy for Langfuse volumes needs an operational decision.
- Public exposure strategy for `langfuse-web` needs reverse proxy/TLS decisions.
- Whether to keep local `TraceFeatureMessageLogWriter` should be revisited if production logs become too noisy.
- Rollback is to set `KOOG_TRACING_LANGFUSE_ENABLED=false` and redeploy, accepting trace loss during the outage because Phoenix fallback and dual-write are explicitly out of scope.
- The Langfuse Docker Compose stack should be treated as single-node, non-HA infrastructure. For high availability or larger trace volume, use the Langfuse Kubernetes/Helm deployment path instead.

## Review Findings And Resolutions

This section resolves the issues found during plan review, including no-op decisions.

1. Koog `addLangfuseExporter` API is unverified.

Resolution: Not a no-op. Add a phase-0 implementation gate to verify the package and signature against Koog `1.0.0` before deleting Phoenix code. If the API is unavailable, stop and choose whether to upgrade Koog, use a generic OpenTelemetry path supported by Langfuse, or defer the migration.

2. Langfuse initialization and boot ordering are underspecified.

Resolution: Not a no-op. Use the official Langfuse v3 compose file as the source of truth, preserve service healthchecks, and keep health-gated `depends_on` conditions for `langfuse-web` and `langfuse-worker`.

3. MinIO/S3 configuration is missing.

Resolution: Not a no-op. Add explicit S3 event/media/batch-export variables and require bucket initialization. For the current Docker Compose target, MinIO remains required unless replacing it with another S3-compatible object store.

4. `langfuse-worker` environment is not detailed.

Resolution: Not a no-op. Apply the shared Langfuse runtime env block to both `langfuse-web` and `langfuse-worker`; only web gets `NEXTAUTH_SECRET` and `LANGFUSE_INIT_*` bootstrap variables in addition.

5. Empty Langfuse keys can be passed when tracing is enabled.

Resolution: Not a no-op. Add validation during app startup or exporter installation. In Docker/prod, prefer failing fast with a clear error if `KOOG_TRACING_LANGFUSE_ENABLED=true` and either key is blank.

6. Removing OpenTelemetry exporter dependencies may break Koog or Langfuse integration.

Resolution: Accepted implementation risk, not a plan blocker. Remove only after the Langfuse exporter compiles and tests pass. Re-add direct OpenTelemetry dependencies if Koog's Langfuse integration requires them at compile time or runtime.

7. Agent-specific tags may not port cleanly.

Resolution: Not a no-op. Preserve `chicken-facts-agent` and `breed-research-agent` identifiers through resource attributes, Langfuse tags, trace names, metadata, or separate project keys. Do not consider the migration complete until both agents are distinguishable in Langfuse.

8. Langfuse image tags are floating.

Resolution: Not a no-op. Pin `langfuse-web` and `langfuse-worker` to a specific v3 patch tag during cutover. Floating `:3` is acceptable only for local experimentation, not production deployment.

9. README and Phoenix-specific docs are not explicitly included in implementation checklist.

Resolution: Not a no-op. Add README and `KOOG_SUBGRAPH_TRACE_HIERARCHY.md` documentation updates to the checklist.

10. `.env.production.example` does not document the new Langfuse variables.

Resolution: Not a no-op. Update `.env.production.example` with placeholders for `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`, `KOOG_TRACING_LANGFUSE_ENABLED`, and the required Langfuse stack secrets if the same repo owns the stack compose.

11. Docker Compose default for tracing may be too aggressive.

Resolution: Intentional but needs staging discipline. App properties default Langfuse tracing to disabled. Docker Compose may default to enabled only after the Langfuse stack and matching API keys are part of the same deployment. During initial rollout, deploy with `KOOG_TRACING_LANGFUSE_ENABLED=false` and enable after verification.

12. `@ConstructorBinding` on the properties class.

Resolution: No-op unless binding fails. The app uses `@ConfigurationPropertiesScan`, and the existing Kotlin properties classes follow the same pattern. Add `@ConstructorBinding` only if Spring Boot binding/metadata generation fails during implementation.

## Implementation Checklist

### Phase 1: Langfuse Infrastructure

- [ ] Add Langfuse v3 compose services.
- [ ] Pin Langfuse web and worker images to a specific v3 patch tag.
- [ ] Preserve official Langfuse healthchecks and health-gated `depends_on` startup ordering.
- [ ] Put Langfuse services on the existing external `shared` Docker network.
- [ ] Add persistent volumes for Langfuse dependencies.
- [ ] Configure MinIO/S3 event upload, media upload, and batch export env vars.
- [ ] Ensure the Langfuse object storage bucket is created before Langfuse starts.
- [ ] Apply the shared Langfuse runtime env block to both web and worker services.
- [ ] Add secure Langfuse secrets to deployment environment.
- [ ] Start Langfuse and confirm UI access.
- [ ] Create or auto-initialize `chicken-api` project and API keys.
- [ ] Capture generated project API keys if keys are not supplied through `LANGFUSE_INIT_PROJECT_*`.

### Phase 2: Application Config

- [ ] Add `LangfuseTracingProperties`.
- [ ] Validate that public and secret keys are non-blank when Langfuse tracing is enabled.
- [ ] Replace Phoenix properties with Langfuse properties.
- [ ] Update Docker env vars for `chicken-api`.
- [ ] Update `.env.production.example` with Langfuse placeholders.
- [ ] Remove Phoenix config classes.

### Phase 3: Koog Agent Exporter Migration

- [ ] Verify the exact Koog Langfuse exporter package and function signature against Koog `1.0.0`.
- [ ] Compile a minimal Langfuse exporter change before deleting Phoenix files.
- [ ] Update `KoogChickenFactsAgent.kt` to use Koog's Langfuse exporter.
- [ ] Update `KoogBreedResearchAgent.kt` to use Koog's Langfuse exporter.
- [ ] Add agent-specific Langfuse trace attributes/tags/metadata/trace names and verify both agents are distinguishable.
- [ ] Remove Phoenix OTLP exporter providers from constructors.
- [ ] Clean up unused imports and dependencies.

### Phase 4: Verification

- [ ] Run `spotlessApply`.
- [ ] Run test suite with Java 21.
- [ ] Start app against local/self-hosted Langfuse.
- [ ] Trigger chicken facts agent.
- [ ] Trigger breed research agent.
- [ ] Confirm traces in Langfuse.
- [ ] Confirm Phoenix references are gone.
- [ ] Confirm `README.md` no longer instructs users to run Phoenix.
- [ ] Update or mark Phoenix-specific investigation docs as historical.

### Phase 5: Production Cutover

- [ ] Deploy Langfuse.
- [ ] Deploy app with Langfuse tracing disabled.
- [ ] Enable Langfuse tracing.
- [ ] Verify production traces.
- [ ] Remove Phoenix service and env vars.
- [ ] Stop/delete Phoenix resources after confirming discard of historical traces.
