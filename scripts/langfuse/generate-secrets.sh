#!/bin/bash

# Script to generate secure secrets for Langfuse local deployment
# This script creates a .env file with randomly generated secrets

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
EXAMPLE_FILE="$SCRIPT_DIR/.env.example"

echo "🔐 Generating secrets for Langfuse..."
echo ""

# Check if .env already exists
if [ -f "$ENV_FILE" ]; then
    echo "⚠️  Warning: .env file already exists at $ENV_FILE"
    read -p "Do you want to overwrite it? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ Aborted. Existing .env file was not modified."
        exit 1
    fi
    echo "📝 Backing up existing .env to .env.backup"
    cp "$ENV_FILE" "$ENV_FILE.backup"
fi

# Check if openssl is available
if ! command -v openssl &> /dev/null; then
    echo "❌ Error: openssl is required but not installed."
    echo "   Please install openssl and try again."
    exit 1
fi

# Generate secrets
echo "🎲 Generating random secrets..."
NEXTAUTH_SECRET=$(openssl rand -base64 32)
ENCRYPTION_KEY=$(openssl rand -hex 32)
SALT=$(openssl rand -base64 12)
POSTGRES_PASSWORD=$(openssl rand -base64 20)
CLICKHOUSE_PASSWORD=$(openssl rand -base64 20)
MINIO_PASSWORD=$(openssl rand -base64 20)
REDIS_PASSWORD=$(openssl rand -base64 20)

# Create .env file from example
if [ ! -f "$EXAMPLE_FILE" ]; then
    echo "❌ Error: .env.example not found at $EXAMPLE_FILE"
    exit 1
fi

echo "📄 Creating .env file from template..."
cp "$EXAMPLE_FILE" "$ENV_FILE"

# Replace placeholders with generated secrets
echo "🔧 Substituting generated secrets..."
sed -i "s|NEXTAUTH_SECRET=CHANGEME_generate_with_openssl_rand_base64_32|NEXTAUTH_SECRET=$NEXTAUTH_SECRET|g" "$ENV_FILE"
sed -i "s|ENCRYPTION_KEY=CHANGEME_generate_with_openssl_rand_hex_32|ENCRYPTION_KEY=$ENCRYPTION_KEY|g" "$ENV_FILE"
sed -i "s|SALT=CHANGEME_random_salt_value|SALT=$SALT|g" "$ENV_FILE"
sed -i "s|POSTGRES_PASSWORD=CHANGEME_postgres_password|POSTGRES_PASSWORD=$POSTGRES_PASSWORD|g" "$ENV_FILE"
sed -i "s|CLICKHOUSE_PASSWORD=CHANGEME_clickhouse_password|CLICKHOUSE_PASSWORD=$CLICKHOUSE_PASSWORD|g" "$ENV_FILE"
sed -i "s|MINIO_ROOT_PASSWORD=CHANGEME_minio_password|MINIO_ROOT_PASSWORD=$MINIO_PASSWORD|g" "$ENV_FILE"
sed -i "s|REDIS_AUTH=CHANGEME_redis_password|REDIS_AUTH=$REDIS_PASSWORD|g" "$ENV_FILE"

# Update DATABASE_URL with the generated postgres password
sed -i "s|DATABASE_URL=postgresql://postgres:CHANGEME_postgres_password@postgres:5432/postgres|DATABASE_URL=postgresql://postgres:$POSTGRES_PASSWORD@postgres:5432/postgres|g" "$ENV_FILE"

# Update all MinIO secret references
sed -i "s|LANGFUSE_S3_EVENT_UPLOAD_SECRET_ACCESS_KEY=CHANGEME_minio_password|LANGFUSE_S3_EVENT_UPLOAD_SECRET_ACCESS_KEY=$MINIO_PASSWORD|g" "$ENV_FILE"
sed -i "s|LANGFUSE_S3_MEDIA_UPLOAD_SECRET_ACCESS_KEY=CHANGEME_minio_password|LANGFUSE_S3_MEDIA_UPLOAD_SECRET_ACCESS_KEY=$MINIO_PASSWORD|g" "$ENV_FILE"
sed -i "s|LANGFUSE_S3_BATCH_EXPORT_SECRET_ACCESS_KEY=CHANGEME_minio_password|LANGFUSE_S3_BATCH_EXPORT_SECRET_ACCESS_KEY=$MINIO_PASSWORD|g" "$ENV_FILE"

echo ""
echo "✅ Success! Generated .env file with secure secrets."
echo ""
echo "📋 Summary of generated secrets:"
echo "   NEXTAUTH_SECRET: ${NEXTAUTH_SECRET:0:20}... (truncated)"
echo "   ENCRYPTION_KEY: ${ENCRYPTION_KEY:0:20}... (truncated)"
echo "   SALT: ${SALT:0:10}... (truncated)"
echo "   POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:0:10}... (truncated)"
echo "   CLICKHOUSE_PASSWORD: ${CLICKHOUSE_PASSWORD:0:10}... (truncated)"
echo "   MINIO_PASSWORD: ${MINIO_PASSWORD:0:10}... (truncated)"
echo "   REDIS_PASSWORD: ${REDIS_PASSWORD:0:10}... (truncated)"
echo ""
echo "🔒 IMPORTANT: Keep your .env file secure and never commit it to version control!"
echo ""
echo "📦 Next steps:"
echo "   1. Review and customize your .env file if needed: $ENV_FILE"
echo "   2. Start Langfuse: docker compose up -d"
echo "   3. Monitor logs: docker compose logs -f langfuse-web"
echo "   4. Access UI: http://localhost:3000"
echo ""
