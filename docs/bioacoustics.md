# Bioacoustics Branch Notes

This document captures the bioacoustics work implemented on the `bioacoustics-ai` branch for the TrailKarma hiking app. It is written as an engineering handoff for demo, testing, and follow-on development.

## Goal

The feature adds an offline-first biodiversity contribution flow to the hiking app:

- record a five-second environmental audio clip on Android
- classify the clip locally first
- persist the event locally with contributor identity, time, and coordinates
- save a biodiversity contribution entry that can later sync to the backend
- optionally attach a photo to the same observation for later verification
- expose a compact, BLE-relayable metadata view without sending raw files
- preserve enough state for later verification, collectibles, rewards, and partner data sharing

This follows the product idea in `AGENTS.md`: a community-powered, offline-first hiking app where helpful contributions become trail intelligence, future research data, and rewardable user actions.

## Branch Scope

The branch is anchored by these main commits:

- `9db96a5` `Add offline biodiversity audio pipeline`
- `054412c` `Harden biodiversity location records and UX`

The work spans Android, the Python backend, Brev-side training/export utilities, model assets, and UX integration into the rest of the app.

## What Was Built

### 1. Android biodiversity capture flow

Primary screen:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureScreen.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureScreen.kt)

View model:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureViewModel.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureViewModel.kt)

Core flow:

1. User taps `Record Trail Sound`.
2. App records a five-second mono WAV clip through `TrailAudioRecorder`.
3. App captures timestamp and best-available fused last-known location.
4. App creates a local `BiodiversityContribution` row immediately.
5. App schedules local inference on the device using WorkManager.
6. App schedules eventual cloud sync if network becomes available.
7. App shows the classification, explanation, reward status, and photo action.
8. User can attach a photo to the same `observationId`.
9. User can save the observation into the local biodiversity ledger.

Important implementation details:

- Audio is stored locally under app files first.
- The capture flow does not block on network.
- Classification is driven by `BiodiversityLocalInferenceWorker`, not the backend.
- The screen was refactored to match the rest of the app’s card, hero, and ledger visual language rather than looking like an isolated prototype.

### 2. Local-first biodiversity persistence model

The local database entity is:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/models/Models.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/models/Models.kt)

`BiodiversityContribution` stores:

- contribution identity
- `observationId`
- `userId`
- `observerDisplayName`
- `observerWalletPublicKey`
- `createdAt`
- `lat`
- `lon`
- `locationAccuracyMeters`
- `locationSource`
- `audioUri`
- `photoUri`
- `topKJson`
- `finalLabel`
- `finalTaxonomicLevel`
- `confidence`
- `confidenceBand`
- `explanation`
- `verificationStatus`
- `relayable`
- `karmaStatus`
- `inferenceState`
- `cloudSyncState`
- `photoSyncState`
- `safeForRewarding`
- `savedLocally`
- `synced`
- `modelMetadataJson`
- `classificationSource`
- `localModelVersion`
- verification and collectible bookkeeping fields
- org-sharing status fields

Additional reward-tracking state is stored in `KarmaEvent`, which was extended so biodiversity events can carry:

- `userId`
- `walletPublicKey`
- `collectibleStatus`
- `collectibleId`
- `verificationTxSignature`
- `verifiedAt`

Room setup:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/db/AppDatabase.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/db/AppDatabase.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/db/Daos.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/db/Daos.kt)

Current DB version is `11`.

Important note:

- The app currently uses `fallbackToDestructiveMigration()`. This is acceptable for the hackathon branch but means schema changes can wipe local state on upgrade.

### 3. Location integrity hardening

One of the most important fixes in this branch was removing fake biodiversity coordinates.

Previous prototype behavior could fall back to `0.0, 0.0`, which is unacceptable for biodiversity data because latitude and longitude are part of the scientific value of the record.

Current behavior:

- location is requested from fused last-known location
- if location exists, store `lat`, `lon`, `accuracyMeters`, and `locationSource = fused_last_known`
- if location is unavailable, store `lat = null`, `lon = null`, and `locationSource = missing`
- records with missing coordinates are not marked relayable
- data-sharing state is downgraded to reflect missing location quality

This affects:

- what gets stored locally
- whether BLE metadata is created
- whether the record is treated as export-ready biodiversity data
- what sync payload is sent to the backend

This is one of the highest-value correctness changes in the branch.

### 4. On-device acoustic inference

The Android app now supports bundled or sideloaded biodiversity model packs:

- [android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt)

Runtime search order for model bundles:

- `filesDir/biodiversity_model/`
- `getExternalFilesDir(null)/biodiversity_model/`
- `app/src/main/assets/biodiversity/`

Pack contents support:

- `perch_encoder.tflite`
- linear head weights and bias
- classifier class labels
- prototype bank
- prototype embeddings
- label metadata
- optional future explainer assets

Current inference stack on Android:

- TFLite Perch encoder when a full pack is present
- linear classification head over embeddings
- prototype retrieval against the exported prototype bank
- deterministic open-world decision logic
- deterministic one-sentence explanation generation on device

