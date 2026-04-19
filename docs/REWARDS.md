# Rewards Layer

This repository implements a hybrid Solana rewards system for TrailKarma. The goal is to keep hiking workflows offline-first and non-crypto-native while still using Solana for the parts that benefit from on-chain uniqueness and ownership.

## Scope

The rewards stack spans three parts of the repo:

- `android_app/`
  Local wallet generation, payload signing, relay intent creation, rewards UI, badge UI, and sync triggers.
- `backend/`
  Sponsor / attestor service that verifies inputs, submits Devnet transactions, manages relay-job flow, and exposes mobile-friendly HTTP endpoints.
- `solana/`
  Anchor program for user profiles, contribution receipts, relay jobs, badge claims, and KARMA tipping.

## What Is On-Chain vs Off-Chain

Off-chain:

- hazard, water, and biodiversity capture
- BLE report exchange
- delayed-message authoring
- relay packet carriage
- outbound voice delivery
- Databricks analytics and report storage

On-chain:

- user registration
- contribution reward uniqueness
- relay-job uniqueness and first-fulfiller settlement
- KARMA minting
- badge eligibility / ownership
- signed KARMA tipping

## Solana Program

Primary program file:

- [solana/programs/trail_karma_rewards/src/lib.rs](/Users/suraj/Desktop/dhacks/datahacks26/solana/programs/trail_karma_rewards/src/lib.rs)

Current Devnet program ID:

- `GmRtJ6ghkm26SfdTDXTFDoX4TzHgQstCjwPPs5EpdZGS`

Current config PDA:

- `CFjam3wWb33mhJY8GjXum5gyAihnkVzMfwXHdScyk489`

### PDA Types

- `Config`
- `UserProfile`
- `ContributionReceipt`
- `RelayJob`
- `BadgeClaim`
- `TipReceipt`

### Implemented Instructions

- `initialize_config`
- `register_user`
- `create_relay_job_from_intent`
- `create_attested_relay_job`
- `fulfill_relay_job`
- `claim_contribution_reward`
- `claim_badge`
- `tip_karma`

## Current Assets On Devnet

KARMA mint:

- `BwXfoGqqbyW6Scaq5oqXhjacLq4gKrjK3jwQRg2SPzQT`

Badge mints:

- `Trail Scout`: `EWDtuZ5Jk1fGq99KkMnowmba9tTNXa6QUym876tfccEb`
- `Relay Ranger`: `FVwQZK7TAXJx87NZxjVK7PFkmGosoJ6oQRYXUdWL7nh5`
- `Species Spotter`: `6uFsGeEznkfzYn59smAzCFw5u4bL8qJBNAwNeJ2nE673`
- `Water Guardian`: `Cz62c2HCaYvhnDHCAF3nNu37WqVnF9QaqHWD8aysa2h`
- `Hazard Herald`: `9kmGY9ktyGehY8CB7mUYrTW1b72QQPQTJ1DZcS1gycX`

Token model:

- KARMA is a fungible transferable token.
- Badges are non-transferable Token-2022 achievement assets.

## Reward Rules In The Current Build

Configured reward amounts:

- hazard report: `10 KARMA`
- water report: `10 KARMA`
- species report: `8 KARMA`
- relay fulfillment: `12 KARMA`
- verification contribution: `4 KARMA`
- high-confidence species bonus: `5 KARMA`

Configured badge rules:

- `Trail Scout`: first verified contribution
- `Relay Ranger`: first relay fulfillment
- `Species Spotter`: first species contribution
- `Water Guardian`: 5 water contributions
- `Hazard Herald`: 5 hazard contributions

## Android Integration

Important files:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/api/RewardsApi.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/api/RewardsApi.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/wallet/WalletManager.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/wallet/WalletManager.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/solana/SolanaPayloadCodec.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/solana/SolanaPayloadCodec.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/rewards/RewardsScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/rewards/RewardsScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/profile/ProfileScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/profile/ProfileScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/SyncWorker.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/SyncWorker.kt)

Current Android behavior:

- creates a local app-managed wallet per user
- signs relay payloads locally
- registers users with the backend and on-chain profile
- claims rewards for pending trail reports when online
- shows wallet state, KARMA balance, badge progress, and activity
- supports user-facing KARMA tipping
- stores voice relay jobs and inbox replies locally
- syncs relay jobs and inbox state through the background worker

## Backend Integration

Important files:

- [backend/src/server.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/server.ts)
- [backend/src/solana/client.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/solana/client.ts)
- [backend/src/voiceRelay.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/voiceRelay.ts)
- [backend/src/config.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/config.ts)
- [backend/src/db.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/db.ts)

Current backend responsibilities:

- sponsor transactions so users do not need SOL
- keep the attestor key
- register users
- fetch wallet state and rewards activity
- claim contribution rewards
- open relay jobs from signed intents
- create attested relay jobs when the backend needs to bridge a sender wallet and a live carrier flow
- fulfill relay jobs
- prepare and submit KARMA tips
- manage voice relay jobs, relay inbox replies, and mesh reply redistribution
- keep local SQLite audit records

### Current HTTP API

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

## Voice Relay Extension

The relay concept has been extended from generic delayed relay into a backend-triggered ElevenLabs/Twilio calling path.

Current flow:

1. Android creates and signs a relay intent offline.
2. The intent is stored in Room and wrapped into a relay packet.
3. BLE carries the packet between phones.
4. The first phone with connectivity can post the job to the backend.
5. The backend opens the relay job on-chain and initiates the outbound call when configured.
6. Successful completion can fulfill the relay job on-chain for the carrier.
7. Recipient replies are stored in the relay inbox and can be returned through mesh-assisted delivery.

Important files:

- [backend/src/voiceRelay.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/voiceRelay.ts)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattClient.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattClient.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattServer.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattServer.kt)

## What Is Implemented vs Still Thin

Implemented:

- real wallet creation and signing on Android
- real Solana program and Devnet assets
- sponsored reward claiming for normal trail reports
- on-chain relay-job state
- backend-managed voice relay opening and reply tracking
- KARMA tipping

Still thin or incomplete:

- biodiversity rewards are not yet as complete as the normal trail-report claim path
- backend attestation remains app/backend driven rather than a richer moderation pipeline
- live multi-phone carrier validation is still less mature than the local code paths
- the relay system is strong for the hackathon demo but still needs harder operational validation for production use
