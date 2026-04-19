# Backend Services

The `backend/` folder currently contains two separate services:

- a TypeScript rewards / relay backend used by the Android app for Solana-backed flows
- a Python FastAPI biodiversity backend used for audio and photo ingestion

They serve different purposes and can be run independently.

## 1. TypeScript Rewards And Voice-Relay Backend

This service is the sponsor / attestor bridge between the Android app and the Anchor program in `solana/`.

### What It Does

- registers app users on-chain
- returns wallet state and reward activity
- claims contribution rewards for hazard, water, and species trail reports
- opens and fulfills relay jobs
- prepares and submits signed KARMA tips
- persists audit state in SQLite
- optionally opens outbound voice relay calls through ElevenLabs/Twilio
- stores relay replies for later delivery back through the mesh

### Main Entrypoints

- [src/server.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/server.ts)
- [src/solana/client.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/solana/client.ts)
- [src/voiceRelay.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/voiceRelay.ts)
- [src/config.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/config.ts)
- [src/db.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/db.ts)

### Required Environment Variables

- `PROGRAM_ID`
- `SPONSOR_SECRET_KEY`
- `ATTESTOR_SECRET_KEY`

### Common Optional Environment Variables

- `PORT`
- `SOLANA_RPC_URL`
- `ANCHOR_IDL_PATH`
- `SQLITE_PATH`
- `ELEVENLABS_API_KEY`
- `ELEVENLABS_AGENT_ID`
- `ELEVENLABS_PHONE_NUMBER_ID`
- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_PHONE_NUMBER`
- `PUBLIC_BASE_URL`

### Run Locally

```bash
cd /Users/suraj/Desktop/dhacks/datahacks26/backend
npm install
npm run build
npm run dev
```

The dev server listens on `PORT`, defaulting to `3000`.

### API Surface

- `GET /health`
- `POST /v1/users/register`
- `POST /v1/profile/upsert`
- `GET /v1/profile/:appUserId`
- `GET /v1/users/:appUserId/wallet`
- `GET /v1/users/:appUserId/rewards/activity`
- `POST /v1/contributions/claim`
- `POST /v1/relay-jobs/open`
- `POST /v1/relay-jobs/fulfill`
- `GET /v1/relay-jobs/:jobIdHex`
- `POST /v1/voice-relay/jobs/open`
- `GET /v1/voice-relay/jobs/:appUserId`
- `GET /v1/voice-relay/inbox/:appUserId`
- `GET /v1/voice-relay/mesh/:appUserId`
- `POST /v1/voice-relay/inbox/:replyId/ack`
- `POST /v1/karma/tip/prepare`
- `POST /v1/karma/tip/submit`

### Current Notes

- The service is wired to Solana Devnet by default.
- Voice relay is functional only when the ElevenLabs/Twilio credentials are present.
- The backend expects the Anchor IDL to exist at `solana/target/idl/trail_karma_rewards.json` unless overridden.
- SQLite is used as a local operational store, not as the source of truth for hiking reports.

## 2. Python Biodiversity Backend

This service powers the biodiversity ingestion path. It can run full backend inference, or it can accept already-classified on-device observations from Android.

### What It Does

- receives audio clips and photo attachments
- stores observation artifacts locally
- runs acoustic inference through `AcousticPipeline`
- verifies typed photo species claims against submitted images with Gemini
- optionally post-processes inference results with a local model
- mirrors observations to Databricks when credentials are configured
- accepts already-classified observations from Android via `/api/biodiversity/audio-sync`

### Main Entrypoints

- [app.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/app.py)
- [acoustic.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/acoustic.py)
- [postprocess.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/postprocess.py)
- [storage.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/storage.py)
- [databricks_mirror.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/databricks_mirror.py)

### Endpoints

- `GET /health`
- `POST /api/biodiversity/audio`
- `POST /api/biodiversity/photo-link`
- `POST /api/biodiversity/photo-verify`
- `POST /api/biodiversity/audio-sync`

### Run Locally

```bash
cd /Users/suraj/Desktop/dhacks/datahacks26
python -m venv .venv
source .venv/bin/activate
pip install -r backend/requirements.txt
uvicorn backend.app:app --reload --port 3000
```

To enable photo verification, configure Gemini before starting the backend:

```bash
export GEMINI_API_KEY=...
export GEMINI_MODEL=gemini-2.5-flash
```

The backend also loads `backend/.env`, so you can copy [backend/.env.example](.env.example) and fill it in locally.

### Optional Databricks Mirroring

Set these if you want biodiversity observations mirrored into Databricks:

- `DATABRICKS_HOST`
- `DATABRICKS_TOKEN`
- `DATABRICKS_WAREHOUSE`

If they are unset, the backend still works and simply skips mirroring.

### Local LLM Post-Processor

The backend can optionally use a local post-processor model. The current code paths still work without it.

Relevant environment variables:

- `TRAILKARMA_LLM_BACKEND`
- `TRAILKARMA_LLM_MODEL_ID`
- `TRAILKARMA_LLM_ADAPTER`
- `TRAILKARMA_LLM_DEVICE`

If these are missing, the service falls back to deterministic wording.

## Training And Model-Pack Export

The `training/` folder contains the Brev-oriented scripts used to build and export the biodiversity model artifacts that end up in `android_app/app/src/main/assets/biodiversity/`.

Important scripts include:

- `training/brev_bootstrap.sh`
- `training/brev_instance_setup.sh`
- `training/train_open_world_head.py`
- `training/finetune_local_llm.py`
- `training/export_android_model_pack.py`
- `training/export_perch_checkpoint_to_tflite.py`
- `training/export_android_explainer_pack.sh`

See [../docs/bioacoustics.md](../docs/bioacoustics.md) and [../android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md](../android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md) for the Android-side expectations.

## Practical Local Setup

If you are working on Android end-to-end, the most useful split is:

- run the TypeScript rewards backend on one port
- run the Python biodiversity backend on another port
- point:
  - `api.baseUrl` at the biodiversity service
  - `rewards.url` at the TypeScript service

For physical-device debugging, the provided scripts expect the backend to be reachable via `http://127.0.0.1:3000` through `adb reverse`, so adjust ports accordingly or run one service at a time during the loop.
