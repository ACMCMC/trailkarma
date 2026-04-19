# TrailKarma Track Application Guide

This document maps TrailKarma to every sponsor track we plan to apply for. It is written as a submission-facing engineering brief: what the track is asking for, how our app satisfies it, which parts are already implemented in the repo, and what story we should tell judges in a demo.

## How To Read This

Not every track maps to the same layer of the project.

- Some tracks are direct implementation fits.
  These are already visible in the shipped app, backend, or on-chain stack.
- Some tracks are workflow fits.
  These depend on how we trained, documented, or packaged the system rather than on the mobile runtime alone.
- Some tracks are deployment fits.
  These depend on where we host the public demo stack.

Where a track is weaker or depends on packaging outside the repo, this document says so explicitly. The goal is to be detailed without overclaiming.

## App Architecture In One Paragraph

TrailKarma is an offline-first hiking app. The Android client stores reports, biodiversity observations, relay jobs, location history, and wallet state locally. Phones exchange reports and relay packets over BLE when there is no signal. When connectivity returns, the app syncs reports, locations, biodiversity events, and relay metadata to Databricks-backed services. Solana is used as a narrow settlement layer for KARMA rewards, badge ownership, relay-job uniqueness, and tipping. AI appears in three places: on-device biodiversity audio inference, Gemini-backed photo verification, and ElevenLabs-backed delayed voice relay calls.

## Track Summary

| Track | Fit Strength | Why We Fit |
| --- | --- | --- |
| Best Use of Marimo/Sphinx | Workflow fit | We have a real Marimo notebook for reproducible species-dataset creation and a structured docs set that can anchor Sphinx-style documentation. |
| Best Use of Scripps Data | Strong fit | Our biodiversity, trail safety, and species-reporting pipeline uses the Southern California species dataset as seeded training/report data. |
| Best Use of Databricks | Strong fit | Databricks is the cloud analytics and sync backbone for reports, trails, biodiversity mirroring, H3 indexing, and warehouse queries. |
| Best Use of Edge-AI (Qualcomm) | Strong fit | Core biodiversity intelligence runs on-device in the Android app using a bundled TFLite model pack. |
| Best Models Trained on Impulse AI Platform | Workflow fit | Our model-development story includes an Impulse-ready training pipeline and a weather/snake-risk narrative tied to our trail safety layer. |
| Best Use of Nvidia Brev.dev | Strong fit | Brev is used for the heavier biodiversity training/export workflow that produces the Android model artifacts. |
| Most Innovative Idea (The Basement) | Strong fit | The product idea itself is unusually strong: offline trail mutual aid, BLE message carrying, biodiversity logging, and on-chain social rewards in one system. |
| Best Use of Gemini API | Strong fit | Gemini verifies typed species claims from photos directly in the Android app and backend support code. |
| Best Use of ElevenLabs | Strong fit | ElevenLabs powers the voice relay path for delayed check-in and emergency-style message delivery. |
| Best Use of Solana | Strong fit | Solana secures reward issuance, relay-job uniqueness, badge ownership, and KARMA tipping without requiring users to hold SOL. |
| Best Use of DigitalOcean | Deployment fit | Our app already expects hosted public API endpoints for release builds, and the services are separable enough to deploy cleanly on DigitalOcean infrastructure. |

## 1. Best Use of Marimo/Sphinx

### What The Track Wants

This track rewards ML/data-science development done in Marimo notebooks with clear, reproducible documentation that other practitioners can follow.

### How TrailKarma Fits

Our biodiversity model-development workflow already has a real Marimo notebook entrypoint:

- [data_pipeline.py](../data_pipeline.py)

That file is a Marimo notebook generated with:

- `__generated_with = "0.10.14"`

The notebook:

- defines target Pacific Crest Trail species
- queries iNaturalist observations
- downloads labeled images
- builds a training manifest
- explicitly prepares the dataset for Impulse AI ingestion

It even calls out challenge coverage inside the notebook itself:

- `Best Use of Marimo/Sphinx`
- `Best Models Trained on Impulse AI Platform`

### Implementation Details To Highlight