Important current behavior:

- the app no longer requires backend classification to complete the main capture flow
- if the full model bundle is missing, Android falls back to a lightweight heuristic path so the UX still works
- final labels remain constrained by deterministic rules on device
- the optional on-device LLM explainer pack is documented/exportable, but not the primary runtime path for demo stability

### 5. Local inference worker and sync worker

Workers:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversitySyncWorker.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversitySyncWorker.kt)

Local inference worker responsibilities:

- find pending biodiversity clips
- load the local audio clip
- run on-device inference
- build top-K candidate payload
- store final decision, confidence, explanation, and metadata
- queue cloud sync afterward

Sync worker responsibilities:

- upload already-classified observations to backend using `POST /api/biodiversity/audio-sync`
- upload attached photos using `POST /api/biodiversity/photo-link`
- preserve the local-first model by retrying later when offline

Important separation:

- `BiodiversityLocalInferenceWorker` does classification
- `BiodiversitySyncWorker` mirrors the resulting record to backend

This means the core UX remains operational offline.

### 6. BLE behavior

BLE relay behavior is intentionally compact and file-free.

Relay payload is built in:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/BiodiversityRepository.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/BiodiversityRepository.kt)

Relayable biodiversity payload includes:

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

Non-negotiable constraints enforced by implementation:

- raw audio is not relayed over BLE
- photos are not relayed over BLE
- events without usable coordinates are not marked relayable

### 7. Reward and collectible bookkeeping

The branch adds the database shape and UI plumbing needed for biodiversity rewards and future collectibles.

Current app behavior:

- if an observation is classified as safe for rewarding, saving it creates a local `KarmaEvent`
- biodiversity contributions carry verification and collectible lifecycle fields
- UI surfaces pending, verified, or ineligible collectible state
- profile and history surfaces now summarize biodiversity reward progress

Important limitation:

- the branch stores the lifecycle state needed for blockchain-backed verification and collectibles, but it does not complete a full production on-chain biodiversity mint/verification flow from this screen
- the current feature is ready to store, display, sync, and later update those records when verification occurs

### 8. UX integration across the app

The biodiversity feature is no longer a sidecar screen. It is integrated into the broader app experience.

Integrated UI surfaces include:

- biodiversity capture screen
- map entry points
- history view
- profile statistics
- status chips for local save, sync, photo, reward, and collectible progress

Relevant files:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureScreen.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/history/ReportHistoryScreen.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/history/ReportHistoryScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/profile/ProfileScreen.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/profile/ProfileScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/BiodiversityUiLabels.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/BiodiversityUiLabels.kt)

Notable UX improvements:

- clearer single entry point for recording
- stronger “offline first” messaging
- explicit lifecycle/status presentation
- visible contributor identity
- visible location quality and missing-location warnings
- visible collectible and partner-sharing state
- local biodiversity ledger section for saved observations

### 9. Backend endpoints and cloud mirror

Primary backend files:

- [backend/app.py](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/app.py)
- [backend/acoustic.py](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/acoustic.py)
- [backend/postprocess.py](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/postprocess.py)
- [backend/storage.py](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/storage.py)
- [backend/databricks_mirror.py](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/databricks_mirror.py)
- [backend/README.md](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/README.md)

Implemented endpoints:

`POST /api/biodiversity/audio`

- upload raw clip
- run backend acoustic inference
- run backend post-processing
- return top-K acoustic candidates, final label, taxonomic level, confidence band, explanation, reward safety, and metadata

`POST /api/biodiversity/photo-link`

- attach a photo to an existing `observation_id`
- keep that link for later verification

`POST /api/biodiversity/audio-sync`

- ingest an already-classified local observation from the Android app
- store raw audio plus local classification fields
- mirror the event to Databricks-compatible storage

Current cloud-side mirror behavior:

- audio and event JSON are stored locally by the backend
- event data is mirrored to Databricks through `DatabricksMirror`
- the mirror logic was updated to tolerate `NULL` location fields rather than failing on missing coordinates

### 10. Training and export workflow on Brev

Training/export scripts live under:

- [backend/training](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/training)

Implemented workflow covers:

- Brev instance bootstrap
- Perch-Hoplite and Perch setup
- public wildlife audio download and curation
- negative example manifest generation
- audio normalization to mono 16 kHz five-second windows
- embedding generation
- prototype-bank and linear-head training
- local LLM dataset generation
- local fine-tuning support
- Android model pack export
- checkpoint-to-TFLite fallback export path

Notable scripts:

- `brev_bootstrap.sh`
- `brev_instance_setup.sh`
- `prepare_reference_audio.py`
- `embed_reference_audio.py`
- `train_open_world_head.py`
- `generate_impulse_jsonl.py`
- `finetune_local_llm.py`
- `export_android_model_pack.py`
- `export_perch_checkpoint_to_tflite.py`
- `export_android_model_pack_brev.sh`
- `export_android_explainer_pack.sh`

Current artifact summary from the backend docs:

