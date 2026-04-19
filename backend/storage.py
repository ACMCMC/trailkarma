from __future__ import annotations

import json
from pathlib import Path
from typing import Any


class EventStore:
    def __init__(self, base_dir: Path) -> None:
        self.base_dir = base_dir
        self.audio_dir = self.base_dir / "audio"
        self.photo_dir = self.base_dir / "photos"
        self.event_dir = self.base_dir / "events"
        for path in (self.audio_dir, self.photo_dir, self.event_dir):
            path.mkdir(parents=True, exist_ok=True)

    def save_audio(self, observation_id: str, audio_bytes: bytes, suffix: str = ".wav") -> Path:
        path = self.audio_dir / f"{observation_id}{suffix}"
        path.write_bytes(audio_bytes)
        return path

    def save_photo(self, observation_id: str, photo_bytes: bytes, suffix: str = ".jpg") -> Path:
        path = self.photo_dir / f"{observation_id}{suffix}"
        path.write_bytes(photo_bytes)
        return path

    def event_path(self, observation_id: str) -> Path:
        return self.event_dir / f"{observation_id}.json"

    def save_event(self, observation_id: str, payload: dict[str, Any]) -> None:
        self.event_path(observation_id).write_text(json.dumps(payload, indent=2))

    def load_event(self, observation_id: str) -> dict[str, Any] | None:
        path = self.event_path(observation_id)
        if not path.exists():
            return None
        return json.loads(path.read_text())

    def merge_event(self, observation_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        current = self.load_event(observation_id) or {}
        current.update(payload)
        self.save_event(observation_id, current)
        return current

    def iter_events(self) -> list[dict[str, Any]]:
        events: list[dict[str, Any]] = []
        for path in sorted(self.event_dir.glob("*.json")):
            try:
                events.append(json.loads(path.read_text()))
            except json.JSONDecodeError:
                continue
        return events

    def has_verified_species_label(self, label: str, *, exclude_observation_id: str | None = None) -> bool:
        normalized = self.normalize_species_label(label)
        if not normalized:
            return False

        for event in self.iter_events():
            if exclude_observation_id and event.get("observation_id") == exclude_observation_id:
                continue
            if event.get("verification_status") != "verified":
                continue
            if event.get("collectible_status") != "verified":
                continue

            candidate = event.get("finalLabel") or event.get("collectible_name")
            if self.normalize_species_label(str(candidate or "")) == normalized:
                return True
        return False

    @staticmethod
    def normalize_species_label(label: str) -> str:
        return " ".join(
            "".join(ch.lower() if ch.isalnum() else " " for ch in label).split()
        )
