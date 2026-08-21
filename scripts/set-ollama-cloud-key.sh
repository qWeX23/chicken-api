#!/usr/bin/env bash
set +x
set -euo pipefail

umask 077

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
SECRET_FILE="${ROOT_DIR}/secrets/ollama-api-key"
MODEL="gpt-oss:120b"
VALIDATE=true
DEPLOY=true
CUSTOM_ENV_FILE=false
CUSTOM_SECRET_FILE=false

usage() {
    cat <<'EOF'
Usage: scripts/set-ollama-cloud-key.sh [options]

Securely installs a dedicated Ollama Cloud API key and configures Chicken API
to use Ollama Cloud for generation while retaining local Ollama embeddings.

Options:
  --model MODEL         Direct Ollama Cloud model name (default: gpt-oss:120b)
  --env-file PATH       Test environment file (requires --no-deploy)
  --secret-file PATH    Test secret destination (requires --no-deploy and must be outside repo)
  --skip-validation     Do not validate the key and model against ollama.com
  --no-deploy           Update files without rebuilding or restarting Chicken API
  -h, --help            Show this help
EOF
}

require_option_value() {
    local option="$1"
    local value="${2:-}"
    if [[ -z "$value" ]]; then
        printf '%s requires a value.\n' "$option" >&2
        exit 2
    fi
}

while (($# > 0)); do
    case "$1" in
        --model)
            require_option_value "$1" "${2:-}"
            MODEL="$2"
            shift 2
            ;;
        --env-file)
            require_option_value "$1" "${2:-}"
            ENV_FILE="$2"
            CUSTOM_ENV_FILE=true
            shift 2
            ;;
        --secret-file)
            require_option_value "$1" "${2:-}"
            SECRET_FILE="$2"
            CUSTOM_SECRET_FILE=true
            shift 2
            ;;
        --skip-validation)
            VALIDATE=false
            shift
            ;;
        --no-deploy)
            DEPLOY=false
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            printf 'Unknown option: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ "$DEPLOY" == true && ("$CUSTOM_ENV_FILE" == true || "$CUSTOM_SECRET_FILE" == true) ]]; then
    printf 'Custom env or secret paths require --no-deploy. Production deployment uses the repository paths.\n' >&2
    exit 2
fi
if [[ "$DEPLOY" == false && "$CUSTOM_ENV_FILE" != "$CUSTOM_SECRET_FILE" ]]; then
    printf 'Test mode requires both --env-file and --secret-file together.\n' >&2
    exit 2
fi

if [[ ! -f "$ENV_FILE" ]]; then
    printf 'Environment file does not exist: %s\n' "$ENV_FILE" >&2
    exit 1
fi
if [[ ! "$MODEL" =~ ^[A-Za-z0-9._:/-]+$ ]]; then
    printf 'Invalid Ollama model name: %s\n' "$MODEL" >&2
    exit 1
fi

for command_name in mktemp install chmod mv cp flock realpath; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        printf 'Required command is unavailable: %s\n' "$command_name" >&2
        exit 1
    fi
done
if [[ "$VALIDATE" == true ]]; then
    for command_name in curl jq; do
        if ! command -v "$command_name" >/dev/null 2>&1; then
            printf 'Required validation command is unavailable: %s\n' "$command_name" >&2
            exit 1
        fi
    done
fi
if [[ "$DEPLOY" == true ]]; then
    for command_name in docker jq awk id env; do
        if ! command -v "$command_name" >/dev/null 2>&1; then
            printf 'Required deployment command is unavailable: %s\n' "$command_name" >&2
            exit 1
        fi
    done
fi

ENV_FILE="$(realpath -- "$ENV_FILE")"
SECRET_FILE="$(realpath -m -- "$SECRET_FILE")"
if [[ "$ENV_FILE" == "$SECRET_FILE" ]]; then
    printf 'Environment and secret files must use different paths.\n' >&2
    exit 1
fi
if [[ -d "$SECRET_FILE" ]]; then
    printf 'Secret destination is a directory: %s\n' "$SECRET_FILE" >&2
    exit 1
