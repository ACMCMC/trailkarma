# Bioacoustics Status

This document describes the biodiversity and bioacoustics work that is implemented in the current `datahacks26` repository. It replaces the older branch-handoff notes and focuses on what exists now.

## What Is Implemented

The current build supports an offline-first biodiversity contribution flow:

- record a 5-second environmental audio clip on Android
- save the observation immediately to Room
- capture best-available location metadata when present
- run on-device classification through the local inference stack
- show a label, confidence band, and short explanation in the app
- optionally attach a photo to the same observation
- save the observation into the local biodiversity ledger
- sync the already-classified event and optional photo to the Python backend later
- generate a compact BLE-relayable metadata payload without sending raw files

## Android Implementation

Main files:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureScreen.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureViewModel.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureViewModel.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/audio/TrailAudioRecorder.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/audio/TrailAudioRecorder.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/BiodiversityRepository.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/BiodiversityRepository.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversitySyncWorker.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversitySyncWorker.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/models/Models.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/main/java/fyi/acmc/trailkarma/models/Models.kt)
- [android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md](../android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md)

### Current Android Flow

1. The user records a 5-second clip.
2. The app stores the audio file locally and creates a `BiodiversityContribution` row.
3. A local worker runs on-device inference.
4. The app stores top-K candidates, final label, taxonomic level, confidence, explanation, and model metadata.
5. The user can save the observation locally and optionally attach a photo.
6. A sync worker uploads the already-classified observation and photo once connectivity is available.

### Local Data Model

`BiodiversityContribution` currently stores:

- observation identity
- user and wallet attribution
- timestamp
- latitude / longitude when available
- location accuracy and source
- audio URI
- optional photo URI
- classification outputs
- verification and collectible bookkeeping fields
- cloud/photo sync state
- data-sharing status

`KarmaEvent` is also extended so biodiversity observations can carry reward and collectible bookkeeping state.

### Location Integrity

The current code avoids fabricating biodiversity coordinates.

- If a usable location exists, the observation stores `lat`, `lon`, accuracy, and source metadata.
- If location is missing, `lat` and `lon` remain `null`.
- Observations without usable coordinates are not marked relayable.
- Missing-location observations can still be stored locally and synced, but they are downgraded in data-sharing quality.

## On-Device Inference

The app supports bundled or sideloaded biodiversity model packs.

Model-pack expectations are documented in:

- [android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md](../android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md)

Current runtime behavior:

- If the full pack exists, Android runs TFLite embedding plus a linear head and prototype retrieval.
- If the pack is missing, Android falls back to a lightweight heuristic path so the capture flow still works.
- Final labels and explanations are currently deterministic on device for demo stability.
- The optional explainer-pack path exists as export scaffolding, but it is not the main runtime requirement.

The repo already includes a bundled biodiversity asset pack under:

- [android_app/app/src/main/assets/biodiversity](../android_app/app/src/main/assets/biodiversity)

## BLE Behavior

BLE relay for biodiversity is intentionally metadata-only.

Relayable biodiversity packets can include:

- `observation_id`
- `lat`
- `lon`
- `location_accuracy_meters`
- `location_source`
- `timestamp`
- `finalLabel`
- `taxonomicLevel`
- `confidenceBand`
- `verificationStatus`

Current constraints:

- raw audio is not relayed over BLE
- photos are not relayed over BLE
- observations without usable coordinates are not marked relayable

## Python Backend

Main files:

- [backend/app.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/app.py)
- [backend/acoustic.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/acoustic.py)
- [backend/postprocess.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/postprocess.py)
- [backend/storage.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/storage.py)
- [backend/databricks_mirror.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/databricks_mirror.py)

Current endpoints:

- `POST /api/biodiversity/audio`
  Accepts raw audio and runs backend inference.
- `POST /api/biodiversity/photo-link`
  Links a photo or photo URI to an existing observation.
- `POST /api/biodiversity/audio-sync`
  Accepts an already-classified on-device observation and stores/mirrors it without re-running inference.

In practice, the Android app now relies primarily on `/api/biodiversity/audio-sync` for the main offline-first path.

## Databricks Mirroring

When `DATABRICKS_HOST`, `DATABRICKS_TOKEN`, and `DATABRICKS_WAREHOUSE` are configured, the Python backend mirrors biodiversity observations into `workspace.trailkarma.biodiversity_events`.

If those variables are not set, the backend still works and skips mirroring.

## Training And Export Tooling

The repo still contains the Brev-oriented training and export scripts that were used to generate the Android model artifacts.

Important scripts:

- [backend/training/train_open_world_head.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/training/train_open_world_head.py)
- [backend/training/finetune_local_llm.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/training/finetune_local_llm.py)
- [backend/training/export_android_model_pack.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/training/export_android_model_pack.py)
- [backend/training/export_perch_checkpoint_to_tflite.py](/Users/suraj/Desktop/dhacks/datahacks26/backend/training/export_perch_checkpoint_to_tflite.py)
- [backend/training/export_android_explainer_pack.sh](/Users/suraj/Desktop/dhacks/datahacks26/backend/training/export_android_explainer_pack.sh)

## Testing

Current Android instrumentation coverage includes:

- [android_app/app/src/androidTest/java/fyi/acmc/trailkarma/BiodiversityFlowSmokeTest.kt](/Users/suraj/Desktop/dhacks/datahacks26/android_app/app/src/androidTest/java/fyi/acmc/trailkarma/BiodiversityFlowSmokeTest.kt)

Preferred loop after Android biodiversity edits:

```bash
scripts/android-smoke-loop.sh
```

## What Is Still Missing

The biodiversity stack is real, but it is not fully complete relative to the original product vision.

Still missing or incomplete:

- automatic photo-based species identification rather than photo attachment only
- a richer backend verification pipeline for biodiversity rewards and collectibles
- full partner-facing biodiversity export and review workflows
- stronger anti-abuse logic for determining when a biodiversity observation is rewardable
- more field validation of the BLE relay path between multiple physical phones
