# AGENTS.md

## Core Idea

A community-powered hiking app that socially rewards hikers for helping one another and contributing to the trail ecosystem. The app is offline-first, letting hikers log their location, hazards, water conditions, and wildlife sightings, relay that information phone-to-phone over BLE when there is no signal, and sync it to the cloud later. It also incentivizes altruistic contribution through Solana-based karma points and digital collectibles awarded for verified actions, whether that is reporting hazards for other travelers, sharing trail conditions, relaying delayed emergency or check-in messages, or contributing biodiversity data. For biodiversity monitoring, the app can run an on-device audio classification model to detect likely species from environmental sounds and pair those detections with location, while also allowing users to upload photos of species they encounter for identification and verification. Together, this creates a social, safety, and citizen-science network for hikers, where helping the community also generates meaningful data for biodiversity researchers.

## Current Build Direction

The current hackathon implementation uses a hybrid Solana architecture:

- Android remains offline-first and is still the source of reports, relay intents, BLE exchange, and local wallet signing
- `backend/` is the oracle / attestor / sponsor service and is responsible for Devnet transaction submission
- `solana/` contains the Anchor program that owns reward state, relay uniqueness, KARMA balances, and badge claims

Contributors should preserve these constraints:

- do not move normal hiking workflows fully on-chain
- do not require users to acquire SOL
- keep relay/message jobs as signed offline intents that are opened or fulfilled on-chain later
- treat backend attestations as the bridge from real-world events to on-chain settlement
- treat the current ElevenLabs voice relay as the preferred delayed-message transport instead of SMS
- keep voice delivery hybrid: offline intent locally, carrier sync over BLE, outbound call/reply capture in the backend, settlement on-chain
- enforce End-to-Oracle Privacy: sensitive relay payloads (recipient, message) must be ECIES-encrypted on-device for the backend; carriers should remain "blind" and anonymous

## Android Agent Loop
Prefer the emulator loop over manual IDE actions:
- `scripts/android-sdk-bootstrap.sh`: Install tools/images.
- `scripts/android-avd-create.sh`: Create AVD.
- `scripts/android-emulator-start.sh`: Start emulator.
- `scripts/android-install-debug.sh`: Build and install APK.
- `scripts/android-smoke-loop.sh`: Run full smoke test suite.
- `scripts/android-physical-debug-loop.sh`: Setup backend reverse-proxy and install on USB phone.
- `scripts/android-physical-capture.sh`: Screenshot and UI dump from physical device.

Current smoke test coverage:

- `android_app/app/src/androidTest/java/fyi/acmc/trailkarma/SmokeNavigationTest.kt`
- verifies launch and common navigation to:
  - Rewards
  - Profile
  - Relay Hub

Expected local environment:

- backend available at `REWARDS_URL`, defaulting to `http://10.0.2.2:3000` for emulator traffic to the host machine
- Android SDK rooted at the path in `android_app/local.properties` or `~/Library/Android/sdk`

Preferred workflow after Android edits:

1. `scripts/android-smoke-loop.sh`
2. if it fails, inspect:
   - `.artifacts/android-smoke/launch-logcat.txt`
   - `.artifacts/android-smoke/ui/window_dump.xml`
   - `.artifacts/android-smoke/ui/screen.png`

## Physical Device Loop
For USB-connected phones:
1. Start backend.
2. `SESSION_NAME=<label> scripts/android-physical-debug-loop.sh`
3. Reproduce flow manually.
4. `scripts/android-physical-capture.sh` for debugging artifacts.
5. `scripts/android-physical-logcat.sh stop`.
