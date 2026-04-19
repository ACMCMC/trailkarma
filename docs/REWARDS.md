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
- bootstrap and configure the ElevenLabs voice relay agent plus imported phone number
- initiate outbound relay calls through ElevenLabs/Twilio
- poll conversation state and turn successful calls into on-chain relay fulfillment
- capture recipient replies into the relay inbox for later delivery
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
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/rewards/RewardsScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/rewards/RewardsScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/rewards/RewardsTheme.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/rewards/RewardsTheme.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/map/MapViewModel.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/map/MapViewModel.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/SyncWorker.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/SyncWorker.kt)

Current Android behavior:

- creates a local app-managed Solana wallet per user
- creates a richer local hiker profile with trail name, callback number, and trusted contacts
- registers that wallet with the backend
- stores wallet registration status locally
- claims rewards for local reports once the phone is online
- creates offline relay intents and signs them locally
- stores voice relay intents as generic relay packets so nearby hikers can carry them over BLE
- opens relay jobs on-chain once the phone regains connectivity or a carrier hiker syncs them
- fulfills relay jobs through the backend
- exposes a dedicated `Rewards` destination from the map drawer and rewards teaser card
- renders a premium rewards dashboard with KARMA balance, collectible gallery, progress cards, and reward activity feed
- supports user-facing KARMA tipping from the rewards screen
- shows redesigned relay mission, profile, and report-ledger surfaces that match the rewards system

## Voice Relay Extension

This branch now extends the original relay concept from delayed SMS into an ElevenLabs-backed outbound calling flow.

Current design:

- the sender still creates a signed offline relay intent locally on Android
- that intent is stored in Room and also wrapped as a generic `RelayPacket`
- BLE GATT now syncs both trail reports and relay packets between phones
- a carrier phone that receives the packet can materialize the same relay intent locally
- when any carrier regains connectivity, the backend can:
  - verify the sender wallet mapping
  - open the relay job on-chain using the original sender signature
  - use the ElevenLabs agent plus imported Twilio number to place the call
  - poll the conversation record later
  - mark the relay fulfilled on-chain for the carrier if the call was meaningfully completed
  - store any recipient reply in the relay inbox for the original hiker

Important backend files for voice relay:

- [backend/src/voiceRelay.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/voiceRelay.ts)
- [backend/src/server.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/server.ts)
- [backend/src/db.ts](/Users/suraj/Desktop/dhacks/datahacks26/backend/src/db.ts)

Important Android files for voice relay and mesh carriage:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattClient.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattClient.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattServer.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattServer.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt)

Current voice relay API surface:

- `POST /v1/profile/upsert`
- `GET /v1/profile/:appUserId`
- `POST /v1/voice-relay/jobs/open`
- `GET /v1/voice-relay/jobs/:appUserId`
- `GET /v1/voice-relay/inbox/:appUserId`
- `POST /v1/voice-relay/inbox/:replyId/ack`

Runtime status validated on this branch:

- backend TypeScript compiles
- backend process starts successfully after rebuilding `better-sqlite3` for the active Node ABI
- Android Kotlin compiles with the new relay packet transport and profile UI
- ElevenLabs agent configuration was updated successfully from the local backend environment
- the imported Twilio number is assigned to the TrailKarma relay agent
- a live outbound call request succeeded and returned a real `conversation_id` and `callSid`

Known remaining caveats:

- the full on-chain open path for a live call still depends on a real sender-side signature coming from the Android wallet
- reply delivery is fully stored in the backend inbox, but generalized backend-to-carrier mesh redistribution of reply packets is not yet automated
- physical multi-phone BLE testing is still needed for the end-to-end carrier flow

## Current GUI Status

The Android rewards GUI is now a real product surface rather than a set of loose status widgets.

What exists now:

- dedicated `RewardsScreen`
  - hero wallet card with live KARMA balance, wallet identity, and contribution summary
  - collectibles gallery with owned vs locked badge states
  - milestone progress cards for verified actions, relay pipeline, and owned collectibles
  - recent reward activity feed for contribution rewards, relay rewards, badges, and KARMA tips
  - tipping dialog for sponsored KARMA transfers between user wallets
- upgraded map entry point
  - drawer now links directly to `Rewards`
  - map rewards teaser card opens the full rewards destination
- upgraded report ledger UI
  - more legible per-report verification and reward state
  - explicit KARMA amounts and transaction snippets for claimed reports
- upgraded relay missions UI
  - clearer mission framing for create/open/fulfill flow
  - better separation of nearby hikers, mission queue, and event log
  - status banner for relay workflow feedback

What is still not built out:

- no custom badge illustrations yet
  - collectibles are visually designed in Compose, but not backed by bespoke art assets
- no explorer or deep-link flow for transaction signatures
- no push-style celebration tied to every reward event outside the rewards screen
- no physical-phone BLE polish pass yet

Recommended next UI work for a collaborator:

- add bespoke collectible art and richer metadata presentation per badge mint
- add a shareable “earned collectible” moment after reward settlement
- add transaction drilldown or Solana explorer deep links
- test and tune spacing, touch targets, and BLE mission flows on physical phones

## API Surfaces For Rewards UI

