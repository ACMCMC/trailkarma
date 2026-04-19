# TrailKarma

A community-powered hiking app that socially rewards hikers for helping one another and contributing to the trail ecosystem.

## 🚀 Project Status (DataHacks 2026)

We have built a production-ready, offline-first Android application integrated with a Databricks Lakehouse backend.

### ✅ Completed Features
- **Offline-First Reporting**: Hikers can log hazards, water sources, and wildlife sightings without cellular signal.
- **Bi-directional Cloud Sync**: Automated background synchronization with Databricks SQL Warehouse when internet is restored.
- **Dynamic Trail Engine**: Trail metadata (PCT, etc.) is pulled dynamically from Databricks, allowing for remote trail management.
- **BLE Contact Tracing / Relay**: Phone-to-phone discovery and encounter logging using Bluetooth Low Energy, allowing "relayed" reports to travel through a mesh of hikers.
- **Interactive Mapping**: OSMDroid-based map with custom markers, trail selection, and real-time community updates.
- **Android 15 Ready**: Fully compliant with the new 16KB memory page alignment requirements (NDK r27/CameraX 1.4.1).

### 🛠 Tech Stack
- **Android**: Kotlin, Jetpack Compose, Room DB, WorkManager, CameraX 1.4.1.
- **Backend**: Databricks (SQL Warehouse, Delta Lake).
- **Spatial**: H3 Hexagonal Grid Indexing (planned for high-speed spatial joins).
- **Communication**: Bluetooth Low Energy (Beaconing & Scanning).

## 📂 Documentation
- [Databricks Setup Guide](DATABRICKS_SETUP.md) - How to initialize the cloud backend.
- [Android Sync & BLE Guide](ANDROID_DATABRICKS_SYNC.md) - How the offline/online relay system works.
- [App Vision](AGENTS.md) - The core philosophy and social reward system.

## 🌲 How to Run
1. **Cloud**: Run `python setup_databricks.py` to initialize the database schema.
2. **Mobile**: Build the app using Android Studio. Ensure you have your Databricks credentials in `local.properties`.
3. **Sync**: Login with a "Hiker Nickname", select your trail, and start hiking!

