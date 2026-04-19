from __future__ import annotations

import json
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from .acoustic import AcousticPipeline
from .databricks_mirror import DatabricksMirror
from .postprocess import LocalPostProcessor
from .schemas import AudioInferenceResponse, AudioSyncResponse, PhotoLinkResponse
from .storage import EventStore


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
ARTIFACT_DIR = BASE_DIR / "artifacts"

app = FastAPI(title="TrailKarma Biodiversity API", version="0.1.0")
event_store = EventStore(DATA_DIR)
acoustic_pipeline = AcousticPipeline(ARTIFACT_DIR)
postprocessor = LocalPostProcessor()
databricks_mirror = DatabricksMirror()


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


@app.post("/api/biodiversity/audio-sync", response_model=AudioSyncResponse)
async def sync_local_audio_observation(
    audio: UploadFile = File(...),
    lat: float = Form(...),
    lon: float = Form(...),
    timestamp: str = Form(...),
    observation_id: str = Form(...),
    final_label: str = Form(...),
    final_taxonomic_level: str = Form(...),
    confidence: float = Form(...),
    confidence_band: str = Form(...),
    explanation: str = Form(...),
    safe_for_rewarding: bool = Form(...),
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
            "audio_path": str(audio_path),
            "timestamp": timestamp,
            "lat": lat,
            "lon": lon,
            "topK_acoustic_candidates": json.loads(top_k_json),
            "finalLabel": final_label,
            "finalTaxonomicLevel": final_taxonomic_level,
            "confidence": confidence,
            "confidenceBand": confidence_band,
            "explanation": explanation,
            "safeForRewarding": safe_for_rewarding,
            "verification_status": "provisional",
            "model_metadata": {
                **(json.loads(model_metadata_json)),
                "classification_source": classification_source,
                "local_model_version": local_model_version,
            },
        },
    )
    databricks_mirror.mirror_event(event)
    return AudioSyncResponse(success=True)
