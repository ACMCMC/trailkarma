#!/usr/bin/env bash
set -euo pipefail

if command -v apt-get >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y ffmpeg git python3-venv python3-dev build-essential
fi

python3 -m venv ~/trailkarma-bio-venv
source ~/trailkarma-bio-venv/bin/activate
python -m pip install --upgrade pip

REPO_DIR="${1:-$HOME/bioacoustics-ai}"
cd "$REPO_DIR"

python -m pip install -r backend/requirements.txt
python -m pip install -r backend/training/requirements.txt
python -m pip install -r backend/training/requirements_llm.txt
python -m pip install "tensorflow[and-cuda]==2.21.0"

cat <<'EOF'
Brev instance base environment is ready.

Recommended next commands:
  source backend/training/brev_gpu_env.sh
  python backend/training/download_xeno_canto.py --species-file backend/training/demo_species.txt --output-dir data/raw/xeno_canto --per-species 20
  python backend/training/prepare_reference_audio.py --input-manifest data/reference_manifest.csv --output-dir data/processed
  # Run Hoplite embeddings
  python backend/training/train_open_world_head.py --embeddings data/processed/hoplite_embeddings.npy --manifest data/processed/normalized_manifest.csv --output-dir backend/artifacts
  python backend/training/generate_impulse_jsonl.py --input-jsonl data/candidates.jsonl --output-jsonl data/llm_train.jsonl
  python backend/training/finetune_local_llm.py --dataset data/llm_train.jsonl --output-dir data/llm_adapter
EOF
