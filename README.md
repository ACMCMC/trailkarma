# TrailKarma

TrailKarma is an offline-first hiking app prototype built for DataHacks 2026. The current repository contains a working Android client, a hybrid Solana rewards layer, a TypeScript relay/rewards backend, a Python biodiversity backend, Databricks sync utilities, and a small React web demo.

## What Exists Today

The current hackathon build implements the following end-to-end pieces:

- Offline-first Android app built with Kotlin, Jetpack Compose, Room, WorkManager, BLE, CameraX, and OSMDroid.
- Local trail reporting for hazards, water, and species observations.
- Continuous local location logging plus online sync of reports, locations, trails, and relay packets to Databricks SQL.
- BLE packet exchange for reports and relay packets so nearby phones can carry data without internet.
- Hybrid Solana rewards flow with app-managed wallets, KARMA balances, badge claims, relay jobs, and KARMA tipping.
- Voice relay jobs that are signed on-device, carried offline, then opened and fulfilled through the backend when connectivity returns.
- End-to-Oracle Privacy: All relay payloads are ECIES-encrypted at the source so that intermediate mesh carriers cannot read the messages.
- Offline biodiversity audio capture with on-device inference, optional photo attachment, local ledgering, and backend mirroring.
- Android smoke-test scripts for emulator and physical-device loops.
- A React/Vite landing-page demo that shows the product concept with mock data.

This is an integrated prototype, not a finished production system. Some parts are live and connected, while a few of the original product goals are still represented as scaffolding, demo logic, or next-step work.

## Current Architecture

TrailKarma is intentionally hybrid:

- `android_app/`
  Android remains the source of truth for offline activity. It stores reports, biodiversity observations, relay jobs, relay inbox messages, location updates, and wallet state locally in Room.
- `backend/`
  Contains two services:
  - a TypeScript sponsor/attestor backend for Solana rewards and voice relay jobs
  - a Python FastAPI biodiversity backend for audio/photo ingestion and Databricks mirroring
- `solana/`
  Anchor program for reward state, uniqueness, relay-job settlement, KARMA issuance, badge ownership, and tipping.
- `web/`
  React/Vite demo site with mock trail-tracker and map views.
- `scripts/`
  Android emulator, install, smoke-test, and physical-device helper scripts.

## Implemented Android Features

- Local user profile with trail name, callback number, relay defaults, and trusted contacts.
- Compose navigation for map, report creation, report history, biodiversity capture, rewards, profile, relay hub, and status screens.
- Offline storage for:
  - trail reports
  - biodiversity contributions
  - relay packets
  - relay job intents
  - relay inbox replies
  - location updates
  - trails
- BLE foreground service plus GATT client/server exchange.
- On-device Solana wallet generation and signing.
- Rewards screen with KARMA balance, badge progress, collectible UI, and activity feed.
- Relay Hub screen for voice relay mission creation, sync, and inbox review.
- Biodiversity capture flow for 5-second audio recording, local inference, save-to-ledger, and photo attachment.

## Implemented Backend Features

### TypeScript rewards / voice-relay service

- Registers app users on-chain and tracks wallet state.
- Claims contribution rewards for hazard, water, and species reports.
- Opens and fulfills relay jobs on Solana Devnet.
- Prepares and submits signed KARMA tips.
- Stores audit and relay state in local SQLite.
- Integrates with ElevenLabs/Twilio for outbound relay calling when configured.
- Exposes mobile-facing endpoints under `/v1/...`.

### Python biodiversity service

- Accepts audio observations and photo attachments.
- Stores observation artifacts locally.
- Runs backend acoustic inference when using `/api/biodiversity/audio`.
- Accepts already-classified on-device observations through `/api/biodiversity/audio-sync`.
- Mirrors biodiversity observations into Databricks when credentials are configured.

## Solana Scope

The on-chain layer is intentionally narrow. Hiking workflows are not moved wholesale to Solana.

Solana is currently used for:

- user registration
- contribution reward receipts
- KARMA minting
- badge eligibility and badge ownership
- relay-job uniqueness and first-fulfiller settlement
- signed KARMA tipping

