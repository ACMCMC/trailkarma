from __future__ import annotations

import hashlib
import json
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from dotenv import load_dotenv

from .acoustic import AcousticPipeline
from .databricks_mirror import DatabricksMirror
from .photo_verification import GeminiPhotoVerifier
from .postprocess import LocalPostProcessor
from .schemas import (
    AudioInferenceResponse,
    AudioSyncResponse,
    PhotoLinkResponse,
    PhotoVerificationResponse,
)
from .storage import EventStore


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
ARTIFACT_DIR = BASE_DIR / "artifacts"

load_dotenv()

app = FastAPI(title="TrailKarma Biodiversity API", version="0.1.0")
event_store = EventStore(DATA_DIR)
acoustic_pipeline = AcousticPipeline(ARTIFACT_DIR)
postprocessor = LocalPostProcessor()
databricks_mirror = DatabricksMirror()
photo_verifier = GeminiPhotoVerifier()


@app.get("/health")
def healthcheck() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/biodiversity/audio", response_model=AudioInferenceResponse)
async def classify_audio(
    audio: UploadFile = File(...),
    lat: float = Form(...),
    lon: float = Form(...),
    timestamp: str = Form(...),
    observation_id: str = Form(...),
) -> AudioInferenceResponse:
    suffix = Path(audio.filename or "clip.wav").suffix or ".wav"
    audio_bytes = await audio.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="audio payload is empty")

    audio_path = event_store.save_audio(observation_id, audio_bytes, suffix=suffix)
    acoustic_result = acoustic_pipeline.run(audio_path, lat=lat, lon=lon, timestamp=timestamp)
    llm_decision, llm_metadata = postprocessor.decide(acoustic_result.payload)

    response = AudioInferenceResponse(
        topK_acoustic_candidates=acoustic_result.payload.top_candidates,
        finalLabel=llm_decision.finalLabel,
        finalTaxonomicLevel=llm_decision.finalTaxonomicLevel,
        confidence=round(acoustic_result.confidence, 4),
        confidenceBand=llm_decision.confidenceBand,
        explanation=llm_decision.explanation,
        safeForRewarding=llm_decision.safeForRewarding,
        model_metadata={
            **acoustic_result.model_metadata,
            "llm": llm_metadata,
        },
    )

    event = event_store.merge_event(
        observation_id,
        {
            "observation_id": observation_id,
            "audio_path": str(audio_path),
            "timestamp": timestamp,
            "lat": lat,
            "lon": lon,
            **response.model_dump(mode="json"),
            "verification_status": "provisional",
        },
    )
    mirror_status = databricks_mirror.mirror_event(event)
    response.model_metadata["databricks_mirror"] = mirror_status
    event_store.save_event(observation_id, event | {"model_metadata": response.model_metadata})
    return response


@app.post("/api/biodiversity/photo-link", response_model=PhotoLinkResponse)
async def link_photo(
    observation_id: str = Form(...),
    photo: UploadFile | None = File(default=None),
    photo_uri: str | None = Form(default=None),
) -> PhotoLinkResponse:
    existing = event_store.load_event(observation_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="observation_id not found")

    stored_photo_uri: str | None = photo_uri
    if photo is not None:
        suffix = Path(photo.filename or "photo.jpg").suffix or ".jpg"
        photo_bytes = await photo.read()
        stored_photo_uri = str(event_store.save_photo(observation_id, photo_bytes, suffix=suffix))

    if not stored_photo_uri:
        raise HTTPException(status_code=400, detail="photo or photo_uri is required")

    event = event_store.merge_event(
        observation_id,
        {
            "photo_uri": stored_photo_uri,
            "verification_status": "provisional",
        },
    )
    databricks_mirror.mirror_event(event)
    return PhotoLinkResponse(success=True)


