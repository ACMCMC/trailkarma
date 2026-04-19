from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


TaxonomicLevel = Literal["species", "genus", "family", "unknown"]
ConfidenceBand = Literal["low", "medium", "medium-high", "high"]


class AcousticCandidate(BaseModel):
    label: str
    scientific_name: str | None = None
    taxonomic_level: Literal["species", "genus", "family", "unknown"]
    score: float
    genus: str | None = None
    family: str | None = None


class StructuredCandidatePayload(BaseModel):
    observation_id: str
    top_candidates: list[AcousticCandidate]
    raw_confidence: float
    background_flags: list[str] = Field(default_factory=list)
    lat: float
    lon: float
    timestamp: str
    time_of_day: str | None = None
    region_hint: str | None = None


class LlmDecision(BaseModel):
    finalLabel: str
    finalTaxonomicLevel: TaxonomicLevel
    confidenceBand: ConfidenceBand
    explanation: str
    safeForRewarding: bool


class AudioInferenceResponse(BaseModel):
    topK_acoustic_candidates: list[AcousticCandidate]
    finalLabel: str
    finalTaxonomicLevel: TaxonomicLevel
    confidence: float
    confidenceBand: ConfidenceBand
    explanation: str
    safeForRewarding: bool
    model_metadata: dict[str, Any] = Field(default_factory=dict)


class PhotoLinkResponse(BaseModel):
    success: bool


class AudioSyncResponse(BaseModel):
    success: bool


class PhotoVerificationResponse(BaseModel):
    claimedLabel: str
    finalLabel: str
    finalTaxonomicLevel: TaxonomicLevel
    detectedLabel: str | None = None
    matchedClaim: bool
    animalPresent: bool
    confidence: float
    confidenceBand: ConfidenceBand
    explanation: str
    verificationStatus: str
    isUniqueSpecies: bool
    rewardAmount: int
    collectibleStatus: str
    collectibleId: str | None = None
    collectibleName: str | None = None
    collectibleImageUri: str | None = None
    model_metadata: dict[str, Any] = Field(default_factory=dict)
