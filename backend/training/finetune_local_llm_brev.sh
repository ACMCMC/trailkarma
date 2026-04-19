#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VENV_DIR="${VENV_DIR:-$HOME/trailkarma-bio-venv}"
MODEL_ID="${TRAILKARMA_LLM_MODEL_ID:-unsloth/Llama-3.2-1B-Instruct}"
DATASET_PATH="${TRAILKARMA_LLM_DATASET:-$ROOT_DIR/data/processed/llm/local_llm_train.jsonl}"
OUTPUT_DIR="${TRAILKARMA_LLM_OUTPUT_DIR:-$ROOT_DIR/backend/artifacts/local_llm}"
EXAMPLES="${TRAILKARMA_LLM_EXAMPLES:-4000}"
EPOCHS="${TRAILKARMA_LLM_EPOCHS:-2}"
BATCH_SIZE="${TRAILKARMA_LLM_BATCH_SIZE:-2}"
GRAD_ACCUM="${TRAILKARMA_LLM_GRAD_ACCUM:-8}"
LEARNING_RATE="${TRAILKARMA_LLM_LEARNING_RATE:-2e-4}"

if [[ ! -d "$VENV_DIR" ]]; then
  echo "Expected virtualenv at $VENV_DIR" >&2
  exit 1
fi

if [[ -z "${HUGGINGFACE_HUB_TOKEN:-}" && -z "${HF_TOKEN:-}" ]]; then
  echo "Set HUGGINGFACE_HUB_TOKEN or HF_TOKEN before running local LLM fine-tuning." >&2
  exit 1
fi

source "$VENV_DIR/bin/activate"
cd "$ROOT_DIR"

mkdir -p "$(dirname "$DATASET_PATH")" "$OUTPUT_DIR"

export TRANSFORMERS_NO_TF=1
export USE_TF=0

python backend/training/generate_local_llm_dataset.py \
  --artifact-dir backend/artifacts \
  --output-jsonl "$DATASET_PATH" \
  --examples "$EXAMPLES"

python backend/training/finetune_local_llm.py \
  --dataset "$DATASET_PATH" \
  --output-dir "$OUTPUT_DIR" \
  --model-id "$MODEL_ID" \
  --epochs "$EPOCHS" \
  --batch-size "$BATCH_SIZE" \
  --grad-accum "$GRAD_ACCUM" \
  --learning-rate "$LEARNING_RATE"
