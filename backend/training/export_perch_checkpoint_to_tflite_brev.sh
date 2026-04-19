#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 3 ]; then
  cat <<'EOF'
Usage:
  export_perch_checkpoint_to_tflite_brev.sh <perch_repo> <checkpoint_workdir> <config_module> [output_path]

Example:
  export_perch_checkpoint_to_tflite_brev.sh \
    /home/ubuntu/perch \
    /home/ubuntu/perch_runs/my_model \
    chirp.configs.baseline \
    /home/ubuntu/bioacoustics-ai/android_app/model_exports/perch_checkpoint_encoder.tflite
EOF
  exit 1
fi

PERCH_REPO="$1"
CHECKPOINT_WORKDIR="$2"
CONFIG_MODULE="$3"
OUTPUT_PATH="${4:-/home/ubuntu/bioacoustics-ai/android_app/model_exports/perch_checkpoint_encoder.tflite}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

python "${PROJECT_ROOT}/backend/training/export_perch_checkpoint_to_tflite.py" \
  --perch-repo "${PERCH_REPO}" \
  --workdir "${CHECKPOINT_WORKDIR}" \
  --config-module "${CONFIG_MODULE}" \
  --output "${OUTPUT_PATH}"
