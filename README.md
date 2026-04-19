# TrailKarma

A community-powered hiking app that socially rewards hikers for helping one another and contributing to the trail ecosystem. The app is offline-first, letting hikers log their location, hazards, water conditions, and wildlife sightings, relay that information phone-to-phone over BLE when there is no signal, and sync it to the cloud later. It also incentivizes altruistic contribution through Solana-based karma points and digital collectibles awarded for verified actions, whether that is reporting hazards for other travelers, sharing trail conditions, relaying delayed emergency or check-in messages, or contributing biodiversity data. For biodiversity monitoring, the app can run an on-device audio classification model to detect likely species from environmental sounds and pair those detections with location, while also allowing users to upload photos of species they encounter for identification and verification. Together, this creates a social, safety, and citizen-science network for hikers, where helping the community also generates meaningful data for biodiversity researchers.

TrailKarma is the DataHacks 2026 build of that idea. This repository contains the Android app, the hybrid Solana rewards layer, the rewards and relay backend, biodiversity ingestion and sync services, and the web demo used to explain the project.

- The Android app is the primary product surface and is designed to work offline first.
- BLE is used for phone-to-phone carriage of reports and relay packets when no internet is available.
- Solana is used narrowly for reward issuance, relay uniqueness and first-fulfiller settlement, KARMA balances, badge ownership, and tipping.
- Real-world events are still observed off-chain; the backend acts as the attestor and sponsor service that bridges those events to on-chain settlement.
- Voice relay is currently implemented as a hybrid ElevenLabs/Twilio outbound calling flow instead of SMS.

## Repository Structure

- `android_app/`: Kotlin/Compose app with Room, BLE, local wallet signing, rewards UI, relay hub, biodiversity capture, and offline sync.
- `backend/`: TypeScript rewards and voice-relay backend plus the biodiversity ingestion and sync service.
- `solana/`: Anchor program and workspace for KARMA, relay settlement, badge ownership, and tipping.
- `web/`: React/Vite product demo.
- `docs/`: feature-specific documentation.
- `scripts/`: Android emulator, smoke-test, install, and physical-device helper scripts.

## What Exists Today

The current hackathon build implements the following end-to-end pieces:

- Offline-first Android app built with Kotlin, Jetpack Compose, Room, WorkManager, BLE, CameraX, and OSMDroid.
- Local trail reporting for hazards, water, and species observations.
- Continuous local location logging plus online sync of reports, locations, trails, and relay packets to Databricks SQL.
- BLE packet exchange for reports and relay packets so nearby phones can carry data without internet.
- Hybrid Solana rewards flow with app-managed wallets, KARMA balances, badge claims, relay jobs, and KARMA tipping.
- Voice relay jobs that are signed on-device, carried offline, then opened and fulfilled through the backend when connectivity returns.
- Offline biodiversity audio capture with on-device inference, optional photo attachment, Gemini-backed photo verification on Android, local ledgering, and Databricks mirroring.
- Android smoke-test scripts for emulator and physical-device loops.
- A React/Vite landing-page demo that shows the product concept with mock data.

This is an integrated prototype, not a finished production system. Some parts are live and connected, while a few of the original product goals are still represented as scaffolding, demo logic, or next-step work.

## Current Architecture

TrailKarma is intentionally hybrid:

- `android_app/`
  Android remains the source of truth for offline activity. It stores reports, biodiversity observations, relay jobs, relay inbox messages, location updates, and wallet state locally in Room.
- `backend/`
  Contains:
  - a TypeScript sponsor, oracle, and attestor backend for Solana rewards and voice relay jobs
  - biodiversity ingestion and sync services for audio, photos, and Databricks mirroring
- `solana/`
  Anchor program for reward state, uniqueness, relay-job settlement, KARMA issuance, badge ownership, and tipping.
- `web/`
  React/Vite demo site for the project story and product walkthrough.
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
- Biodiversity capture flow for short audio recording, local inference, save-to-ledger, photo attachment, and Gemini photo verification against a typed species label.

## Implemented Backend Features

### TypeScript rewards and voice-relay service

- Registers app users on-chain and tracks wallet state.
- Claims contribution rewards for hazard, water, and species reports.
- Opens and fulfills relay jobs on Solana Devnet.
- Prepares and submits signed KARMA tips.
- Stores audit and relay state in local SQLite.
- Integrates with ElevenLabs/Twilio for outbound relay calling when configured.
- Exposes mobile-facing endpoints under `/v1/...`.

### Biodiversity ingestion and sync services

- Accept audio observations, photo attachments, and Gemini-backed image verification for typed species claims.
- Store observation artifacts locally.
- Support backend acoustic inference when using the biodiversity service directly.
- Accept already-classified on-device observations through `/api/biodiversity/audio-sync`.
- Mirror biodiversity observations into Databricks when credentials are configured.

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

## Running The Project

### 1. Databricks setup

Initialize the Databricks schema and demo data:

```bash
python setup_databricks.py
```

See [DATABRICKS_SETUP.md](DATABRICKS_SETUP.md) for details.

### 2. Rewards and voice backend

```bash
cd backend
npm install
npm run build
npm run dev
```

Required environment variables are documented in [backend/README.md](backend/README.md).

### 3. Android app

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

- Debug builds default to `http://10.0.2.2:3000` for the biodiversity backend and can be overridden with `api.debugBaseUrl`, `api.baseUrl`, or `TRAILKARMA_API_BASE_URL`.
- Debug rewards calls default to the debug biodiversity URL and can be overridden with `rewards.debugBaseUrl`, `rewards.url`, or `REWARDS_BASE_URL`.
- Release builds require real hosted URLs so downloaded APKs work without setup. Set `api.releaseBaseUrl` or `TRAILKARMA_PUBLIC_API_BASE_URL` for the biodiversity backend.
- Set `rewards.releaseBaseUrl` or `TRAILKARMA_PUBLIC_REWARDS_BASE_URL` for the rewards backend.
- Gemini values can come from `android_app/local.properties`, Gradle properties, or environment variables via `gemini.apiKey`, `gemini.model`, `GEMINI_API_KEY`, and `GEMINI_MODEL`.
- Databricks values can come from `android_app/local.properties`, Gradle properties, or environment variables.

### 4. Web demo

```bash
cd web
npm install
npm run dev
```

The current web app is still a concept and demo surface rather than a live production dashboard.

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
- [backend/README.md](backend/README.md): TypeScript rewards backend and biodiversity service setup
- [docs/REWARDS.md](docs/REWARDS.md): hybrid Solana rewards architecture and current API surface
- [docs/bioacoustics.md](docs/bioacoustics.md): biodiversity feature status, model-pack flow, and current limitations
- [docs/RELAY_PRIVACY.md](docs/RELAY_PRIVACY.md): mesh relay privacy and end-to-oracle encryption architecture
- [android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md](android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md): on-device model-pack layout
- [web/README.md](web/README.md): web demo status and scope

## Remaining Gaps

The current implementation already covers the main offline-first Android flow, BLE carriage, Solana rewards, voice-relay settlement, and audio-based biodiversity capture. The largest remaining gaps are:

- a stronger moderation or attestation pipeline for biodiversity verification instead of mostly local or demo collectible bookkeeping
- generalized researcher-facing biodiversity export workflows
- more mature multi-phone BLE validation for full relay-carrier flows in the wild
- stronger trust and anti-abuse logic around what counts as a verified real-world action
- a web experience backed by live project data instead of mock or demo data
