## Overview
TrailKarma is an offline-first hiking app built for DataHacks 2026. It features:
- **Offline-first Android App**: Reports, locations, and biodiversity capture (Room + WorkManager).
- **Mesh Relay**: BLE-based phone-to-phone data carriage for offline signal areas.
- **Privacy**: End-to-Oracle encryption (X25519) to keep messages private from mesh carriers.
- **Solana Rewards**: KARMA rewards for hazards, wildlife reports, and relay fulfillment.
- **Voice Relay**: Hybrid ElevenLabs/Twilio outbound calling from signed offline intents.
- **Biodiversity**: On-device audio classification and Databricks mirroring.

## Repository Structure
- `android_app/`: Kotlin/Compose app with Room, BLE, and Solana wallet.
- `backend/`: TypeScript rewards/relay service & Python biodiversity API.
- `solana/`: Anchor program for rewards, relay settlement, and KARMA.
- `web/`: React/Vite product demo.
- `scripts/`: Android automation (emulator, smoke tests, physical device).
- `docs/`: Feature-specific documentation.

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
