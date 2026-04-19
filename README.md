# TrailKarma

A community-powered hiking app that socially rewards hikers for helping one another and contributing to the trail ecosystem.

## 🚀 Project Status (DataHacks 2026)

We have built a production-ready, offline-first Android application integrated with a Databricks Lakehouse backend, featuring a resilient BLE mesh networking system and advanced H3 spatial indexing.

### ✅ Completed Features
- **Offline-First Reporting**: Hikers can log hazards, water sources, and wildlife sightings without cellular signal.
- **Bi-directional Cloud Sync**: Automated background synchronization with Databricks SQL Warehouse using idempotent `MERGE INTO` operations.
- **H3 Spatial Intelligence**: Databricks natively computes H3 hexagonal cells (Resolution 9) for every report and GPS ping, enabling high-speed spatial aggregations and heatmaps.
- **BLE Mesh Networking**: A robust, persistent foreground service for phone-to-phone discovery and GATT-based data synchronization. Hikers "sync" missing reports with each other in the wild.
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
- [App Vision](AGENTS.md) - The core philosophy and social reward system.

## 🌲 How to Run
1. **Cloud**: Run `python setup_databricks.py` to initialize the database schema. (Requires `h3` python package).
2. **Mobile**: Build the app using Android Studio. Ensure you have your Databricks credentials in `local.properties`.
3. **Sync**: Login with a "Hiker Nickname", select your trail, and start hiking!

