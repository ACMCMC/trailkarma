from __future__ import annotations

import base64
import json
import os
from typing import Any
from urllib import error, request


class GeminiPhotoVerifier:
    def __init__(self) -> None:
        self.api_key = os.getenv("GEMINI_API_KEY", "").strip()
        self.model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash").strip() or "gemini-2.5-flash"

    def verify(
        self,
        *,
        observation_id: str,
        claimed_label: str,
        photo_bytes: bytes,
        mime_type: str,
    ) -> tuple[dict[str, Any], dict[str, Any]]:
        if not self.api_key:
            raise RuntimeError("GEMINI_API_KEY is not configured for photo verification.")

        prompt = f"""
You are verifying a biodiversity photo submission for a hiking app.

Observation ID: {observation_id}
Claimed species label: {claimed_label}

Look at the image and decide whether the claimed species is actually visible.

Rules:
- Only set matchedClaim=true when the claimed species is clearly present in the image.
- If the photo is ambiguous, low quality, obstructed, or does not provide enough evidence, set matchedClaim=false.
- Only set animalPresent=true when an animal is actually visible.
- detectedLabel should be your best visible species/common-name guess, or null when you cannot identify one.
- detectedTaxonomicLevel should be species, genus, family, or unknown.
- explanation should be concise and grounded in what is visible.
- confidence must be between 0 and 1.
- confidenceBand must be one of low, medium, medium-high, or high.
- Do not invent certainty that is not present in the image.
        """.strip()

        schema = {
            "type": "object",
            "properties": {
                "matchedClaim": {
                    "type": "boolean",
                    "description": "True only when the claimed species label clearly matches the animal visible in the image.",
                },
                "animalPresent": {
                    "type": "boolean",
                    "description": "True when an animal is actually visible in the image.",
                },
                "detectedLabel": {
                    "type": ["string", "null"],
                    "description": "Best visible species or animal label when identifiable, otherwise null.",
                },
                "detectedTaxonomicLevel": {
                    "type": "string",
                    "enum": ["species", "genus", "family", "unknown"],
                    "description": "Taxonomic precision for detectedLabel.",
                },
                "confidence": {
                    "type": "number",
                    "minimum": 0,
                    "maximum": 1,
                    "description": "Confidence in the verification decision.",
                },
                "confidenceBand": {
                    "type": "string",
                    "enum": ["low", "medium", "medium-high", "high"],
                    "description": "Human-readable confidence bucket.",
                },
                "explanation": {
                    "type": "string",
                    "description": "Short explanation grounded in visible image evidence.",
                },
            },
            "required": [
                "matchedClaim",
                "animalPresent",
                "detectedLabel",
                "detectedTaxonomicLevel",
                "confidence",
                "confidenceBand",
                "explanation",
            ],
            "additionalProperties": False,
        }

        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt},
                        {
                            "inline_data": {
                                "mime_type": mime_type,
                                "data": base64.b64encode(photo_bytes).decode("utf-8"),
                            }
                        },
                    ]
                }
            ],
            "generationConfig": {
                "responseMimeType": "application/json",
                "responseJsonSchema": schema,
                "temperature": 0.1,
            },
        }

        req = request.Request(
            url=f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:generateContent",
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "x-goog-api-key": self.api_key,
            },
            method="POST",
        )

        try:
            with request.urlopen(req, timeout=60) as response:
                raw = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Gemini API request failed: {detail}") from exc
        except error.URLError as exc:
            raise RuntimeError("Gemini API request could not reach the server.") from exc

        candidate = ((raw.get("candidates") or [{}])[0]) if isinstance(raw, dict) else {}
        parts = (((candidate.get("content") or {}).get("parts")) or [{}]) if isinstance(candidate, dict) else [{}]
        text = parts[0].get("text") if parts else None
        if not text:
            finish_reason = candidate.get("finishReason")
            raise RuntimeError(f"Gemini returned no structured response text. finishReason={finish_reason}")

        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as exc:
            raise RuntimeError("Gemini returned malformed JSON for photo verification.") from exc

        return parsed, {
            "provider": "gemini",
            "model": self.model,
            "finishReason": candidate.get("finishReason"),
        }