@app.post("/api/biodiversity/photo-verify", response_model=PhotoVerificationResponse)
async def verify_photo_claim(
    observation_id: str = Form(...),
    claimed_label: str = Form(...),
    timestamp: str = Form(...),
    user_id: str = Form(...),
    observer_display_name: str | None = Form(default=None),
    observer_wallet_public_key: str | None = Form(default=None),
    lat: float | None = Form(default=None),
    lon: float | None = Form(default=None),
    location_accuracy_meters: float | None = Form(default=None),
    location_source: str = Form(default="missing"),
    photo: UploadFile = File(...),
) -> PhotoVerificationResponse:
    cleaned_claim = claimed_label.strip()
    if not cleaned_claim:
        raise HTTPException(status_code=400, detail="claimed_label is required")

    suffix = Path(photo.filename or "photo.jpg").suffix or ".jpg"
    photo_bytes = await photo.read()
    if not photo_bytes:
        raise HTTPException(status_code=400, detail="photo payload is empty")

    stored_photo_uri = str(event_store.save_photo(observation_id, photo_bytes, suffix=suffix))
    mime_type = photo.content_type or "image/jpeg"

    try:
        decision, verifier_metadata = photo_verifier.verify(
            observation_id=observation_id,
            claimed_label=cleaned_claim,
            photo_bytes=photo_bytes,
            mime_type=mime_type,
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    matched_claim = bool(decision.get("matchedClaim"))
    detected_label = decision.get("detectedLabel")
    final_label = cleaned_claim if matched_claim else (detected_label or cleaned_claim)
    final_taxonomic_level = decision.get("detectedTaxonomicLevel") or ("species" if matched_claim else "unknown")
    confidence = round(float(decision.get("confidence") or 0.0), 4)
    confidence_band = str(decision.get("confidenceBand") or "low")
    explanation = str(decision.get("explanation") or "No verification explanation returned.")
    animal_present = bool(decision.get("animalPresent"))

    is_unique_species = False
    reward_amount = 0
    collectible_status = "not_eligible"
    collectible_id: str | None = None
    collectible_name: str | None = None
    collectible_image_uri: str | None = None
    verification_status = "rejected"

    if matched_claim:
        verification_status = "verified"
        is_unique_species = not event_store.has_verified_species_label(
            final_label,
            exclude_observation_id=observation_id,
        )
        reward_amount = 13 if is_unique_species else 8
        collectible_status = "verified" if is_unique_species else "duplicate_species"
        collectible_name = final_label
        if is_unique_species:
            collectible_id = f"species:{slugify(final_label)}"
            collectible_image_uri = build_collectible_gradient(final_label)

    response = PhotoVerificationResponse(
        claimedLabel=cleaned_claim,
        finalLabel=final_label,
        finalTaxonomicLevel=final_taxonomic_level,
        detectedLabel=detected_label,
        matchedClaim=matched_claim,
        animalPresent=animal_present,
        confidence=confidence,
        confidenceBand=confidence_band,
        explanation=explanation,
        verificationStatus=verification_status,
        isUniqueSpecies=is_unique_species,
        rewardAmount=reward_amount,
        collectibleStatus=collectible_status,
        collectibleId=collectible_id,
        collectibleName=collectible_name,
        collectibleImageUri=collectible_image_uri,
        model_metadata=verifier_metadata,
    )

    event = event_store.merge_event(
        observation_id,
        {
            "observation_id": observation_id,
            "user_id": user_id,
            "observer_display_name": observer_display_name,
            "observer_wallet_public_key": observer_wallet_public_key,
            "timestamp": timestamp,
            "lat": lat,
            "lon": lon,
            "location_accuracy_meters": location_accuracy_meters,
            "location_source": location_source,
            "photo_uri": stored_photo_uri,
            "claimed_label": cleaned_claim,
            "finalLabel": final_label,
            "finalTaxonomicLevel": final_taxonomic_level,
            "confidence": confidence,
            "confidenceBand": confidence_band,
            "explanation": explanation,
            "verification_status": verification_status,
            "collectible_status": collectible_status,
            "collectible_id": collectible_id,
            "collectible_name": collectible_name,
            "collectible_image_uri": collectible_image_uri,
            "reward_amount": reward_amount,
            "safeForRewarding": matched_claim,
            "model_metadata": response.model_metadata,
        },
    )
    mirror_status = databricks_mirror.mirror_event(event)
    event_store.save_event(
        observation_id,
        event | {"model_metadata": {**response.model_metadata, "databricks_mirror": mirror_status}},
    )
    return response


@app.post("/api/biodiversity/audio-sync", response_model=AudioSyncResponse)
async def sync_local_audio_observation(
    audio: UploadFile = File(...),
    lat: float | None = Form(default=None),
    lon: float | None = Form(default=None),
    location_accuracy_meters: float | None = Form(default=None),
    location_source: str = Form(...),
    timestamp: str = Form(...),
    observation_id: str = Form(...),
    user_id: str = Form(...),
    observer_display_name: str | None = Form(default=None),
    observer_wallet_public_key: str | None = Form(default=None),
    final_label: str = Form(...),
    final_taxonomic_level: str = Form(...),
    confidence: float = Form(...),
    confidence_band: str = Form(...),
    explanation: str = Form(...),
    safe_for_rewarding: bool = Form(...),
    verification_status: str = Form(...),
    verification_tx_signature: str | None = Form(default=None),
    verified_at: str | None = Form(default=None),
    collectible_status: str = Form(...),
    collectible_id: str | None = Form(default=None),
    collectible_name: str | None = Form(default=None),
    collectible_image_uri: str | None = Form(default=None),
    data_share_status: str = Form(...),
    shared_with_org_at: str | None = Form(default=None),
    top_k_json: str = Form(...),
    model_metadata_json: str = Form(...),
    classification_source: str = Form(...),
    local_model_version: str = Form(...),
) -> AudioSyncResponse:
    suffix = Path(audio.filename or "clip.wav").suffix or ".wav"
    audio_bytes = await audio.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="audio payload is empty")

    audio_path = event_store.save_audio(observation_id, audio_bytes, suffix=suffix)
    event = event_store.merge_event(
        observation_id,
        {
            "observation_id": observation_id,
            "user_id": user_id,
            "observer_display_name": observer_display_name,
            "observer_wallet_public_key": observer_wallet_public_key,
            "audio_path": str(audio_path),
            "timestamp": timestamp,
            "lat": lat,
            "lon": lon,
            "location_accuracy_meters": location_accuracy_meters,
            "location_source": location_source,
            "topK_acoustic_candidates": json.loads(top_k_json),
            "finalLabel": final_label,
            "finalTaxonomicLevel": final_taxonomic_level,
            "confidence": confidence,
            "confidenceBand": confidence_band,
            "explanation": explanation,
            "safeForRewarding": safe_for_rewarding,
            "verification_status": verification_status,
            "verification_tx_signature": verification_tx_signature,
            "verified_at": verified_at,
            "collectible_status": collectible_status,
            "collectible_id": collectible_id,
            "collectible_name": collectible_name,
            "collectible_image_uri": collectible_image_uri,
            "data_share_status": data_share_status,
            "shared_with_org_at": shared_with_org_at,
            "model_metadata": {
                **(json.loads(model_metadata_json)),
                "classification_source": classification_source,
                "local_model_version": local_model_version,
            },
        },
    )
    databricks_mirror.mirror_event(event)
    return AudioSyncResponse(success=True)


def slugify(value: str) -> str:
    return "-".join("".join(ch.lower() if ch.isalnum() else " " for ch in value).split())


def build_collectible_gradient(label: str) -> str:
    digest_a = hashlib.sha256(label.encode("utf-8")).hexdigest()
    digest_b = hashlib.sha256(label[::-1].encode("utf-8")).hexdigest()
    color_a = f"#{digest_a[:6].upper()}"
    color_b = f"#{digest_b[:6].upper()}"
    return f"gradient:{color_a}:{color_b}"
