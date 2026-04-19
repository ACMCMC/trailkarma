# datahacks26

## Core Idea

A community-powered hiking app that socially rewards hikers for helping one another and contributing to the trail ecosystem. The app is offline-first, letting hikers log their location, hazards, water conditions, and wildlife sightings, relay that information phone-to-phone over BLE when there is no signal, and sync it to the cloud later. It also incentivizes altruistic contribution through Solana-based karma points and digital collectibles awarded for verified actions, whether that is reporting hazards for other travelers, sharing trail conditions, relaying delayed emergency or check-in messages, or contributing biodiversity data. For biodiversity monitoring, the app can run an on-device audio classification model to detect likely species from environmental sounds and pair those detections with location, while also allowing users to upload photos of species they encounter for identification and verification. Together, this creates a social, safety, and citizen-science network for hikers, where helping the community also generates meaningful data for biodiversity researchers.

## Solana MVP

The repo now includes a hybrid Solana reward layer built around the hackathon requirements:

- `solana/`: Anchor program for user profiles, contribution receipts, relay jobs, badge claims, and sponsored KARMA tipping
- `backend/`: TypeScript sponsor + attestor service for Devnet transaction submission and verification
- `android_app/`: device-generated app wallets, offline relay intent signing, wallet registration, reward sync, and KARMA/badge UI hooks

The design keeps real-world events off-chain and reward settlement on-chain:

- Android captures reports and relay intents offline first
- the backend verifies report / relay fulfillment inputs and sponsors all Devnet transactions
- the Solana program enforces uniqueness, fulfillment state, KARMA issuance, and badge ownership

## Quick Setup

1. Build and deploy the Anchor program from `solana/`
2. Create Devnet Token-2022 mints for KARMA and the five badge mints, with the config PDA as mint authority
3. Put `PROGRAM_ID`, `SPONSOR_SECRET_KEY`, `ATTESTOR_SECRET_KEY`, and `SOLANA_RPC_URL` in your environment
4. Run the rewards backend from `backend/`
5. Point Android at the backend with `REWARDS_BASE_URL` or `-Prewards.url=...`

Useful commands:

```bash
cd backend
npm install
npm run dev
```

```bash
cd solana
anchor build
anchor deploy
```
