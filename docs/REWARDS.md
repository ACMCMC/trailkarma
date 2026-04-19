# Rewards Layer

This branch adds a hybrid Solana rewards system for TrailKarma. The design goal is to keep the hiking app offline-first and non-crypto-native while still giving us real on-chain reward issuance, relay-job uniqueness, and ownership of KARMA and collectible badges.

## What Exists In This Branch

There are three main pieces:

- `solana/`
  - Anchor program for reward state, relay jobs, contribution receipts, badge claims, and sponsored KARMA tipping.
- `backend/`
  - TypeScript sponsor + attestor service that reads the Anchor IDL, submits Devnet transactions, signs attestations, and exposes mobile-friendly HTTP endpoints.
- `android_app/`
  - App-managed wallet generation, offline intent signing, wallet registration, contribution reward claiming, relay job opening/fulfillment, and KARMA/badge UI.

## Core Design

The app does not try to put hiking activity fully on-chain.

Real-world events stay off-chain:

- hazard reports
- water reports
- biodiversity reports
- BLE encounters
- delayed relay message creation
- future phone-call or voice-agent delivery

On-chain state is used for:

- preventing duplicate reward issuance
- first-valid-fulfiller logic for relay jobs
- minting KARMA
- minting badges / collectibles
- proving ownership of those assets

The backend acts as the sponsor and attestor layer. Users do not need SOL and do not need to manually manage wallets for the hackathon.

## Solana Program

Program ID on Devnet:

- `GmRtJ6ghkm26SfdTDXTFDoX4TzHgQstCjwPPs5EpdZGS`

Config PDA:

- `CFjam3wWb33mhJY8GjXum5gyAihnkVzMfwXHdScyk489`

Primary PDA types:

- `UserProfile`
  - maps app users to wallets and tracks contribution counters
- `ContributionReceipt`
  - one per verified contribution reward
- `RelayJob`
  - tracks delayed relay job state and first valid fulfillment
- `BadgeClaim`
  - prevents duplicate badge minting
- `TipReceipt`
  - prevents duplicate tips from replayed signed payloads

Implemented instructions:

- `initialize_config`
- `register_user`
- `create_relay_job_from_intent`
- `fulfill_relay_job`
- `claim_contribution_reward`
- `claim_badge`
- `tip_karma`

## Assets On Devnet

KARMA fungible token:

- `BwXfoGqqbyW6Scaq5oqXhjacLq4gKrjK3jwQRg2SPzQT`

Badge mints:

- `Trail Scout`: `EWDtuZ5Jk1fGq99KkMnowmba9tTNXa6QUym876tfccEb`
- `Relay Ranger`: `FVwQZK7TAXJx87NZxjVK7PFkmGosoJ6oQRYXUdWL7nh5`
- `Species Spotter`: `6uFsGeEznkfzYn59smAzCFw5u4bL8qJBNAwNeJ2nE673`
- `Water Guardian`: `Cz62c2HCaYvhnDHCAF3nNu37WqVnF9QaqHWD8aysa2h`
- `Hazard Herald`: `9kmGY9ktyGehY8CB7mUYrTW1b72QQPQTJ1DZcS1gycX`

Token model:

- KARMA is transferable.
- Badges are Token-2022 non-transferable achievement mints.

## Reward Rules

Current configured values:

- hazard report: `10 KARMA`
- water report: `10 KARMA`
- species report: `8 KARMA`
- relay fulfillment: `12 KARMA`
- verification contribution: `4 KARMA`
- high-confidence species bonus: `5 KARMA`

Current badge rules:

- `Trail Scout`: first verified contribution
- `Relay Ranger`: first relay fulfillment
- `Species Spotter`: first species contribution
- `Water Guardian`: 5 water contributions
- `Hazard Herald`: 5 hazard contributions

## Backend

Backend folder:

- `backend/`

Important files:

- [backend/src/server.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/server.ts)
- [backend/src/solana/client.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/solana/client.ts)
- [backend/src/bootstrap.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/bootstrap.ts)
- [backend/.env.example](/Users/suraj/Desktop/dhacks/datahacks26/backend/.env.example)

Backend responsibilities:

- sponsor all user transactions
- keep the attestor key
- register wallets on-chain
- claim contribution rewards
- open relay jobs from signed offline intents
- fulfill relay jobs
- prepare and submit sponsored KARMA tips
- maintain a local SQLite audit trail

Current backend environment uses:

- `PROGRAM_ID=GmRtJ6ghkm26SfdTDXTFDoX4TzHgQstCjwPPs5EpdZGS`
- `SOLANA_RPC_URL=https://api.devnet.solana.com`

## Android Wiring

Important Android files:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/api/RewardsApi.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/api/RewardsApi.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/wallet/WalletManager.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/wallet/WalletManager.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/solana/SolanaPayloadCodec.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/solana/SolanaPayloadCodec.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/map/MapViewModel.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/map/MapViewModel.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/SyncWorker.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/SyncWorker.kt)

Current Android behavior:

- creates a local app-managed Solana wallet per user
- registers that wallet with the backend
- stores wallet registration status locally
- claims rewards for local reports once the phone is online
- creates offline relay intents and signs them locally
- opens relay jobs on-chain once the phone regains connectivity
- fulfills relay jobs through the backend
- displays KARMA and badge state on the map screen
- shows reward claim status in report history

Android configuration:

