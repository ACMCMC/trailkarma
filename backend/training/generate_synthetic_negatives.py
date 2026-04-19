from __future__ import annotations

import argparse
import math
import random
import wave
from pathlib import Path

import numpy as np


SAMPLE_RATE = 16000
DURATION_S = 5
SAMPLES = SAMPLE_RATE * DURATION_S


def normalize(signal: np.ndarray) -> np.ndarray:
    peak = float(np.max(np.abs(signal)) + 1e-8)
    signal = signal / peak
    return np.clip(signal * 0.7, -1.0, 1.0)


def lowpass_noise(cutoff_hz: float, scale: float = 1.0) -> np.ndarray:
    freq_bins = np.fft.rfftfreq(SAMPLES, d=1.0 / SAMPLE_RATE)
    spectrum = np.fft.rfft(np.random.normal(size=SAMPLES))
    spectrum[freq_bins > cutoff_hz] = 0
    signal = np.fft.irfft(spectrum, n=SAMPLES).astype(np.float32)
    return normalize(signal) * scale


def band_noise(low_hz: float, high_hz: float, scale: float = 1.0) -> np.ndarray:
    freq_bins = np.fft.rfftfreq(SAMPLES, d=1.0 / SAMPLE_RATE)
    spectrum = np.fft.rfft(np.random.normal(size=SAMPLES))
    mask = (freq_bins >= low_hz) & (freq_bins <= high_hz)
    spectrum[~mask] = 0
    signal = np.fft.irfft(spectrum, n=SAMPLES).astype(np.float32)
    return normalize(signal) * scale


def make_wind() -> np.ndarray:
    return normalize(lowpass_noise(400, scale=1.0) * np.linspace(0.5, 1.0, SAMPLES))


def make_stream() -> np.ndarray:
    return normalize(band_noise(150, 3000, scale=1.0) * np.random.uniform(0.7, 1.0, SAMPLES))


def make_traffic() -> np.ndarray:
    t = np.arange(SAMPLES) / SAMPLE_RATE
    engine = 0.6 * np.sin(2 * math.pi * random.uniform(70, 140) * t)
    rumble = lowpass_noise(250, scale=0.8)
    return normalize(engine + rumble)


def make_footsteps() -> np.ndarray:
    signal = np.zeros(SAMPLES, dtype=np.float32)
    for _ in range(random.randint(6, 12)):
        center = random.randint(0, SAMPLES - 1)
        width = random.randint(400, 1400)
        start = max(0, center - width // 2)
        end = min(SAMPLES, center + width // 2)
        burst = np.hanning(end - start) * random.uniform(0.4, 1.0)
        signal[start:end] += burst.astype(np.float32) * lowpass_noise(1800, scale=1.0)[start:end]
    return normalize(signal)


def make_gear_rustle() -> np.ndarray:
    signal = np.zeros(SAMPLES, dtype=np.float32)
    for _ in range(random.randint(15, 30)):
        start = random.randint(0, SAMPLES - 400)
        end = min(SAMPLES, start + random.randint(150, 700))
        burst = band_noise(500, 6000, scale=1.0)[start:end]
        envelope = np.hanning(end - start).astype(np.float32)
        signal[start:end] += burst * envelope
    return normalize(signal)


def make_silence() -> np.ndarray:
    return np.zeros(SAMPLES, dtype=np.float32)


def make_speech_like() -> np.ndarray:
    t = np.arange(SAMPLES) / SAMPLE_RATE
    fundamental = random.uniform(90, 180)
    harmonic = (
        0.8 * np.sin(2 * math.pi * fundamental * t)
        + 0.3 * np.sin(2 * math.pi * fundamental * 2.1 * t)
        + 0.15 * np.sin(2 * math.pi * fundamental * 3.9 * t)
    )
    envelope = np.zeros(SAMPLES, dtype=np.float32)
    cursor = 0
    while cursor < SAMPLES:
        syllable = random.randint(600, 2400)
        gap = random.randint(300, 1200)
        end = min(SAMPLES, cursor + syllable)
        envelope[cursor:end] = np.hanning(end - cursor)
        cursor = end + gap
    fricative = band_noise(1200, 5000, scale=0.35)
    return normalize(harmonic * envelope + fricative * envelope)


def make_dog_bark_like() -> np.ndarray:
    signal = np.zeros(SAMPLES, dtype=np.float32)
    for _ in range(random.randint(3, 7)):
        start = random.randint(0, SAMPLES - 2500)
        length = random.randint(900, 2200)
        t = np.arange(length) / SAMPLE_RATE
        burst = (
            np.sin(2 * math.pi * random.uniform(280, 520) * t)
            + 0.4 * band_noise(200, 2000, scale=1.0)[:length]
        )
        envelope = np.exp(-4.0 * np.linspace(0.0, 1.0, num=length)).astype(np.float32)
        signal[start : start + length] += burst[:length] * envelope
    return normalize(signal)


GENERATORS = {
    "speech": make_speech_like,
    "wind": make_wind,
    "stream": make_stream,
    "traffic": make_traffic,
    "footsteps": make_footsteps,
    "gear_rustle": make_gear_rustle,
    "silence": make_silence,
    "dog_bark": make_dog_bark_like,
}


def write_wav(path: Path, signal: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = np.int16(np.clip(signal, -1.0, 1.0) * 32767.0)
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(SAMPLE_RATE)
        wav_file.writeframes(pcm.tobytes())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--per-category", type=int, default=18)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)

    output_dir = Path(args.output_dir)
    for category, generator in GENERATORS.items():
        for index in range(args.per_category):
            write_wav(output_dir / category / f"{category}_{index:03d}.wav", generator())


if __name__ == "__main__":
    main()