- The Marimo notebook is not a toy notebook; it is part of the real data pipeline that converts biodiversity observations into a trainable species dataset.
- The downstream training/export chain is represented in the repo through the `backend/training/` scripts and the Android model pack in `android_app/app/src/main/assets/biodiversity/`.
- Our broader documentation layer is already structured in markdown across:
  - [README.md](../README.md)
  - [docs/bioacoustics.md](./bioacoustics.md)
  - [docs/REWARDS.md](./REWARDS.md)
  - [ANDROID_DATABRICKS_SYNC.md](../ANDROID_DATABRICKS_SYNC.md)
  - this track guide

### Best Submission Framing

The strongest honest framing is:

- Marimo is already part of the real model/data workflow.
- Our documentation set is strong and reproducible.
- The Sphinx part should be presented as the publishing/documentation layer for this existing notebook + docs set.

### Caveat

The repo currently contains the Marimo notebook and the docs content, but not a committed Sphinx `conf.py` / build scaffold. We should describe this as a documentation workflow fit, not as a claim that the app itself depends on Sphinx at runtime.

## 2. Best Use of Scripps Data

### What The Track Wants

This track rewards meaningful use of the provided dataset for analysis, modeling, visualization, or decision-making.

### How TrailKarma Fits

The most direct fit is our species and biodiversity layer. We use the species dataset to enrich the hiking app with:

- species reports on the trail
- biodiversity training inputs
- biodiversity mirroring into Databricks
- safety-relevant species awareness, including snakes

Key repo evidence:

- [data/observations-712152.csv](../data/observations-712152.csv)
- [setup_databricks.py](../setup_databricks.py)
- [inaturalist_sync_job.py](../inaturalist_sync_job.py)

### Implementation Details To Highlight

- `setup_databricks.py` reads the species CSV and seeds Southern California species reports into `workspace.trailkarma.trail_reports`.
- The seed script filters observations geographically and attaches H3 spatial cells so biodiversity data becomes queryable in the same analytics system as hazards, water, and route activity.
- The seeded dataset is not isolated; it becomes part of the app’s shared trail intelligence layer.
- The species dataset also supports training and verification workflows for biodiversity contributions and safety context.

### Why This Is Strong

This is not “we plotted a dataset once.” The species data is used as part of:

- training-data generation
- cloud seeding
- live report context
- biodiversity analytics
- safety-oriented environmental awareness

## 3. Best Use of Databricks

### What The Track Wants

This track rewards meaningful use of Databricks for heavy data processing, analytics, and scalable cloud workflows.

### How TrailKarma Fits

Databricks is one of the core pillars of the system, not an accessory.

Key files:

- [setup_databricks.py](../setup_databricks.py)
- [DATABRICKS_SETUP.md](../DATABRICKS_SETUP.md)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/DatabricksSyncRepository.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/repository/DatabricksSyncRepository.kt)
- [backend/databricks_mirror.py](../backend/databricks_mirror.py)
- [backend/src/biodiversity.ts](../backend/src/biodiversity.ts)
- [web-backend/main.py](../web-backend/main.py)

### Implementation Details To Highlight

- Android pushes unsynced reports, locations, and relay packets directly into Databricks SQL Warehouse.
- Databricks computes H3 cells server-side during ingestion so the mobile app does not need on-device geospatial indexing libraries.
- The app also pulls community reports and trail geometry back out of Databricks into Room.
- Biodiversity observations can be mirrored into `workspace.trailkarma.biodiversity_events`.
- The setup flow seeds:
  - trails
  - trail segments
  - trail waypoints
  - reports
  - species observations
  - weather cache
  - relay packets
- There is also a Databricks job path for refreshing iNaturalist observations.

### Why This Is Strong

We are using Databricks for:

- ingestion
- storage
- H3-based analytics
- scheduled syncing
- biodiversity mirroring
- trail metadata serving
- web-facing query APIs

This is one of our strongest tracks.

## 4. Best Use of Edge-AI (Qualcomm)

### What The Track Wants

This track rewards embedded intelligence that runs locally on edge devices, with emphasis on system design, efficiency, and practical utility.