- `BuildConfig.REWARDS_BASE_URL` controls the backend URL
- default value is `http://10.0.2.2:3000` for Android emulator to local machine
- override with `-Prewards.url=...` or `REWARDS_BASE_URL=...` at build time
- `BuildConfig.DATABRICKS_URL` defaults to `https://dbc-f1d1578e-8435.cloud.databricks.com`
- `BuildConfig.DATABRICKS_WAREHOUSE` now defaults to `5fa7bca37483870e`
- `BuildConfig.DATABRICKS_TOKEN` still comes from environment or Gradle property and is not stored in the repo

Examples:

```bash
cd android_app
./gradlew :app:assembleDebug -Prewards.url=http://10.0.2.2:3000
```

For a physical device later, use your machine LAN IP instead of `10.0.2.2`, for example:

```bash
cd android_app
./gradlew :app:assembleDebug -Prewards.url=http://192.168.1.50:3000
```

## Android Integration Fixes Added

The branch now includes a few app-side fixes that were necessary to make rewards usable in practice:

- cleartext HTTP is enabled in the app manifest so the emulator can talk to a local backend at `http://10.0.2.2:3000`
- reward claiming is no longer blocked on Databricks being configured
- the sync worker now treats Databricks sync as optional and rewards sync as independent
- pending reward claims are derived from local unclaimed reports, not just reports already marked as cloud-synced
- the wallet state can refresh after reward claims so the KARMA card updates without restarting the app

## Databricks Status

Databricks is now validated against the live workspace used by this project.

Verified workspace details:

- host: `https://dbc-f1d1578e-8435.cloud.databricks.com`
- active SQL warehouse: `5fa7bca37483870e`
- current catalog: `workspace`
- active schema used by the app: `trailkarma`

Verified tables present:

- `trailkarma.trail_reports`
- `trailkarma.location_updates`
- `trailkarma.relay_packets`
- additional project tables already exist in the same schema

Verified behavior:

- the exact `INSERT INTO trailkarma.trail_reports (...)` SQL emitted by the Android app succeeds
- the exact `INSERT INTO trailkarma.location_updates (...)` SQL emitted by the Android app succeeds
- the inserted rows can be read back successfully
- test rows were deleted after validation

Repo fixes added for Databricks:

- root [.env.example](/Users/suraj/Desktop/dhacks/datahacks26/.env.example) now includes `DATABRICKS_WAREHOUSE`
- [android_app/app/build.gradle.kts](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/build.gradle.kts) now defaults the warehouse ID to `5fa7bca37483870e`
- [android_app/app/src/main/java/fyi/acmc/trailkarma/MainActivity.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/MainActivity.kt) now only seeds Databricks config when URL, token, and warehouse are all present
- [setup_databricks.py](/Users/suraj/Desktop/dhacks/datahacks26/setup_databricks.py) was updated to target the existing `trailkarma` schema layout rather than stale catalog assumptions

## What Was Tested

### Local validator

Verified end to end on `solana-test-validator`:

- program deploy
- config initialization
- mint initialization
- backend startup
- user registration
- hazard contribution claim
- KARMA minting
- `Trail Scout` badge minting

### Devnet

Verified end to end on Devnet:

- program deployed successfully
- IDL metadata written successfully
- config and all six mints bootstrapped successfully
- backend read the deployed config
- test user registered through backend
- hazard contribution claimed on-chain
- wallet reflected `10 KARMA` and `Trail Scout`

Example successful Devnet reward transaction:

- `5BYdCN76VbrmA5nrCUCfGQWhFsdwZnG9CEeSq36bTq7wytjmzgNd93b9pw3JhpxE57KCKZoZXAadeZVJ2n6nYXnK`

## Current Commands

Build backend:

```bash
cd backend
npm install
npm run build
```

Run backend:

```bash
cd backend
npm run start
```

Bootstrap on-chain config and mints:

```bash
cd backend
npm run bootstrap
```

Build Solana program:

```bash
cd solana
~/.avm/bin/anchor-1.0.0 build
```

Deploy Solana program:

```bash
cd solana
~/.avm/bin/anchor-1.0.0 deploy --provider.cluster devnet --provider.wallet ../backend/keys/admin.json
```

## Important Tooling Notes

The working toolchain in this repo ended up being:

- Solana CLI `3.1.13`
- Anchor CLI `1.0.0` for successful build/deploy flow

`anchor 0.30.1` was not reliable in this environment because of IDL generation issues. The program dependencies are still `anchor-lang` / `anchor-spl` `0.30.1`, but the CLI that worked for this branch was `1.0.0`.

The SBF build also needed an older compatible `blake3` / `constant_time_eq` lockfile resolution so the Solana platform Rust toolchain could compile the workspace.

## Known Gaps

This is a strong MVP, not the final production design.

Current limitations:

- contribution verification is backend-trusted, not externally attested
- relay fulfillment currently uses a mock proof string, not a real carrier/provider delivery receipt
- there is no hosted backend URL yet for physical phone testing
- map wallet state refreshes after local state changes, but there is no push channel or background polling strategy yet
- the Android app still has a simplified relay demo flow rather than full encrypted traveler messaging UX

## Planned Relay Upgrade

The future relay fulfillment path should use ElevenLabs agent calling instead of raw SMS delivery.

Current plan:

- keep Twilio as the phone-number layer you already provisioned
- use ElevenLabs agent calling for the actual outbound call / conversation flow
- treat the ElevenLabs or backend completion event as the fulfillment attestation source
- keep the on-chain design the same
  - off-chain real-world delivery event
  - backend attestation
  - on-chain uniqueness and reward settlement

That means the Solana architecture in this branch does not need to change when we swap SMS delivery for ElevenLabs calling. Only the backend fulfillment verification path changes.