fi
if [[ "$CUSTOM_SECRET_FILE" == true && "$SECRET_FILE" == "$ROOT_DIR"/* ]]; then
    printf 'Custom test secrets must be outside the repository.\n' >&2
    exit 1
fi

SECRET_DIR="$(dirname -- "$SECRET_FILE")"
install -d -m 700 "$SECRET_DIR"
exec 9>"${SECRET_DIR}/.ollama-cloud-key.lock"
if ! flock -n 9; then
    printf 'Another Ollama Cloud key update is already running.\n' >&2
    exit 1
fi

read -r -s -p "Ollama Cloud API key: " api_key
printf '\n' >&2

if [[ -z "$api_key" || "$api_key" =~ [[:space:]] || "$api_key" =~ [[:cntrl:]] ]]; then
    printf 'Invalid API key format. Nothing was changed.\n' >&2
    unset api_key
    exit 1
fi

tmp_secret=""
tmp_env=""
curl_header=""
tags_file=""
probe_request=""
probe_response=""
backup_env=""
backup_secret=""
had_secret=false
commit_started=false
completed=false
deployment_started=false
previous_image_id=""
cleanup() {
    local exit_status=$?
    if [[ "$commit_started" == true && "$completed" != true ]]; then
        if [[ -n "$backup_env" && -f "$backup_env" ]]; then
            mv -f -- "$backup_env" "$ENV_FILE"
            backup_env=""
        fi
        if [[ "$had_secret" == true && -n "$backup_secret" && -f "$backup_secret" ]]; then
            mv -f -- "$backup_secret" "$SECRET_FILE"
            backup_secret=""
        elif [[ "$had_secret" != true ]]; then
            rm -f -- "$SECRET_FILE"
        fi
        printf 'Restored the previous credential files after setup failure.\n' >&2
        if [[ "$deployment_started" == true ]]; then
            printf 'Restoring the previous Chicken API deployment...\n' >&2
            if [[ -n "$previous_image_id" ]]; then
                if ! docker image tag "$previous_image_id" chicken-api:latest; then
                    printf 'Could not restore the previous Chicken API image tag.\n' >&2
                fi
            fi
            if ! run_compose "$ENV_FILE" up -d --no-build --force-recreate --wait --wait-timeout 180 chicken-api; then
                printf 'Automatic deployment rollback failed; inspect Chicken API before retrying.\n' >&2
            fi
        fi
    fi
    [[ -z "$tmp_secret" ]] || rm -f -- "$tmp_secret"
    [[ -z "$tmp_env" ]] || rm -f -- "$tmp_env"
    [[ -z "$curl_header" ]] || rm -f -- "$curl_header"
    [[ -z "$tags_file" ]] || rm -f -- "$tags_file"
    [[ -z "$probe_request" ]] || rm -f -- "$probe_request"
    [[ -z "$probe_response" ]] || rm -f -- "$probe_response"
    [[ -z "$backup_env" ]] || rm -f -- "$backup_env"
    [[ -z "$backup_secret" ]] || rm -f -- "$backup_secret"
    unset api_key
    return "$exit_status"
}
trap cleanup EXIT

run_compose() {
    local compose_env_file="$1"
    shift
    env \
        -u KOOG_OLLAMA_BASE_URL \
        -u KOOG_AGENT_BASE_URL \
        -u KOOG_OLLAMA_EMBEDDING_BASE_URL \
        -u KOOG_AGENT_MODEL \
        -u CHICKEN_API_OLLAMA_API_KEY \
        -u CHICKEN_API_OLLAMA_API_KEY_REQUIRED \
        -u KOOG_OLLAMA_API_KEY \
        -u KOOG_AGENT_API_KEY \
        -u OLLAMA_API_KEY \
        -u KOOG_OLLAMA_WEB_TOOLS_PROVIDER \
        -u CHICKEN_API_OLLAMA_WEB_TOOLS_BASE_URL \
        -u CHICKEN_API_OLLAMA_WEB_TOOLS_API_KEY \
        -u SEARXNG_BASE_URL \
        -u KOOG_AGENT_ENABLED \
        -u KOOG_BREED_RESEARCH_AGENT_ENABLED \
        -u KOOG_AGENT_SCHEDULER_ENABLED \
        -u KOOG_BREED_RESEARCH_AGENT_SCHEDULER_ENABLED \
        -u COMPOSE_FILE \
        -u COMPOSE_PATH_SEPARATOR \
        -u COMPOSE_PROJECT_NAME \
        docker compose \
        --file "$ROOT_DIR/docker-compose.yml" \
        --project-directory "$ROOT_DIR" \
        --project-name chicken-api \
        --env-file "$compose_env_file" \
        "$@"
}

if [[ "$VALIDATE" == true ]]; then
    curl_header="$(mktemp "${TMPDIR:-/tmp}/chicken-api-ollama-header.XXXXXX")"
    tags_file="$(mktemp "${TMPDIR:-/tmp}/chicken-api-ollama-tags.XXXXXX")"
    probe_request="$(mktemp "${TMPDIR:-/tmp}/chicken-api-ollama-request.XXXXXX")"
    probe_response="$(mktemp "${TMPDIR:-/tmp}/chicken-api-ollama-response.XXXXXX")"
    chmod 600 "$curl_header" "$tags_file" "$probe_request" "$probe_response"
    printf 'Authorization: Bearer %s\n' "$api_key" >"$curl_header"

    printf 'Validating Ollama Cloud key and model %s...\n' "$MODEL"
    curl \
        --disable \
        --fail \
        --silent \
        --show-error \
        --connect-timeout 10 \
        --max-time 60 \
        --header "@$curl_header" \
        --output "$tags_file" \
        https://ollama.com/api/tags

    if ! jq -e --arg model "$MODEL" '
        def normalized: sub(":latest$"; "");
        any(.models[]?; ((.name // .model // "") | normalized) == ($model | normalized))
    ' "$tags_file" >/dev/null; then
        printf 'Model %s is not available to this Ollama Cloud key.\n' "$MODEL" >&2
        printf 'Available models:\n' >&2
        jq -r '.models[]? | (.name // .model // empty)' "$tags_file" >&2
        exit 1
    fi

    jq -n --arg model "$MODEL" '{
        model: $model,
        messages: [{role: "user", content: "Reply with OK."}],
        stream: false,
        options: {num_predict: 2}
    }' >"$probe_request"
    curl \
        --disable \
        --fail \
        --silent \
        --show-error \
        --connect-timeout 10 \
        --max-time 120 \
        --header "@$curl_header" \
        --header 'Content-Type: application/json' \
        --data-binary "@$probe_request" \
        --output "$probe_response" \
        https://ollama.com/api/chat
    if ! jq -e '(.error? // "") == "" and .message.content? != null' "$probe_response" >/dev/null; then
        printf 'Ollama Cloud generation validation returned an unexpected response. Nothing was changed.\n' >&2
        exit 1
    fi

    rm -f -- "$curl_header" "$tags_file" "$probe_request" "$probe_response"
    curl_header=""
    tags_file=""
    probe_request=""
    probe_response=""
fi

declare -A updates=(
    [KOOG_OLLAMA_BASE_URL]="https://ollama.com"
    [KOOG_OLLAMA_EMBEDDING_BASE_URL]="http://ollama:11434"
    [KOOG_AGENT_MODEL]="$MODEL"
    [CHICKEN_API_OLLAMA_API_KEY]=""
    [CHICKEN_API_OLLAMA_API_KEY_REQUIRED]="true"
    [KOOG_OLLAMA_API_KEY]=""
    [KOOG_AGENT_API_KEY]=""
    [OLLAMA_API_KEY]=""
    [KOOG_OLLAMA_WEB_TOOLS_PROVIDER]="searxng"
    [SEARXNG_BASE_URL]="http://searxng:8080"
    [CHICKEN_API_OLLAMA_WEB_TOOLS_BASE_URL]=""
    [CHICKEN_API_OLLAMA_WEB_TOOLS_API_KEY]=""
    [KOOG_AGENT_ENABLED]="true"
    [KOOG_BREED_RESEARCH_AGENT_ENABLED]="true"
)
update_order=(
    KOOG_OLLAMA_BASE_URL
    KOOG_OLLAMA_EMBEDDING_BASE_URL
    KOOG_AGENT_MODEL
    CHICKEN_API_OLLAMA_API_KEY
    CHICKEN_API_OLLAMA_API_KEY_REQUIRED
    KOOG_OLLAMA_API_KEY
    KOOG_AGENT_API_KEY
    OLLAMA_API_KEY
    KOOG_OLLAMA_WEB_TOOLS_PROVIDER
    SEARXNG_BASE_URL
    CHICKEN_API_OLLAMA_WEB_TOOLS_BASE_URL
    CHICKEN_API_OLLAMA_WEB_TOOLS_API_KEY
    KOOG_AGENT_ENABLED
    KOOG_BREED_RESEARCH_AGENT_ENABLED
)
declare -A seen=()

tmp_env="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" =~ ^[[:space:]]*(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)= ]]; then
        key="${BASH_REMATCH[2]}"
        if [[ ${updates[$key]+present} ]]; then
            printf '%s=%s\n' "$key" "${updates[$key]}" >>"$tmp_env"
            seen["$key"]=true
            continue
        fi
    fi
    printf '%s\n' "$line" >>"$tmp_env"
done <"$ENV_FILE"

for key in "${update_order[@]}"; do
    if [[ ! ${seen[$key]+present} ]]; then
        printf '%s=%s\n' "$key" "${updates[$key]}" >>"$tmp_env"
    fi
done

if [[ "$DEPLOY" == true ]]; then
    configured_user="$(
        run_compose "$tmp_env" config --format json | jq -r '.services["chicken-api"].user'
    )"
    if [[ "$configured_user" != "$(id -u):$(id -g)" ]]; then
        printf 'Secret owner %s:%s does not match configured container user %s. Nothing was changed.\n' \
            "$(id -u)" "$(id -g)" "$configured_user" >&2
        exit 1
    fi
    previous_image_id="$(docker inspect chicken-api --format '{{.Image}}' 2>/dev/null || true)"
fi

tmp_secret="$(mktemp "${SECRET_DIR}/.ollama-api-key.XXXXXX")"
printf '%s\n' "$api_key" >"$tmp_secret"
chmod 600 "$tmp_secret"
chmod 600 "$tmp_env"
backup_env="$(mktemp "${ENV_FILE}.backup.XXXXXX")"
cp -p -- "$ENV_FILE" "$backup_env"
if [[ -f "$SECRET_FILE" ]]; then
    had_secret=true
    backup_secret="$(mktemp "${SECRET_DIR}/.ollama-api-key.backup.XXXXXX")"
    cp -p -- "$SECRET_FILE" "$backup_secret"
fi
commit_started=true
mv -f -- "$tmp_secret" "$SECRET_FILE"
tmp_secret=""
mv -f -- "$tmp_env" "$ENV_FILE"
tmp_env=""
unset api_key

if [[ "$DEPLOY" == false ]]; then
    completed=true
    printf 'Installed Ollama Cloud credential and configured model %s.\n' "$MODEL"
    printf 'Deployment skipped. Existing scheduler enable flags were preserved.\n'
    exit 0
fi

printf 'Credential files updated; deploying Chicken API with model %s...\n' "$MODEL"
run_compose "$ENV_FILE" config --quiet
deployment_started=true
run_compose "$ENV_FILE" up -d --build --force-recreate --wait --wait-timeout 180 chicken-api

deadline=$((SECONDS + 180))
while ((SECONDS < deadline)); do
    ready_count="$({
        docker exec chicken-api curl \
            --fail \
            --silent \
            --connect-timeout 3 \
            --max-time 10 \
            http://127.0.0.1:8081/actuator/prometheus || true
    } | awk '
        /^chicken_api_scheduled_task_ready\{/ && $2 == "1.0" { count += 1 }
        END { print count + 0 }
    ')"
    if [[ "$ready_count" == "2" ]]; then
        completed=true
        printf 'Installed Ollama Cloud credential and configured model %s.\n' "$MODEL"
        printf 'Chicken API is healthy and both Ollama Cloud agents are ready.\n'
        printf 'Scheduler enable flags were preserved. Follow docs/SCHEDULED_JOBS_RUNBOOK.md for a controlled run.\n'
        printf 'If this replaced an older key, revoke that key at https://ollama.com/settings/keys.\n'
        exit 0
    fi
    sleep 3
done

printf 'Chicken API started, but both agents did not become ready within 180 seconds.\n' >&2
printf 'Inspect with: docker logs --since 10m chicken-api\n' >&2
exit 1
