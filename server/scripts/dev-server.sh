#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$SERVER_DIR/.env.local"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Copy .env.local.example and configure it." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DATABASE_URL:?DATABASE_URL must be configured}"
: "${GOOGLE_WEB_CLIENT_ID:?GOOGLE_WEB_CLIENT_ID must be configured}"
: "${JWT_SECRET:?JWT_SECRET must be configured}"

cd "$SERVER_DIR"
exec go run ./cmd/server
