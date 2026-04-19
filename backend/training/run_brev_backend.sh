#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VENV_DIR="${VENV_DIR:-$HOME/trailkarma-bio-venv}"
HOST="${TRAILKARMA_BACKEND_HOST:-0.0.0.0}"
PORT="${TRAILKARMA_BACKEND_PORT:-3000}"
LOG_LEVEL="${TRAILKARMA_BACKEND_LOG_LEVEL:-info}"

if [[ ! -d "$VENV_DIR" ]]; then
  echo "Expected virtualenv at $VENV_DIR" >&2
  exit 1
fi

source "$VENV_DIR/bin/activate"
cd "$ROOT_DIR"
source backend/training/brev_gpu_env.sh

exec uvicorn backend.app:app --host "$HOST" --port "$PORT" --log-level "$LOG_LEVEL"
