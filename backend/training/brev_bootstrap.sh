#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-$HOME/bioacoustics-ai-brev}"
mkdir -p "$ROOT_DIR"
cd "$ROOT_DIR"

if [ ! -d perch-hoplite ]; then
  git clone https://github.com/google-research/perch-hoplite.git
fi

if [ ! -d perch ]; then
  git clone https://github.com/google-research/perch.git
fi

if [ ! -d hackathon-repo ]; then
  echo "Clone your hackathon repo into $ROOT_DIR/hackathon-repo before running the rest of the pipeline."
fi

python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r hackathon-repo/backend/training/requirements.txt

cat <<'EOF'
Brev bootstrap complete.

Next steps:
1. Install Perch-Hoplite runtime deps in this instance.
2. Stage iNatSounds, xeno-canto, and negative clips into raw_audio/.
3. Run prepare_reference_audio.py to normalize 5s windows + metadata.
4. Use the Perch-Hoplite embedding notebook or batch script on the normalized windows.
5. Run train_open_world_head.py to build:
   - artifacts/prototype_bank.json
   - artifacts/metadata.json
6. Run generate_impulse_jsonl.py to create the Impulse fine-tuning dataset.
EOF