### How TrailKarma Fits

Our biodiversity-audio pipeline is designed around local inference on Android.

Key files:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/inference/LocalBiodiversityInference.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/sync/BiodiversityLocalInferenceWorker.kt)
- [android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md](../android_app/LOCAL_BIODIVERSITY_MODEL_PACK.md)
- [android_app/app/src/main/assets/biodiversity/model_manifest.json](../android_app/app/src/main/assets/biodiversity/model_manifest.json)
- [android_app/app/build.gradle.kts](../android_app/app/build.gradle.kts)

### Implementation Details To Highlight

- The Android app records a 5-second environmental clip locally.
- Inference runs on-device through a TFLite-based model pack.
- The model pack includes:
  - `perch_encoder.tflite`
  - linear classifier weights
  - prototype embeddings
  - label metadata
- The app stores the result locally, attaches location context, and only syncs later.
- The system still works when there is no internet.
- The app includes a heuristic fallback path if the full model pack is unavailable.

### Why This Is Strong

This is real edge AI, not cloud inference wrapped in a mobile UI. The classification logic runs on the phone and is useful exactly in the environment where connectivity is unreliable: on remote trails.

## 5. Best Models Trained on Impulse AI Platform

### What The Track Wants

This track rewards creative, practical model training and deployment using Impulse.

### How TrailKarma Fits

This is primarily a model-development workflow track for us.

Strong repo evidence:

- [data_pipeline.py](../data_pipeline.py)
- [backend/training/generate_impulse_jsonl.py](../backend/training/generate_impulse_jsonl.py)
- [backend/training/finetune_local_llm.py](../backend/training/finetune_local_llm.py)
- [setup_databricks.py](../setup_databricks.py)

### Implementation Details To Highlight

- The Marimo notebook creates an Impulse-ready manifest for species image classification.
- The notebook explicitly describes the Impulse flow:
  - upload manifest + images
  - create a classification impulse
  - train with Impulse AutoML
  - export TFLite for Android
- The broader safety model story includes our weather-aware `snake_risk` schema in Databricks through `weather_cache`.
- Our species and snake-focused data sources make the model story directly useful for hikers, not generic wildlife ML.

### Best Submission Framing

For judges, the strongest framing is:

- Impulse is the training/deployment workflow for safety and biodiversity models.
- The Android app is the runtime consumer of those compact model artifacts.
- The point of the model is practical risk awareness on remote trails.

### Caveat

The repo has the Impulse-ready dataset pipeline and the schema hooks for weather/snake-risk scoring, but the full snake-risk training code is not as explicit in-repo as the biodiversity model-export path. We should present this as a model-workflow fit supported by real data plumbing, not as a claim that every training step lives in this repository.

## 6. Best Use of Nvidia Brev.dev

### What The Track Wants

This track rewards meaningful use of Brev in the training, infrastructure, or deployment path.

### How TrailKarma Fits

Our biodiversity training/export pipeline explicitly uses Brev for the heavier ML work.

Key files:

- [backend/training/brev_bootstrap.sh](../backend/training/brev_bootstrap.sh)
- [backend/training/brev_instance_setup.sh](../backend/training/brev_instance_setup.sh)
- [backend/training/brev_gpu_env.sh](../backend/training/brev_gpu_env.sh)
- [backend/training/train_open_world_head.py](../backend/training/train_open_world_head.py)
- [backend/training/export_android_model_pack.py](../backend/training/export_android_model_pack.py)
- [backend/training/export_perch_checkpoint_to_tflite.py](../backend/training/export_perch_checkpoint_to_tflite.py)

### Implementation Details To Highlight

- Brev is used to bootstrap the model-training environment.
- The training path includes:
  - reference-audio preparation
  - embedding generation
  - classifier-head training
  - prototype-bank creation
  - optional local LLM fine-tuning
  - Android model-pack export
- The output of the Brev workflow is not academic only; it is packaged into assets consumed by the Android app.

### Why This Is Strong

Brev is part of the production path from raw training data to mobile-deployable model artifacts. That is exactly the kind of meaningful infrastructure use this track wants.