The backend now exposes enough structured reward data for the Android UX:

- `GET /v1/users/:appUserId/wallet`
  - KARMA balance
  - owned badge labels
  - structured badge metadata and progress targets
  - aggregate reward stats for hazards, water, species, relays, and total earned KARMA
- `GET /v1/users/:appUserId/rewards/activity`
  - contribution reward events
  - relay reward events
  - badge mint events
  - KARMA tip events

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
- relay creation no longer crashes the BLE screen when no local user record exists
- the BLE screen now shows a small status message for relay create/open/fulfill actions instead of failing silently

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

- program rebuilt with Anchor `1.0.0` and upgraded on Devnet successfully
- program deployed successfully
- IDL metadata written successfully
- config and all six mints bootstrapped successfully
- backend read the deployed config
- test user registered through backend
- hazard contribution claimed on-chain
- wallet reflected `10 KARMA` and `Trail Scout`
- relay job open worked on-chain
- relay fulfillment worked on-chain
- KARMA tipping worked end to end

Most recent Devnet smoke-test behavior:

- fresh user registration returned `0 KARMA`
- hazard contribution claim minted `10 KARMA`
- first contribution minted `Trail Scout`
- relay-job open produced a valid on-chain relay job
- first valid relay fulfiller received `12 KARMA`
- first relay fulfillment minted `Relay Ranger`
- a `1 KARMA` tip transferred successfully between two test wallets

Example successful Devnet smoke-test transactions from the current branch:

- program upgrade: `4RAPe3MtRcfX9QJwQWRruGGA7TqMeqe5gDPxmR6UE5F5D5odKr2vbfCX8yURDszUoif7sMFBJDEJvzMzgVghkyA2`
- contribution reward: `5WT9SkzDyH47qFhAi7AK6KPepXmsiPJpFmGCNwdSHwqt6UVej8qJgpAna6yBPFfipH5omwtbcgBjVctr1nqH2jHS`
- relay open: `32tyjcoNZaQpH1H5aXySMRf8CQCmbwcW5f9fDTckbidQ6yXhDq16hFVczCy2wWt9eyZmvNewA8nJaDpYtfLmivj9`
- relay fulfill: `4hnhE2Xkf1T6jEvvaXW579U9Edej67KAZUmZeWjZF72oDFkEAD6KvQSgvkwcUBcNZkini61tXndGFV6m1bYi1W7i`
- tip transfer: `H6XgZmZCMjR77oG7SxTiQcxnfL6SPW9iTxz3VgHyRKSGGCqqqamRDQBDpVqBQaHU1hNiXph42RNtXxwDLcWqEdb`

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
~/.avm/bin/anchor build
```

Deploy Solana program:

```bash
cd solana
~/.avm/bin/anchor deploy --provider.cluster devnet --provider.wallet ../backend/keys/admin.json
```

## Important Tooling Notes

The working toolchain in this repo is currently:

- Solana CLI `3.1.13`
- Anchor CLI `1.0.0`
- `anchor-lang` / `anchor-spl` `1.0.0`
- backend TypeScript client package `@anchor-lang/core` `1.0.0`

The workspace now pins Anchor through [solana/Anchor.toml](/Users/suraj/Desktop/dhacks/datahacks26/solana/Anchor.toml):

- `[toolchain]`
- `anchor_version = "1.0.0"`

The earlier vendored `anchor-syn` workaround was removed. This branch now builds against upstream Anchor `1.0.0` directly.

- [solana/Cargo.toml](/Users/suraj/Desktop/dhacks/datahacks26/solana/Cargo.toml)
- [solana/programs/trail_karma_rewards/Cargo.toml](/Users/suraj/Desktop/dhacks/datahacks26/solana/programs/trail_karma_rewards/Cargo.toml)
- [backend/package.json](/Users/suraj/Desktop/dhacks/datahacks26/backend/package.json)

Important local shell note:

- `anchor build` needs the real Solana install directory on your shell `PATH`
- the working path is `~/.local/share/solana/install/active_release/bin`
- `cargo-build-sbf` should come from that real directory, not from a standalone symlink, because it expects adjacent SDK files

Current migration-specific code changes:

- program imports now use the Solana 3-style split crates needed under Anchor `1.0.0`
- backend imports were moved from `@coral-xyz/anchor` to `@anchor-lang/core`
- explicit enum discriminants in the program were removed so Anchor `1.0.0` IDL generation works cleanly while preserving the same enum ordering

Current warning status:

- `anchor build` succeeds and emits IDL/artifacts correctly
- the program still emits non-fatal `unexpected cfg` warnings from Anchor/Solana macros during build
- those warnings did not block the build, packaging, or Devnet smoke test

## Known Gaps

This is a strong MVP, not the final production design.

Current limitations:

- contribution verification is backend-trusted, not externally attested
- relay fulfillment currently uses a mock proof string, not a real carrier/provider delivery receipt
- there is no hosted backend URL yet for physical phone testing
- map wallet state refreshes after local state changes, but there is no push channel or background polling strategy yet
- the Android app still has a simplified relay demo flow rather than full encrypted traveler messaging UX
- the Android app does not yet have a full collectibles gallery, wallet history screen, or in-app tipping UI

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