Off-chain systems still own:

- report capture
- biodiversity capture
- BLE exchange
- delayed message authoring
- outbound voice delivery
- Databricks analytics

## Repo Layout

- `android_app/`: Android app and tests
- `backend/`: TypeScript rewards backend and Python biodiversity API
- `solana/`: Anchor program and workspace
- `web/`: React/Vite demo
- `docs/`: feature-specific documentation
- `scripts/`: Android automation and helper scripts
- `data/`: sample trail and observation data used by setup scripts

## Running The Project

### 1. Databricks setup

Initialize the Databricks schema and demo data:

```bash
python setup_databricks.py
```

See [DATABRICKS_SETUP.md](DATABRICKS_SETUP.md) for details.

### 2. Rewards / voice backend

```bash
cd backend
npm install
npm run build
npm run dev
```

Required environment variables are documented in [backend/README.md](backend/README.md).

### 3. Biodiversity backend

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r backend/requirements.txt
uvicorn backend.app:app --reload --port 3000
```

If you run both backends locally, use different ports or separate sessions and point the Android build to the appropriate base URLs.

### 4. Android app

Preferred local loop:

```bash
scripts/android-sdk-bootstrap.sh
scripts/android-avd-create.sh
scripts/android-smoke-loop.sh
```

Useful scripts:

- `scripts/android-install-debug.sh`
- `scripts/android-ui-dump.sh`
- `scripts/android-physical-debug-loop.sh`
- `scripts/android-physical-capture.sh`

Android build configuration:

- `api.baseUrl` or `TRAILKARMA_API_BASE_URL` controls the biodiversity API base URL.
- `rewards.url` or `REWARDS_BASE_URL` controls the rewards backend base URL.
- Databricks values can come from `android_app/local.properties`, Gradle properties, or environment variables.

### 5. Web demo

```bash
cd web
npm install
npm run dev
```

The current web app is a concept/demo surface and uses mock data.

## Testing

Current Android instrumentation coverage includes:

- `SmokeNavigationTest`
  Verifies launch and navigation to Rewards, Profile, and Relay Hub.
- `BiodiversityFlowSmokeTest`
  Verifies biodiversity capture-screen rendering and the audio-record flow.
- `RewardsRepositoryIntegrationTest`
  Covers reward repository behavior against the current app/backend contract.

Preferred Android verification loop after app changes:

```bash
scripts/android-smoke-loop.sh
```

For physical-device testing:

```bash
SESSION_NAME=my-session scripts/android-physical-debug-loop.sh
```

## Documentation

- [ANDROID_DATABRICKS_SYNC.md](ANDROID_DATABRICKS_SYNC.md): Android offline sync, Databricks, BLE, and test loop notes
- [backend/README.md](backend/README.md): TypeScript rewards backend and Python biodiversity backend
- [docs/REWARDS.md](docs/REWARDS.md): hybrid Solana rewards architecture and current API surface
- [docs/bioacoustics.md](docs/bioacoustics.md): biodiversity feature status, model-pack flow, and current limitations
- [docs/RELAY_PRIVACY.md](docs/RELAY_PRIVACY.md): mesh relay privacy and non-interactive key exchange (NIKE) architecture
- [android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md](android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md): on-device model-pack layout
- [web/README.md](web/README.md): web demo status and scope

## What Is Still Missing From The Original Product Vision

The current implementation already covers the main offline-first Android flow, BLE carriage, Solana rewards, voice-relay settlement, and audio-based biodiversity capture. The largest remaining gaps are:

- photo-based species identification and verification, rather than only photo attachment/upload
- a real moderation or attestation pipeline for biodiversity verification instead of mostly local/demo collectible bookkeeping
- generalized backend-to-backend or partner-facing biodiversity export workflows for researchers
- more mature multi-phone BLE validation for full relay-carrier flows in the wild
- stronger trust / anti-abuse logic around what counts as a verified real-world action
- a web experience backed by live project data instead of mock/demo data

Those gaps are discussed in more detail in the feature docs and summarized in the final response for this task.
