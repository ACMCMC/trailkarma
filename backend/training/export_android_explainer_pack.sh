#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE_MODEL="${TRAILKARMA_LLM_MODEL_ID:-unsloth/Llama-3.2-1B-Instruct}"
ADAPTER_DIR="${TRAILKARMA_LLM_ADAPTER_DIR:-$ROOT_DIR/backend/artifacts/local_llm/adapter}"
MERGED_DIR="${TRAILKARMA_LLM_MERGED_DIR:-$ROOT_DIR/backend/artifacts/local_llm/merged}"
OUTPUT_DIR="${TRAILKARMA_ANDROID_EXPLAINER_DIR:-$ROOT_DIR/android_app/app/src/main/assets/biodiversity_explainer}"
LLAMA_CPP_DIR="${TRAILKARMA_LLAMA_CPP_DIR:-$HOME/llama.cpp}"
GGUF_BASENAME="${TRAILKARMA_ANDROID_EXPLAINER_BASENAME:-trailkarma-explainer}"
QUANTIZATION="${TRAILKARMA_ANDROID_EXPLAINER_QUANT:-Q4_K_M}"

cd "$ROOT_DIR"

mkdir -p "$OUTPUT_DIR"

python backend/training/merge_local_llm_adapter.py \
  --base-model "$BASE_MODEL" \
  --adapter-path "$ADAPTER_DIR" \
  --output-dir "$MERGED_DIR"

F16_GGUF="$OUTPUT_DIR/${GGUF_BASENAME}.f16.gguf"
QUANT_GGUF="$OUTPUT_DIR/${GGUF_BASENAME}.${QUANTIZATION}.gguf"

if [[ ! -d "$LLAMA_CPP_DIR" ]]; then
  echo "llama.cpp not found at $LLAMA_CPP_DIR; merged model saved at $MERGED_DIR but GGUF export skipped." >&2
  exit 0
fi

PYTHON_BIN="${PYTHON_BIN:-python}"

"$PYTHON_BIN" "$LLAMA_CPP_DIR/convert_hf_to_gguf.py" \
  "$MERGED_DIR" \
  --outfile "$F16_GGUF"

if [[ -x "$LLAMA_CPP_DIR/build/bin/llama-quantize" ]]; then
  "$LLAMA_CPP_DIR/build/bin/llama-quantize" "$F16_GGUF" "$QUANT_GGUF" "$QUANTIZATION"
else
  echo "llama-quantize not found under $LLAMA_CPP_DIR/build/bin; leaving only F16 GGUF at $F16_GGUF" >&2
fi

cat > "$OUTPUT_DIR/explainer_manifest.json" <<EOF
{
  "base_model": "$BASE_MODEL",
  "adapter_dir": "$ADAPTER_DIR",
  "merged_dir": "$MERGED_DIR",
  "preferred_gguf": "$(basename "$QUANT_GGUF")",
  "fallback_gguf": "$(basename "$F16_GGUF")",
  "quantization": "$QUANTIZATION"
}
EOF

echo "Android explainer pack written to: $OUTPUT_DIR"
