from __future__ import annotations

import json
import wave
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from .schemas import AcousticCandidate, StructuredCandidatePayload


@dataclass
class AcousticInferenceResult:
    payload: StructuredCandidatePayload
    confidence: float
    model_metadata: dict[str, Any]


@dataclass
class _ArtifactBundle:
    classifier_coef: np.ndarray
    classifier_intercept: np.ndarray
    classifier_classes: list[str]
    label_metadata: dict[str, dict[str, Any]]
    prototype_bank: list[dict[str, Any]]
    prototype_embeddings: np.ndarray


class AcousticPipeline:
    def __init__(self, artifact_dir: Path) -> None:
        self.artifact_dir = artifact_dir
        self.artifact_metadata = self._load_artifact_metadata()
        self.artifacts = self._load_artifacts()
        self._embedding_model = None
        self._embedding_model_error: str | None = None

    def _load_artifact_metadata(self) -> dict[str, Any]:
        metadata_path = self.artifact_dir / "metadata.json"
        if metadata_path.exists():
            return json.loads(metadata_path.read_text())
        return {
            "encoder": "heuristic-demo-fallback",
            "prototype_bank": "not_loaded",
            "classifier": "not_loaded",
            "region_prior": "PCT + Southern California",
        }

    def _load_artifacts(self) -> _ArtifactBundle | None:
        required_files = [
            self.artifact_dir / "classifier_coef.npy",
            self.artifact_dir / "classifier_intercept.npy",
            self.artifact_dir / "classifier_classes.json",
            self.artifact_dir / "label_metadata.json",
            self.artifact_dir / "prototype_bank.json",
            self.artifact_dir / "prototype_embeddings.npy",
        ]
        if not all(path.exists() for path in required_files):
            return None

        return _ArtifactBundle(
            classifier_coef=np.load(required_files[0]),
            classifier_intercept=np.load(required_files[1]),
            classifier_classes=json.loads(required_files[2].read_text()),
            label_metadata=json.loads(required_files[3].read_text()),
            prototype_bank=json.loads(required_files[4].read_text()),
            prototype_embeddings=np.load(required_files[5]),
        )

    def run(self, audio_path: Path, lat: float, lon: float, timestamp: str) -> AcousticInferenceResult:
        sample_rate, signal = self._read_wav(audio_path)
        features = self._extract_features(signal, sample_rate)

        if self.artifacts is not None:
            try:
                return self._run_artifact_pipeline(audio_path, signal, sample_rate, lat, lon, timestamp, features)
            except Exception as exc:
                candidates, raw_confidence, background_flags = self._heuristic_candidates(features)
                return AcousticInferenceResult(
                    payload=StructuredCandidatePayload(
                        observation_id=audio_path.stem,
                        top_candidates=candidates,
                        raw_confidence=raw_confidence,
                        background_flags=background_flags,
                        lat=lat,
                        lon=lon,
                        timestamp=timestamp,
                        time_of_day=self._time_of_day(timestamp),
                        region_hint="coastal Southern California",
                    ),
                    confidence=raw_confidence,
                    model_metadata={
                        **self.artifact_metadata,
                        "artifact_mode": "fallback_after_error",
                        "artifact_error": str(exc),
                        "sample_rate_hz": sample_rate,
                        "feature_summary": {k: round(v, 4) for k, v in features.items()},
                    },
                )

        candidates, raw_confidence, background_flags = self._heuristic_candidates(features)
        return AcousticInferenceResult(
            payload=StructuredCandidatePayload(
                observation_id=audio_path.stem,
                top_candidates=candidates,
                raw_confidence=raw_confidence,
                background_flags=background_flags,
                lat=lat,
                lon=lon,
                timestamp=timestamp,
                time_of_day=self._time_of_day(timestamp),
                region_hint="coastal Southern California",
            ),
            confidence=raw_confidence,
            model_metadata={
                **self.artifact_metadata,
                "artifact_mode": "heuristic_only",
                "sample_rate_hz": sample_rate,
                "feature_summary": {k: round(v, 4) for k, v in features.items()},
            },
        )

    def _run_artifact_pipeline(
        self,
        audio_path: Path,
        signal: np.ndarray,
        sample_rate: int,
        lat: float,
        lon: float,
        timestamp: str,
        features: dict[str, float],
    ) -> AcousticInferenceResult:
        if self.artifacts is None:
            raise ValueError("Artifacts are not loaded")

        embedding = self._embed_signal(signal, sample_rate)
        classifier_probs = self._predict_classifier_probs(embedding)
        retrieval = self._retrieve_nearest_prototypes(embedding)
        candidates, raw_confidence, background_flags = self._combine_signals(classifier_probs, retrieval, features)

        return AcousticInferenceResult(
            payload=StructuredCandidatePayload(
                observation_id=audio_path.stem,
                top_candidates=candidates,
                raw_confidence=raw_confidence,
                background_flags=background_flags,
                lat=lat,
                lon=lon,
                timestamp=timestamp,
                time_of_day=self._time_of_day(timestamp),
                region_hint="coastal Southern California",
            ),
            confidence=raw_confidence,
            model_metadata={
                **self.artifact_metadata,
                "artifact_mode": "trained_open_world",
                "sample_rate_hz": sample_rate,
                "feature_summary": {k: round(v, 4) for k, v in features.items()},
                "classifier_top": classifier_probs[:3],
                "retrieval_top": retrieval["top_matches"][:5],
            },
        )

    def _load_embedding_model(self):
        if self._embedding_model is not None:
            return self._embedding_model
        if self._embedding_model_error is not None:
            raise RuntimeError(self._embedding_model_error)

        try:
            import tensorflow as tf
            from ml_collections import config_dict
            from perch_hoplite.zoo import model_configs
        except Exception as exc:  # pragma: no cover - runtime dependency on Brev backend
            self._embedding_model_error = f"perch_hoplite is unavailable: {exc}"
            raise RuntimeError(self._embedding_model_error) from exc

        try:  # pragma: no cover - depends on Brev GPU runtime
            for device in tf.config.list_physical_devices("GPU"):
                tf.config.experimental.set_memory_growth(device, True)
        except Exception:
            pass

        model_key = self.artifact_metadata.get("model_key")
        model_config_dict = self.artifact_metadata.get("model_config")
        preset_name = self.artifact_metadata.get("model_preset_name")

        if model_key and model_config_dict:
            model_class = model_configs.get_model_class(model_key)
            model_config = config_dict.ConfigDict(model_config_dict)
            self._embedding_model = model_class.from_config(model_config)
            return self._embedding_model
        if preset_name:
            preset = model_configs.get_preset_model_config(preset_name)
            self._embedding_model = preset.load_model()
            return self._embedding_model

        self._embedding_model_error = "Artifact metadata does not include model_key/model_config or model_preset_name"
        raise RuntimeError(self._embedding_model_error)

    def _embed_signal(self, signal: np.ndarray, sample_rate: int) -> np.ndarray:
        model = self._load_embedding_model()
        target_rate = int(getattr(model, "sample_rate", self.artifact_metadata.get("sample_rate_hz", sample_rate)))
        window_size_s = float(getattr(model, "window_size_s", self.artifact_metadata.get("window_size_s", 5.0)))
        target_samples = int(round(window_size_s * target_rate))

        audio = signal.astype(np.float32)
        if sample_rate != target_rate:
            audio = self._resample(audio, sample_rate, target_rate)
        if audio.size < target_samples:
            audio = np.pad(audio, (0, target_samples - audio.size))
        else:
            audio = audio[:target_samples]

        outputs = model.embed(audio)
        embeddings = np.asarray(outputs.embeddings)
        if embeddings.ndim == 4:
            return embeddings[0, 0, 0].astype(np.float32)
        if embeddings.ndim == 3:
            return embeddings[0, 0].astype(np.float32)
        if embeddings.ndim == 2:
            return embeddings[0].astype(np.float32)
        raise ValueError(f"Unexpected embedding shape: {embeddings.shape}")

    def _predict_classifier_probs(self, embedding: np.ndarray) -> list[dict[str, float | str]]:
        if self.artifacts is None:
            raise ValueError("Artifacts are not loaded")
        logits = embedding @ self.artifacts.classifier_coef.T + self.artifacts.classifier_intercept
        logits = logits - np.max(logits)
        exp_logits = np.exp(logits)
        probabilities = exp_logits / np.sum(exp_logits)
        ranked = np.argsort(probabilities)[::-1]
        return [
            {
                "label_key": self.artifacts.classifier_classes[index],
                "probability": float(probabilities[index]),
            }
            for index in ranked
        ]

    def _retrieve_nearest_prototypes(self, embedding: np.ndarray) -> dict[str, Any]:
        if self.artifacts is None:
            raise ValueError("Artifacts are not loaded")

        embedding_norm = self._l2_normalize(embedding)
        prototype_matrix = self._l2_normalize(self.artifacts.prototype_embeddings)
        similarities = prototype_matrix @ embedding_norm
        ranked = np.argsort(similarities)[::-1]

        grouped: dict[str, list[float]] = {}
        top_matches: list[dict[str, Any]] = []
        for index in ranked[:16]:
            prototype = self.artifacts.prototype_bank[int(index)]
            label_key = prototype["label_key"]
            similarity = float(similarities[int(index)])
            grouped.setdefault(label_key, []).append(similarity)
            top_matches.append(
                {
                    "label_key": label_key,
                    "label": prototype["label"],
                    "similarity": round(similarity, 4),
                    "source": prototype.get("source"),
                    "source_id": prototype.get("source_id"),
                }
            )

        label_scores = []
        for label_key, sims in grouped.items():
            label_scores.append(
                {
                    "label_key": label_key,
                    "score": float(max(sims) * 0.7 + (sum(sims[:2]) / max(len(sims[:2]), 1)) * 0.3),
                }
            )
        label_scores.sort(key=lambda item: item["score"], reverse=True)
        return {"label_scores": label_scores, "top_matches": top_matches}

    def _combine_signals(
        self,
        classifier_probs: list[dict[str, float | str]],
        retrieval: dict[str, Any],
        features: dict[str, float],
    ) -> tuple[list[AcousticCandidate], float, list[str]]:
        if self.artifacts is None:
            raise ValueError("Artifacts are not loaded")

        combined_scores: dict[str, float] = {}
        classifier_lookup = {item["label_key"]: float(item["probability"]) for item in classifier_probs}
        retrieval_lookup = {
            item["label_key"]: max(0.0, (float(item["score"]) + 1.0) / 2.0)
            for item in retrieval["label_scores"]
        }

        for label_key, probability in classifier_lookup.items():
            combined_scores[label_key] = combined_scores.get(label_key, 0.0) + 0.7 * probability
        for label_key, retrieval_score in retrieval_lookup.items():
            combined_scores[label_key] = combined_scores.get(label_key, 0.0) + 0.3 * retrieval_score

        top_classifier_label = classifier_probs[0]["label_key"] if classifier_probs else None
        top_retrieval_label = retrieval["label_scores"][0]["label_key"] if retrieval["label_scores"] else None
        if top_classifier_label and top_classifier_label == top_retrieval_label:
            label_key = str(top_classifier_label)
            combined_scores[label_key] = combined_scores.get(label_key, 0.0) + 0.08

        ranked_labels = sorted(combined_scores.items(), key=lambda item: item[1], reverse=True)
        ranked_labels = ranked_labels[:5]
        background_flags = self._background_flags(features)

        if not ranked_labels:
            return self._heuristic_candidates(features)

        top_score = ranked_labels[0][1]
        second_score = ranked_labels[1][1] if len(ranked_labels) > 1 else 0.0
        margin = max(0.0, top_score - second_score)
        raw_confidence = float(max(0.05, min(0.99, top_score + 0.25 * margin)))

        candidates: list[AcousticCandidate] = []
        for label_key, score in ranked_labels[:3]:
            candidates.append(self._candidate_from_label(label_key, score))

        top_meta = self.artifacts.label_metadata.get(ranked_labels[0][0], {})
        genus = top_meta.get("genus")
        family = top_meta.get("family")
        if genus and not any(candidate.taxonomic_level == "genus" and candidate.label == genus for candidate in candidates):
            candidates.append(
                AcousticCandidate(
                    label=str(genus),
                    taxonomic_level="genus",
                    score=round(max(0.05, raw_confidence * 0.45), 4),
                    genus=str(genus),
                    family=str(family) if family else None,
                )
            )
        if family and not any(candidate.taxonomic_level == "family" and candidate.label == family for candidate in candidates):
            candidates.append(
                AcousticCandidate(
                    label=str(family),
                    taxonomic_level="family",
                    score=round(max(0.04, raw_confidence * 0.28), 4),
                    family=str(family),
                )
            )

        if raw_confidence < 0.38 and not any(candidate.label == "unknown animal sound" for candidate in candidates):
            candidates.insert(
                0,
                AcousticCandidate(label="unknown animal sound", taxonomic_level="unknown", score=round(max(0.2, 1.0 - raw_confidence), 4)),
            )

        normalized = self._normalize_candidate_scores(candidates[:3])
        return normalized, raw_confidence, background_flags

    def _candidate_from_label(self, label_key: str, score: float) -> AcousticCandidate:
        if self.artifacts is None:
            raise ValueError("Artifacts are not loaded")
        meta = self.artifacts.label_metadata.get(label_key, {})
        return AcousticCandidate(
            label=str(meta.get("display_label") or "unknown animal sound"),
            scientific_name=meta.get("scientific_name"),
            taxonomic_level=str(meta.get("taxonomic_level") or "unknown"),
            score=round(float(score), 4),
            genus=meta.get("genus"),
            family=meta.get("family"),
        )

    def _normalize_candidate_scores(self, candidates: list[AcousticCandidate]) -> list[AcousticCandidate]:
        total = sum(max(candidate.score, 0.0) for candidate in candidates) or 1.0
        return [candidate.model_copy(update={"score": round(candidate.score / total, 4)}) for candidate in candidates]

    def _read_wav(self, audio_path: Path) -> tuple[int, np.ndarray]:
        with wave.open(str(audio_path), "rb") as wav_file:
            sample_rate = wav_file.getframerate()
            frame_count = wav_file.getnframes()
            raw = wav_file.readframes(frame_count)
            signal = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
        return sample_rate, signal

    def _extract_features(self, signal: np.ndarray, sample_rate: int) -> dict[str, float]:
        if signal.size == 0:
            return {
                "rms": 0.0,
                "zcr": 0.0,
                "dominant_freq_hz": 0.0,
                "centroid_hz": 0.0,
                "low_ratio": 1.0,
                "mid_ratio": 0.0,
                "high_ratio": 0.0,
            }

        rms = float(np.sqrt(np.mean(np.square(signal))))
        zcr = float(np.mean(np.abs(np.diff(np.signbit(signal)))))

        window = np.hanning(signal.size)
        spectrum = np.abs(np.fft.rfft(signal * window))
        freqs = np.fft.rfftfreq(signal.size, d=1.0 / sample_rate)
        total_energy = float(np.sum(spectrum) + 1e-8)

        dominant_freq = float(freqs[int(np.argmax(spectrum))]) if spectrum.size else 0.0
        centroid = float(np.sum(freqs * spectrum) / total_energy)
        low_ratio = float(np.sum(spectrum[freqs < 400]) / total_energy)
        mid_ratio = float(np.sum(spectrum[(freqs >= 400) & (freqs < 2000)]) / total_energy)
        high_ratio = float(np.sum(spectrum[freqs >= 2000]) / total_energy)

        return {
            "rms": rms,
            "zcr": zcr,
            "dominant_freq_hz": dominant_freq,
            "centroid_hz": centroid,
            "low_ratio": low_ratio,
            "mid_ratio": mid_ratio,
            "high_ratio": high_ratio,
        }

    def _heuristic_candidates(
        self, features: dict[str, float]
    ) -> tuple[list[AcousticCandidate], float, list[str]]:
        rms = features["rms"]
        zcr = features["zcr"]
        dominant = features["dominant_freq_hz"]
        centroid = features["centroid_hz"]
        low_ratio = features["low_ratio"]
        high_ratio = features["high_ratio"]
        background_flags = self._background_flags(features)

        if rms < 0.015:
            return (
                self._normalize_candidate_scores(
                    [
                        AcousticCandidate(label="unknown animal sound", taxonomic_level="unknown", score=0.18),
                        AcousticCandidate(label="Wind / stream noise", taxonomic_level="unknown", score=0.52),
                        AcousticCandidate(label="Human speech", taxonomic_level="unknown", score=0.1),
                    ]
                ),
                0.18,
                background_flags,
            )

        if low_ratio > 0.58:
            return (
                self._normalize_candidate_scores(
                    [
                        AcousticCandidate(label="Wind / stream noise", taxonomic_level="unknown", score=0.72),
                        AcousticCandidate(label="unknown animal sound", taxonomic_level="unknown", score=0.16),
                        AcousticCandidate(label="Human speech", taxonomic_level="unknown", score=0.08),
                    ]
                ),
                0.16,
                background_flags,
            )

        if 250 <= dominant <= 3400 and 0.04 <= zcr <= 0.22 and 0.18 <= high_ratio <= 0.6:
            return (
                self._normalize_candidate_scores(
                    [
                        AcousticCandidate(label="Human speech", taxonomic_level="unknown", score=0.81),
                        AcousticCandidate(label="unknown animal sound", taxonomic_level="unknown", score=0.12),
                        AcousticCandidate(label="Wind / stream noise", taxonomic_level="unknown", score=0.05),
                    ]
                ),
                0.12,
                background_flags,
            )

        if dominant >= 3000 and high_ratio > 0.55:
            return (
                self._normalize_candidate_scores(
                    [
                        AcousticCandidate(
                            label="Pacific tree cricket",
                            scientific_name="Oecanthus californicus",
                            taxonomic_level="species",
                            score=0.62,
                            genus="Oecanthus",
                            family="Gryllidae",
                        ),
                        AcousticCandidate(label="Gryllidae", taxonomic_level="family", score=0.21, family="Gryllidae"),
                        AcousticCandidate(label="Insect chorus", taxonomic_level="unknown", score=0.09),
                    ]
                ),
                0.62,
                background_flags,
            )

        if 1200 <= centroid <= 3200 and high_ratio > 0.35:
            return (
                self._normalize_candidate_scores(
                    [
                        AcousticCandidate(
                            label="California scrub-jay",
                            scientific_name="Aphelocoma californica",
                            taxonomic_level="species",
                            score=0.67,
                            genus="Aphelocoma",
                            family="Corvidae",
                        ),
                        AcousticCandidate(
                            label="Steller's jay",
                            scientific_name="Cyanocitta stelleri",
                            taxonomic_level="species",
                            score=0.19,
                            genus="Cyanocitta",
                            family="Corvidae",
                        ),
                        AcousticCandidate(label="Corvidae", taxonomic_level="family", score=0.09, family="Corvidae"),
                    ]
                ),
                0.67,
                background_flags,
            )

        if 300 <= dominant <= 1100 and 0.18 <= zcr <= 0.42:
            return (
                self._normalize_candidate_scores(
                    [
                        AcousticCandidate(
                            label="Pacific treefrog",
                            scientific_name="Pseudacris regilla",
                            taxonomic_level="species",
                            score=0.58,
                            genus="Pseudacris",
                            family="Hylidae",
                        ),
                        AcousticCandidate(label="Pseudacris", taxonomic_level="genus", score=0.23, genus="Pseudacris", family="Hylidae"),
                        AcousticCandidate(label="Hylidae", taxonomic_level="family", score=0.1, family="Hylidae"),
                    ]
                ),
                0.58,
                background_flags,
            )

        return (
            self._normalize_candidate_scores(
                [
                    AcousticCandidate(label="unknown animal sound", taxonomic_level="unknown", score=0.41),
                    AcousticCandidate(label="Mammalia", taxonomic_level="family", score=0.22, family="Mammalia"),
                    AcousticCandidate(label="Corvidae", taxonomic_level="family", score=0.14, family="Corvidae"),
                ]
            ),
            0.41,
            background_flags,
        )

    def _background_flags(self, features: dict[str, float]) -> list[str]:
        flags: list[str] = []
        if features["rms"] < 0.015:
            flags.append("silence_high")
        if features["low_ratio"] > 0.58:
            flags.append("wind_low")
        if 250 <= features["dominant_freq_hz"] <= 3400 and 0.04 <= features["zcr"] <= 0.22 and 0.18 <= features["high_ratio"] <= 0.6:
            flags.append("speech_like")
        return flags

    def _resample(self, audio: np.ndarray, source_rate: int, target_rate: int) -> np.ndarray:
        if source_rate == target_rate or audio.size == 0:
            return audio.astype(np.float32)
        duration = audio.size / float(source_rate)
        target_size = max(1, int(round(duration * target_rate)))
        source_index = np.linspace(0.0, 1.0, num=audio.size, endpoint=False)
        target_index = np.linspace(0.0, 1.0, num=target_size, endpoint=False)
        return np.interp(target_index, source_index, audio).astype(np.float32)

    def _l2_normalize(self, values: np.ndarray) -> np.ndarray:
        if values.ndim == 1:
            norm = np.linalg.norm(values) + 1e-8
            return values / norm
        norm = np.linalg.norm(values, axis=1, keepdims=True) + 1e-8
        return values / norm

    def _time_of_day(self, timestamp: str) -> str:
        hour_str = timestamp[11:13] if "T" in timestamp and len(timestamp) >= 13 else "12"
        hour = int(hour_str)
        if 5 <= hour < 11:
            return "morning"
        if 11 <= hour < 17:
            return "afternoon"
        if 17 <= hour < 21:
            return "evening"
        return "night"
