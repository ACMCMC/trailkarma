#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACT_DIR="${TRAILKARMA_ARTIFACT_DIR:-$ROOT_DIR/backend/artifacts}"
OUTPUT_DIR="${TRAILKARMA_ANDROID_PACK_DIR:-$ROOT_DIR/android_app/app/src/main/assets/biodiversity}"
PROTOTYPE_DTYPE="${TRAILKARMA_ANDROID_PROTOTYPE_DTYPE:-float16}"
ALLOW_SELECT_TF_OPS="${TRAILKARMA_ANDROID_ALLOW_SELECT_TF_OPS:-0}"
SKIP_TFLITE_EXPORT="${TRAILKARMA_ANDROID_SKIP_TFLITE_EXPORT:-0}"

cd "$ROOT_DIR"

ARGS=(
  --artifact-dir "$ARTIFACT_DIR"
  --output-dir "$OUTPUT_DIR"
  --prototype-dtype "$PROTOTYPE_DTYPE"
)

if [[ "$ALLOW_SELECT_TF_OPS" == "1" ]]; then
  ARGS+=(--allow-select-tf-ops)
fi

if [[ "$SKIP_TFLITE_EXPORT" == "1" ]]; then
  ARGS+=(--skip-tflite-export)
fi

python backend/training/export_android_model_pack.py "${ARGS[@]}"

echo "Android biodiversity model pack written to: $OUTPUT_DIR"