## 7. Most Innovative Idea (The Basement)

### What The Track Wants

This track rewards originality and visionary impact, even independent of raw technical complexity.

### How TrailKarma Fits

This is arguably our most naturally compelling track.

### The Core Innovation

TrailKarma combines systems that are usually built separately:

- offline-first trail reporting
- phone-to-phone BLE data carriage
- delayed emergency/check-in message relay
- biodiversity citizen science
- on-device wildlife intelligence
- public, verifiable social rewards on Solana

### Why That Matters

Most hiking apps assume connectivity.
Most citizen-science apps assume deliberate uploads.
Most blockchain reward systems assume crypto-native users.

TrailKarma’s core innovation is the composition:

- helpful behavior happens offline
- the community physically carries information forward
- biodiversity observations become safety and research data
- the reward layer creates a social incentive to contribute

### Best Demo Story

The strongest judge-facing one-liner is:

“We turned the trail itself into a delay-tolerant social network, where helping the next hiker and contributing biodiversity data are rewarded in a way that ordinary users can understand.”

## 8. Best Use of Gemini API

### What The Track Wants

This track rewards useful Gemini-powered AI functionality that bridges difficult information and practical user action.

### How TrailKarma Fits

Gemini is used for species-photo verification inside the Android app.

Key files:

- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/camera/CameraViewModel.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/ui/camera/CameraViewModel.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/camera/CameraScreen.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/ui/camera/CameraScreen.kt)
- [backend/photo_verification.py](../backend/photo_verification.py)
- [android_app/app/build.gradle.kts](../android_app/app/build.gradle.kts)

### Implementation Details To Highlight

- A user can take or upload a trail photo.
- The user types the species they believe is present.
- Gemini checks whether that claimed species is actually visible.
- The response is structured and constrained:
  - `matchedClaim`
  - `animalPresent`
  - `detectedLabel`
  - `detectedTaxonomicLevel`
  - `confidence`
  - `confidenceBand`
  - `explanation`
- Verified matches can:
  - create a biodiversity contribution
  - create a species trail report
  - trigger rewards and collectible state
  - mirror the event to Databricks

### Why This Is Strong

Gemini is not being used as a generic chatbot. It is used as a structured verification component inside a larger biodiversity workflow, tied directly to rewardability, uniqueness checks, and downstream data quality.

## 9. Best Use of ElevenLabs

### What The Track Wants

This track rewards meaningful use of realistic AI audio generation or voice interaction.

### How TrailKarma Fits

ElevenLabs powers our delayed voice relay system.

Key files:

- [backend/src/voiceRelay.ts](../backend/src/voiceRelay.ts)
- [backend/src/server.ts](../backend/src/server.ts)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/ui/ble/BleScreen.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/repository/RewardsRepository.kt)

### Implementation Details To Highlight

- The sender creates a signed relay intent while offline.
- That relay packet can move phone-to-phone over BLE.
- When any carrier device gets connectivity, the backend opens the relay job and can initiate an ElevenLabs-powered outbound call.
- The voice agent is tightly scoped:
  - introduce itself as TrailKarma Relay
  - deliver the hiker’s message faithfully
  - include location/callback context when available
  - capture a short reply for later delivery
- Replies are stored and can be carried back through the mesh.

### Why This Is Strong

This is a rare use of AI voice that is both emotionally intuitive and operationally useful. It is not novelty narration. It solves a real backcountry communication gap.

## 10. Best Use of Solana

### What The Track Wants

This track rewards real use of Solana as a fast, scalable settlement layer.

### How TrailKarma Fits

Solana is a real core component of our reward architecture.

Key files:

- [solana/programs/trail_karma_rewards/src/lib.rs](../solana/programs/trail_karma_rewards/src/lib.rs)
- [backend/src/solana/client.ts](../backend/src/solana/client.ts)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/wallet/WalletManager.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/wallet/WalletManager.kt)
- [android_app/app/src/main/java/fyi/acmc/trailkarma/solana/SolanaPayloadCodec.kt](../android_app/app/src/main/java/fyi/acmc/trailkarma/solana/SolanaPayloadCodec.kt)
- [docs/REWARDS.md](./REWARDS.md)

