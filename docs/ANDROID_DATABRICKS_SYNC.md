# Android Sync, BLE, And Test Loop

This document describes the current Android data flow in the `datahacks26` repo. It focuses on the implemented offline-first behavior rather than the older setup notes.

## Current Android Sync Model

The Android app treats local state as the source of truth and sync as a background reconciliation step.

Current local data includes:

- trail reports
- biodiversity contributions
- relay packets
- relay job intents
- relay inbox messages
- location updates
- trail metadata
- wallet / profile state

The app uses three main background workers:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/SyncWorker.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/SyncWorker.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversitySyncWorker.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversitySyncWorker.kt)

## What `SyncWorker` Does

When connectivity is available, `SyncWorker` currently:

- pushes unsynced trail reports to Databricks
- pushes unsynced location updates to Databricks
- pushes unsynced relay packets to Databricks
- pulls recent trail reports from Databricks
- pulls trail geometry / metadata from Databricks
- syncs the current user registration to the rewards backend
- claims pending rewards for trail reports
- opens pending relay jobs
- opens pending voice relay jobs
- syncs mesh relay replies
- syncs the relay inbox

Databricks sync logic lives in:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/DatabricksSyncRepository.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/DatabricksSyncRepository.kt)

Rewards / relay sync logic lives in:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt)

## Databricks Behavior

The current Android app syncs against Databricks SQL Warehouse using SQL statements generated on-device.

Implemented behavior:

- `MERGE INTO` for `trail_reports`
- `MERGE INTO` for `location_updates`
- `MERGE INTO` for `relay_packets`
- pull of recent community reports back into Room
- pull of trail metadata / geometry back into Room
- server-side H3 computation using Databricks functions

Important current detail:

- Android sends raw coordinates.
- Databricks computes H3 cells on ingest.
- H3 is not computed on-device.

## BLE Mesh Behavior

The BLE system runs through a foreground service and GATT-based sync.

Important files:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/ble/BleService.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ble/BleService.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ble/BleRepository.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ble/BleRepository.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattClient.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattClient.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattServer.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ble/GattServer.kt)

Current BLE responsibilities:

- discover nearby TrailKarma devices
- exchange trail reports
- exchange relay packets
- carry voice relay intents between phones
- carry relay replies back toward the sender

The repo is clearly built around BLE carriage, but full multi-phone field validation is still less mature than the local code surface.

## Android Configuration

Important build-time properties in [android_app/app/build.gradle.kts](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/build.gradle.kts):

- `api.baseUrl`
  Biodiversity backend base URL. Falls back to `TRAILKARMA_API_BASE_URL`, then `http://10.0.2.2:3000`.
- `rewards.url`
  Rewards / relay backend base URL. Falls back to `REWARDS_BASE_URL`, then `api.baseUrl`.
- `databricks.url`
- `databricks.token`
- `databricks.warehouse`

These can come from:

- Gradle properties
- environment variables
- `android_app/local.properties`

## Preferred Emulator Loop

Use the scripted loop from `AGENTS.md` instead of manual Android Studio clicking:

```bash
scripts/android-sdk-bootstrap.sh
scripts/android-avd-create.sh
scripts/android-emulator-start.sh
scripts/android-install-debug.sh
scripts/android-smoke-loop.sh
```

Useful artifacts after a smoke-loop failure:

- `.artifacts/android-smoke/launch-logcat.txt`
- `.artifacts/android-smoke/ui/window_dump.xml`
- `.artifacts/android-smoke/ui/screen.png`

## Preferred Physical-Device Loop

With a USB-connected Android phone:

```bash
SESSION_NAME=my-session scripts/android-physical-debug-loop.sh
```

This loop:

- checks the local backend health endpoint
- installs the debug build
- sets up `adb reverse tcp:3000 tcp:3000`
- starts background `logcat` capture

Related helpers:

- `scripts/android-physical-install-debug.sh`
- `scripts/android-physical-logcat.sh`
- `scripts/android-physical-capture.sh`

## Current Instrumentation Coverage

Current Android smoke tests:

- [android_app/app/src/androidTest/java/fyi/acmc/trailkarma/SmokeNavigationTest.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/androidTest/java/fyi/acmc/trailkarma/SmokeNavigationTest.kt)
- [android_app/app/src/androidTest/java/fyi/acmc/trailkarma/BiodiversityFlowSmokeTest.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/androidTest/java/fyi/acmc/trailkarma/BiodiversityFlowSmokeTest.kt)

Coverage today is strongest around launch/navigation and the biodiversity capture flow. It is not yet a full end-to-end automated mesh / relay / rewards test suite.

## Current Gaps

Still missing or incomplete in this area:

- more complete automated end-to-end testing for relay carrier flows
- stronger physical multi-phone BLE validation
- a user-facing settings surface for changing backend endpoints at runtime
- deeper sync observability and failure reporting inside the app
