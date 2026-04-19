# TrailKarma Biodiversity Backend

FastAPI service for the hackathon biodiversity audio feature.

## Endpoints

- `POST /api/biodiversity/audio`
  - multipart fields: `audio`, `lat`, `lon`, `timestamp`, `observation_id`
  - stores the clip locally
  - runs acoustic inference first
  - sends structured candidate JSON to a local post-processor model when configured
  - returns final label, confidence wording, explanation, and model metadata

- `POST /api/biodiversity/photo-link`
  - multipart fields: `observation_id` plus either `photo` or `photo_uri`
  - links a photo to the same event for later verification

- `POST /api/biodiversity/audio-sync`
  - multipart fields: raw `audio` plus local classification fields from the phone
  - stores an already-classified on-device observation without re-running backend inference

## Run locally

```bash
cd /path/to/bioacoustics-ai
python -m venv .venv
source .venv/bin/activate
pip install -r backend/requirements.txt
uvicorn backend.app:app --reload --port 3000
```

## Run on Brev GPU

On the Brev instance, serve the trained open-world acoustic pipeline with:

```bash
cd /home/ubuntu/bioacoustics-ai
bash backend/training/run_brev_backend.sh
```

From your laptop, forward the instance port so the Android emulator can reach it through `10.0.2.2`:

```bash
brev port-forward trailkarma-bio -p 3000:3000
```

## Local LLM configuration

Set these environment variables on the Brev instance to use the fine-tuned local model:

```bash
export TRAILKARMA_LLM_BACKEND=local
export TRAILKARMA_LLM_MODEL_ID=unsloth/Llama-3.2-1B-Instruct
export TRAILKARMA_LLM_ADAPTER=/path/to/lora-adapter
export TRAILKARMA_LLM_DEVICE=auto
```

If they are missing, the backend falls back to deterministic wording over the acoustic candidates.

To synthesize a Brev-side training set from the acoustic artifact inventory and fine-tune a local adapter:

```bash
cd /home/ubuntu/bioacoustics-ai
export HUGGINGFACE_HUB_TOKEN=...
bash backend/training/finetune_local_llm_brev.sh
```

After training, point the backend at the adapter:

```bash
export TRAILKARMA_LLM_BACKEND=local
export TRAILKARMA_LLM_MODEL_ID=unsloth/Llama-3.2-1B-Instruct
export TRAILKARMA_LLM_ADAPTER=/home/ubuntu/bioacoustics-ai/backend/artifacts/local_llm/adapter
export TRAILKARMA_LLM_DEVICE=auto
bash backend/training/run_brev_backend.sh
```

## Brev / training workflow

The `training/` folder contains the hackathon-side scripts:

1. `brev_bootstrap.sh` clones Perch-Hoplite, Perch, and the hackathon repo on a Brev GPU instance.
2. `brev_instance_setup.sh` installs ffmpeg, Python envs, backend deps, LLM fine-tuning deps, and the TensorFlow CUDA extras needed for Perch GPU inference.
3. `download_xeno_canto.py` pulls a small demo-oriented reference set from xeno-canto.
4. `build_negative_manifest.py` converts curated background-noise folders into manifest rows.
5. `prepare_reference_audio.py` normalizes reference audio to mono 16 kHz 5 second WAV windows.
6. `source backend/training/brev_gpu_env.sh` before any TensorFlow/Perch embedding or inference command on Brev so TensorFlow can resolve the pip-installed CUDA libraries.
7. Use the Perch-Hoplite embedding notebook or `embed_reference_audio.py` on those windows.
8. `train_open_world_head.py` builds prototype-bank metadata and a logistic-regression head artifact set.
9. `generate_impulse_jsonl.py` converts classifier outputs into JSONL conversations for local LLM fine-tuning.
10. `finetune_local_llm.py` trains a LoRA adapter for the local post-processor model.
11. `export_android_model_pack.py` packages the trained acoustic artifacts into the Android asset format and can attempt a TFLite export of the Perch encoder.
12. `export_perch_checkpoint_to_tflite.py` is the fallback path when the published Perch SavedModel cannot be converted directly. It expects a real Perch classifier training workdir checkpoint plus the matching config module and re-exports with non-XLA `jax2tf`.
13. `export_android_explainer_pack.sh` merges the local LoRA adapter and, if `llama.cpp` is present, converts it into a GGUF explainer pack for mobile experiments.

Current trained artifact footprint on Brev:

- `796` reference windows embedded with `perch_v2_gpu`
- `169` learned classes in the linear head
- `700` prototype-bank entries for retrieval

## Android pack export

After acoustic training finishes on Brev:

```bash
cd /home/ubuntu/bioacoustics-ai
python backend/training/export_android_model_pack.py \
  --artifact-dir backend/artifacts \
  --output-dir android_app/app/src/main/assets/biodiversity
```

If the Perch graph needs unsupported TFLite ops, rerun with:

```bash
python backend/training/export_android_model_pack.py \
  --artifact-dir backend/artifacts \
  --output-dir android_app/app/src/main/assets/biodiversity \
  --allow-select-tf-ops
```

If the published Perch SavedModel still fails because it contains `tf.XlaCallModule`, use a real Perch training checkpoint instead:

```bash
cd /home/ubuntu/bioacoustics-ai
python backend/training/export_perch_checkpoint_to_tflite.py \
  --perch-repo /home/ubuntu/perch \
  --workdir /path/to/perch_workdir \
  --config-module chirp.configs.baseline \
  --output android_app/model_exports/perch_checkpoint_encoder.tflite
```

Notes:

- This path requires the original Perch JAX checkpoint workdir, not just the published Kaggle SavedModel.
- The exporter forces CPU, disables XLA in `jax2tf`, and leaves `jit_compile` off by default to avoid recreating an XLA-backed graph.
- Once the `.tflite` file is produced, copy it into the Android biodiversity model pack as `perch_encoder.tflite`.

To package the optional local explainer into GGUF, first train the local adapter and then:

```bash
cd /home/ubuntu/bioacoustics-ai
export TRAILKARMA_LLM_MODEL_ID=unsloth/Llama-3.2-1B-Instruct
export TRAILKARMA_LLM_ADAPTER_DIR=/home/ubuntu/bioacoustics-ai/backend/artifacts/local_llm/adapter
export TRAILKARMA_LLAMA_CPP_DIR=$HOME/llama.cpp
bash backend/training/export_android_explainer_pack.sh
```