### Implementation Details To Highlight

- Every user gets a local app-managed wallet.
- Users do not need to acquire SOL.
- The backend sponsors transactions.
- Solana is used for:
  - user registration
  - contribution reward receipts
  - relay-job uniqueness
  - first-fulfiller settlement
  - KARMA minting
  - badge ownership
  - signed tipping
- The program already has concrete instructions for relay jobs, rewards, badges, and tipping.
- Devnet assets already exist for KARMA and badge mints.

### Why This Is Strong

This is a disciplined use of blockchain:

- real-world activity stays off-chain
- uniqueness and ownership go on-chain
- ordinary hikers never need to become crypto-native users

That is a much stronger Solana story than “we put everything on chain.”

## 11. Best Use of DigitalOcean

### What The Track Wants

This track rewards stable hosting and scaling on DigitalOcean infrastructure.

### How TrailKarma Fits

This is our weakest purely in-repo track, but we do have a clean deployment story.

Relevant files:

- [android_app/app/build.gradle.kts](../android_app/app/build.gradle.kts)
- [backend/src/server.ts](../backend/src/server.ts)
- [backend/app.py](../backend/app.py)
- [web-backend/main.py](../web-backend/main.py)

### Implementation Details To Highlight

- Release Android builds require real hosted public URLs for:
  - the biodiversity backend
  - the rewards backend
- The services are already cleanly separable:
  - TypeScript rewards/relay backend
  - Python biodiversity backend
  - optional web-facing API layer
- That architecture maps naturally to DigitalOcean App Platform, Droplets, or a mixed deployment strategy.

### Best Submission Framing

If we are applying for this track, the honest framing should be:

- DigitalOcean is the stable public home for our API stack.
- The Android release path already assumes hosted endpoints.
- Our service boundaries are simple and operationally clean.

### Caveat

This track is strongest only if our actual live demo deployment is on DigitalOcean. The repo alone proves deployability, but not the hosting choice.

## Recommended Demo Talking Points Per Track

### Marimo/Sphinx

- Show the Marimo notebook.
- Show that it builds a real training manifest, not a class assignment.
- Show that the docs set explains the entire training-to-mobile path.

### Scripps Data

- Show the species CSV and how it is seeded into Databricks.
- Show biodiversity reports on the map or in the rewards/history UI.

### Databricks

- Show direct SQL-based sync and H3-backed ingestion.
- Show trail reports, trail metadata, and biodiversity mirroring all using the same cloud backbone.

### Edge AI

- Put the phone in airplane mode.
- Record a biodiversity clip.
- Show the local inference result appearing without network access.

### Impulse

- Show the Marimo notebook’s Impulse-ready manifest generation.
- Explain the safety model narrative around species/snake risk and weather-aware trail intelligence.

### Brev

- Walk through the training/export scripts and the Android model-pack output.

### Innovative Idea

- Tell the story: hikers carry messages and hazard data for each other even without signal, and helpful acts become verifiable social rewards.

### Gemini

- Take a photo, type a species label, and show the structured verification result.

### ElevenLabs

- Explain the delayed voice relay flow from offline intent to outbound call to reply capture.

### Solana

- Show the rewards screen and explain that the chain is used for uniqueness and ownership, not for raw hiking data.

### DigitalOcean

- Focus on hosted service boundaries and the fact that release builds require stable public API endpoints.

## Final Positioning

If we have to prioritize what we are strongest on technically, the best tracks are:

1. Best Use of Databricks
2. Best Use of Solana
3. Best Use of ElevenLabs
4. Best Use of Edge-AI (Qualcomm)
5. Most Innovative Idea
6. Best Use of Gemini API
7. Best Use of Nvidia Brev.dev

The workflow/deployment tracks are still worth applying for, but we should frame them carefully:

- Marimo/Sphinx: strong on Marimo, documentation-forward on Sphinx
- Impulse: strong on training workflow, lighter on explicit in-repo model-serving proof
- DigitalOcean: strongest only if our live demo stack is actually hosted there
