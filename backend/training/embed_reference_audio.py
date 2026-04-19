from __future__ import annotations

import argparse
import csv
import json
import wave
from pathlib import Path

import numpy as np


def read_wav(path: Path) -> tuple[int, np.ndarray]:
    with wave.open(str(path), "rb") as wav_file:
        sample_rate = wav_file.getframerate()
        frame_count = wav_file.getnframes()
        audio = np.frombuffer(wav_file.readframes(frame_count), dtype=np.int16).astype(np.float32) / 32768.0
    return sample_rate, audio


def resample_audio(audio: np.ndarray, source_rate: int, target_rate: int) -> np.ndarray:
    if source_rate == target_rate or audio.size == 0:
        return audio
    duration = audio.size / float(source_rate)
    target_size = max(1, int(round(duration * target_rate)))
    source_index = np.linspace(0.0, 1.0, num=audio.size, endpoint=False)
    target_index = np.linspace(0.0, 1.0, num=target_size, endpoint=False)
    return np.interp(target_index, source_index, audio).astype(np.float32)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, help="Normalized manifest CSV from prepare_reference_audio.py")
    parser.add_argument("--output-npy", required=True)
    parser.add_argument("--model-name", default="perch_v2", help="Preset model name from perch_hoplite.zoo.model_configs")
    parser.add_argument("--batch-size", type=int, default=16)
    args = parser.parse_args()

    from ml_collections import config_dict
    from perch_hoplite.zoo import model_configs

    rows = list(csv.DictReader(open(args.manifest, newline="")))
    preset = model_configs.get_preset_model_config(args.model_name)
    embedding_model = preset.load_model()
    target_rate = int(getattr(embedding_model, "sample_rate", preset.model_config.sample_rate))
    window_size_s = float(getattr(embedding_model, "window_size_s", preset.model_config.window_size_s))
    target_samples = int(round(window_size_s * target_rate))

    embeddings: list[np.ndarray] = []
    batch_audio: list[np.ndarray] = []
    for row in rows:
        sample_rate, audio = read_wav(Path(row["window_path"]))
        audio = resample_audio(audio, sample_rate, target_rate)
        if audio.size < target_samples:
            audio = np.pad(audio, (0, target_samples - audio.size))
        else:
            audio = audio[:target_samples]
        batch_audio.append(audio.astype(np.float32))

        if len(batch_audio) >= args.batch_size:
            outputs = embedding_model.batch_embed(np.stack(batch_audio, axis=0))
            batch_embeddings = np.asarray(outputs.embeddings).reshape(len(batch_audio), -1, outputs.embeddings.shape[-1])[:, 0, :]
            embeddings.extend(batch_embeddings)
            batch_audio = []

    if batch_audio:
        outputs = embedding_model.batch_embed(np.stack(batch_audio, axis=0))
        batch_embeddings = np.asarray(outputs.embeddings).reshape(len(batch_audio), -1, outputs.embeddings.shape[-1])[:, 0, :]
        embeddings.extend(batch_embeddings)

    output_path = Path(args.output_npy)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    np.save(output_path, np.stack(embeddings, axis=0))

    metadata = {
        "encoder": "Perch-Hoplite",
        "model_preset_name": preset.preset_name,
        "model_key": preset.model_key,
        "model_config": config_dict.ConfigDict(preset.model_config).to_dict(),
        "embedding_dim": int(preset.embedding_dim),
        "sample_rate_hz": target_rate,
        "window_size_s": window_size_s,
        "manifest_rows": len(rows),
    }
    output_path.with_name("embedding_metadata.json").write_text(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()