- `796` reference windows embedded
- `169` learned classes in the linear head
- `700` prototype-bank entries

### 11. LLM / explanation path status

The original intended stack included an Impulse-hosted fine-tuned LLM. That was blocked operationally, so the branch shifted toward Brev-hosted or deterministic alternatives while preserving the architecture boundary.

What exists now:

- backend post-processing abstraction in [backend/postprocess.py](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/postprocess.py)
- local fine-tuning scripts and training-data generation
- deterministic rules when the local model is not configured
- deterministic one-sentence on-device wording for Android stability

Important current truth:

- the LLM is not the primary classifier
- labels remain bounded by acoustic candidates and deterministic logic
- explanation wording can be generated locally on backend or deterministically on device
- the mobile demo path does not depend on a remote LLM service

### 12. Databricks and biodiversity data-sharing posture

The branch now preserves the fields needed for future biodiversity data products:

- who contributed the event
- where it was observed
- when it was observed
- what was likely detected
- confidence and taxonomic level
- whether a photo was linked
- whether a collectible or verification event was attached
- whether the record has been mirrored and whether it is ready for partner sharing

This is important because biodiversity data is only useful to outside orgs if provenance and location integrity are preserved. The current data model supports that.

Current `dataShareStatus` states used by the flow include:

- `local_only`
- `captured_local`
- `classification_ready`
- `ready_local`
- `location_missing`
- `mirrored_cloud`
- `mirrored_cloud_missing_location`

### 13. Known limitations

These are the main deliberate shortcuts or still-open items in the branch:

- no production on-chain biodiversity verification or mint pipeline from the capture flow yet
- no production migration strategy for Room schema changes
- on-device explainer LLM pack is optional and not the primary mobile runtime
- backend `POST /api/biodiversity/audio` still expects required `lat` and `lon`, while Android’s main happy path now prefers local classification and nullable location for sync
- fused last-known location is used, not an active fresh location request
- photo attachment is link-only today and does not run image classification
- end-to-end scientific validation is demo-grade, not benchmark-grade

### 14. Demo-ready story

As of this branch, the demo story is:

1. Record a trail sound in the Android app.
2. Store the clip immediately on device.
3. Classify locally on the phone with the bundled biodiversity model pack when available.
4. Show a species/genus/family decision with confidence wording and a one-sentence explanation.
5. Save the event into the local biodiversity ledger.
6. Attach a photo to the same `observationId`.
7. Show pending reward and collectible-related state in the UI.
8. Sync the full record later when network exists.
9. Relay only compact metadata over BLE.

### 15. Files worth reading first

### 15. Android validation on 2026-04-19

The Android biodiversity flow was exercised on the local emulator using the repo’s Android testing workflow.

Automated signal that passed:

- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=fyi.acmc.trailkarma.BiodiversityFlowSmokeTest`

What that smoke covers:

- app launch
- navigation into the biodiversity screen
- biodiversity screen rendering
- tapping the `Record Trail Sound` CTA
- observing the recording state transition
- confirming the screen remains alive after the recording cycle

Runtime issues found during testing and fixed in this branch:

- `BiodiversityRepository` crashed when constructing a Moshi adapter for the Kotlin `RelayableBiodiversityPayload` data class because it used a plain `Moshi.Builder().build()` without `KotlinJsonAdapterFactory`
- `LocalBiodiversityInferenceEngine` failed for the same reason when parsing `ModelManifest`
- the biodiversity smoke test itself was stabilized using `UiAutomator` waits for the record-button state transition, because Compose-only idling around background workers was too brittle for this flow

Files changed as a direct result of testing:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/BiodiversityRepository.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/BiodiversityRepository.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt)
- [android_app/app/build.gradle.kts](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/build.gradle.kts)
- [android_app/app/src/androidTest/java/fyi/acmc/trailkarma/BiodiversityFlowSmokeTest.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/androidTest/java/fyi/acmc/trailkarma/BiodiversityFlowSmokeTest.kt)

Artifacts collected during Android testing:

- `.artifacts/android-smoke/failure1/`
- `.artifacts/android-smoke/final/`

One broader existing test remains flaky:

- `fyi.acmc.trailkarma.SmokeNavigationTest`

Observed failure:

- `No compose hierarchies found in the app`

That failure does not point directly at the biodiversity code path and should be handled separately from the bioacoustics branch-specific runtime fixes above.

### 16. Files worth reading first

If someone needs to continue the feature, start here:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureScreen.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureViewModel.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/ui/biodiversity/BiodiversityCaptureViewModel.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/BiodiversityRepository.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/repository/BiodiversityRepository.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversitySyncWorker.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversitySyncWorker.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/models/Models.kt](/Users/suraj/Desktop/dhacks/bioacoustics-ai/android_app/app/src/main/java/fyi/acmc/trailkarma/models/Models.kt)
- [backend/app.py](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/app.py)
- [backend/README.md](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/README.md)
- [backend/training/export_android_model_pack.py](/Users/suraj/Desktop/dhacks/bioacoustics-ai/backend/training/export_android_model_pack.py)
