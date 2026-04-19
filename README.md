# TrailKarma

A community-powered hiking app that socially rewards hikers for helping one another and contributing to the trail ecosystem.

## Core Idea

The app is offline-first, letting hikers log their location, hazards, water conditions, and wildlife sightings, relay that information phone-to-phone over BLE when there is no signal, and sync it to the cloud later. It also incentivizes altruistic contribution through Solana-based karma points and digital collectibles awarded for verified actions, whether that is reporting hazards for other travelers, sharing trail conditions, relaying delayed emergency or check-in messages, or contributing biodiversity data. For biodiversity monitoring, the app can run an on-device audio classification model to detect likely species from environmental sounds and pair those detections with location, while also allowing users to upload photos of species they encounter for identification and verification. Together, this creates a social, safety, and citizen-science network for hikers, where helping the community also generates meaningful data for biodiversity researchers.

## 🚀 Project Status (DataHacks 2026)

We have built a production-ready, offline-first Android application integrated with a Databricks Lakehouse backend, featuring a resilient BLE mesh networking system and advanced H3 spatial indexing.

### ✅ Completed Features
- **Offline-First Reporting**: Hikers can log hazards, water sources, and wildlife sightings without cellular signal.
- **Bi-directional Cloud Sync**: Automated background synchronization with Databricks SQL Warehouse using idempotent `MERGE INTO` operations.
- **H3 Spatial Intelligence**: Databricks natively computes H3 hexagonal cells (Resolution 9) for every report and GPS ping, enabling high-speed spatial aggregations and heatmaps.
- **BLE Mesh Networking**: A robust, persistent foreground service for phone-to-phone discovery and GATT-based data synchronization. Hikers "sync" missing reports with each other in the wild.
- **Voice Relay Jobs**: Offline-signed relay intents can now be carried over BLE and later turned into ElevenLabs outbound calls once any carrier hiker regains connectivity.
- **Dynamic Trail Engine**: Trail metadata (PCT, etc.) is pulled dynamically from Databricks, with O(1) trail-line snapping via `trail_segments`.
- **Modern UI/UX**: Polished Jetpack Compose interface with animated sync spinners, full-screen report details, and interactive OSM maps.
- **Android 15 Ready**: Fully compliant with the new 16KB memory page alignment requirements (NDK r27/CameraX 1.4.1).

### 🛠 Tech Stack
- **Android**: Kotlin, Jetpack Compose, Room DB, WorkManager, CameraX 1.4.1.
- **Backend**: Databricks (SQL Warehouse, Delta Lake, H3 Spatial Functions).
- **Communication**: Bluetooth Low Energy (Persistent Mesh, GATT Server/Client).
- **Spatial**: Uber H3 (Server-side indexing for O(1) clustering and Z-Ordering).

## 📂 Documentation
- [Databricks Setup Guide](DATABRICKS_SETUP.md) - How to initialize the cloud backend with H3 support.
- [Android Sync & BLE Guide](ANDROID_DATABRICKS_SYNC.md) - How the GATT mesh and Databricks sync pipeline works.
- [Rewards Architecture](docs/REWARDS.md) - Solana KARMA, badges, PDAs, sponsor flow, and attestation-backed claims.
- [App Vision](AGENTS.md) - The core philosophy and social reward system.

## Solana Layer

The Solana integration is intentionally hybrid. Real-world events still happen off-chain in the app, over BLE, or through backend providers. On-chain state is used for the parts Solana is good at: uniqueness, first-fulfiller settlement for relay jobs, KARMA issuance, and badge ownership.

- `solana/`: Anchor program for user profiles, contribution receipts, relay jobs, badge claims, and sponsored KARMA transfers.
- `backend/`: TypeScript attestor + sponsor service that verifies claims and submits Devnet transactions.
- `android_app/`: app-managed wallets, offline relay intent signing, BLE packet carrying, reward claim sync, and wallet/badge UI.

## 🌲 How to Run
1. **Cloud**: Run `python setup_databricks.py` to initialize the Databricks schema and demo data. This script requires the `h3` Python package.
2. **Rewards**: Start the backend in `backend/` after setting the Solana sponsor/attestor environment variables.
3. **Mobile**: Build the Android app using Android Studio with local Databricks and backend settings in your Gradle properties.
4. **Sync**: Login with a hiker nickname, select a trail, and start hiking.
