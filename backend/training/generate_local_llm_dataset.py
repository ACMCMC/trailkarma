from __future__ import annotations

import argparse
import json
import random
from collections import defaultdict
from pathlib import Path


SYSTEM_PROMPT = (
    "You are given candidate wildlife detections from an audio model. "
    "Return valid JSON with keys finalLabel, finalTaxonomicLevel, confidenceBand, explanation, safeForRewarding. "
    "Do not invent a species not present in the candidate list."
)

NEGATIVE_LABELS = {
    "Dog bark",
    "Footsteps / gear rustle",
    "Human speech",
    "Silence",
    "Traffic noise",
    "Wind / stream noise",
}

TIME_OF_DAY = ["pre-dawn", "morning", "midday", "evening", "night"]
REGION_HINTS = [
    "coastal Southern California",
    "oak woodland trail",
    "chaparral ridge",
    "riparian canyon",
    "pine forest trail",
]
BACKGROUND_FLAGS = [
    [],
    ["wind_low"],
    ["stream_low"],
    ["handling_low"],
    ["wind_low", "distant_traffic_low"],
]


def load_label_metadata(artifact_dir: Path) -> dict[str, dict]:
    return json.loads((artifact_dir / "label_metadata.json").read_text())


def build_indexes(label_metadata: dict[str, dict]) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    family_to_species: dict[str, list[str]] = defaultdict(list)
    genus_to_species: dict[str, list[str]] = defaultdict(list)
    for label_key, meta in label_metadata.items():
        if meta.get("taxonomic_level") != "species":
            continue
        family = meta.get("family")
        genus = meta.get("genus")
        if family:
            family_to_species[str(family)].append(label_key)
        if genus:
            genus_to_species[str(genus)].append(label_key)
    return family_to_species, genus_to_species


def candidate_from_meta(
    meta: dict,
    score: float,
    *,
    label_override: str | None = None,
    taxonomic_level_override: str | None = None,
) -> dict:
    return {
        "label": label_override or meta.get("display_label") or meta.get("family") or "unknown animal sound",
        "scientific_name": meta.get("scientific_name"),
        "taxonomic_level": taxonomic_level_override or meta.get("taxonomic_level") or "unknown",
        "score": round(score, 4),
        "genus": meta.get("genus"),
        "family": meta.get("family"),
    }


def normalize_scores(candidates: list[dict]) -> list[dict]:
    total = sum(max(float(candidate["score"]), 0.0) for candidate in candidates) or 1.0
    normalized = []
    for candidate in candidates:
        candidate = dict(candidate)
        candidate["score"] = round(float(candidate["score"]) / total, 4)
        normalized.append(candidate)
    return normalized


def sample_species_key(keys: list[str], rng: random.Random) -> str:
    return keys[rng.randrange(len(keys))]


def safe_alt_species(
    label_key: str,
    label_metadata: dict[str, dict],
    family_to_species: dict[str, list[str]],
    genus_to_species: dict[str, list[str]],
    rng: random.Random,
) -> str | None:
    meta = label_metadata[label_key]
    genus = meta.get("genus")
    family = meta.get("family")

    genus_pool = [key for key in genus_to_species.get(str(genus), []) if key != label_key] if genus else []
    family_pool = [key for key in family_to_species.get(str(family), []) if key != label_key] if family else []
    pool = genus_pool or family_pool
    if not pool:
        return None
    return sample_species_key(pool, rng)


def build_payload(
    observation_id: str,
    top_candidates: list[dict],
    raw_confidence: float,
    rng: random.Random,
) -> dict:
    return {
        "observation_id": observation_id,
        "top_candidates": normalize_scores(top_candidates),
        "raw_confidence": round(raw_confidence, 4),
        "background_flags": rng.choice(BACKGROUND_FLAGS),
        "lat": round(rng.uniform(32.55, 33.25), 5),
        "lon": round(rng.uniform(-117.45, -116.85), 5),
        "timestamp": "2026-04-18T20:10:00Z",
        "time_of_day": rng.choice(TIME_OF_DAY),
        "region_hint": rng.choice(REGION_HINTS),
    }


def build_response(payload: dict) -> dict:
    top = payload["top_candidates"][0]
    top_label = top["label"]
    top_level = top["taxonomic_level"]
    raw_confidence = float(payload["raw_confidence"])

    if top_label in NEGATIVE_LABELS:
        explanations = {
            "Dog bark": "The strongest signal is a domestic dog bark, so this should not be rewarded as wildlife.",
            "Footsteps / gear rustle": "Handling noise and footsteps dominate the clip, so the wildlife evidence is not reliable.",
            "Human speech": "The strongest signal is human speech rather than wildlife, so this should not be rewarded.",
            "Silence": "The clip is too quiet to support a reliable wildlife identification.",
            "Traffic noise": "Traffic noise dominates the clip rather than trail wildlife.",
            "Wind / stream noise": "Environmental background noise is strongest here, so this is not safe to reward as wildlife.",
        }
        return {
            "finalLabel": top_label,
            "finalTaxonomicLevel": "unknown",
            "confidenceBand": "high" if top_label in {"Human speech", "Silence"} else "medium",
            "explanation": explanations[top_label],
            "safeForRewarding": False,
        }

    if top_level == "species" and raw_confidence >= 0.8:
        return {
            "finalLabel": top_label,
            "finalTaxonomicLevel": "species",
            "confidenceBand": "high",
            "explanation": f"Likely {top_label}. The top acoustic candidate is clearly strongest among the open-world matches.",
            "safeForRewarding": True,
        }

    if top_level == "species" and raw_confidence >= 0.63:
        return {
            "finalLabel": top_label,
            "finalTaxonomicLevel": "species",
            "confidenceBand": "medium-high",
            "explanation": f"Likely {top_label}. Similar taxa are possible, but the top candidate still leads the list.",
            "safeForRewarding": True,
        }

    if top.get("genus"):
        return {
            "finalLabel": top["genus"],
            "finalTaxonomicLevel": "genus",
            "confidenceBand": "medium",
            "explanation": f"The clip appears wildlife-like, but genus-level wording is safer than a species claim here.",
            "safeForRewarding": True,
        }

    if top.get("family"):
        return {
            "finalLabel": top["family"],
            "finalTaxonomicLevel": "family",
            "confidenceBand": "medium",
            "explanation": f"The evidence supports this family, but not a reliable species-level identification.",
            "safeForRewarding": False,
        }

    return {
        "finalLabel": "unknown animal sound",
        "finalTaxonomicLevel": "unknown",
        "confidenceBand": "low",
        "explanation": "The clip likely contains an animal sound, but the evidence is too weak for a reliable taxonomic label.",
        "safeForRewarding": False,
    }


def message_record(payload: dict, response: dict) -> dict:
    return {
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=True)},
            {"role": "assistant", "content": json.dumps(response, ensure_ascii=True)},
        ]
    }


def generate_records(label_metadata: dict[str, dict], total_examples: int, seed: int) -> list[dict]:
    rng = random.Random(seed)
    family_to_species, genus_to_species = build_indexes(label_metadata)

    species_keys = [key for key, meta in label_metadata.items() if meta.get("taxonomic_level") == "species"]
    negative_keys = [key for key, meta in label_metadata.items() if (meta.get("display_label") or "") in NEGATIVE_LABELS]

    if not species_keys:
        raise ValueError("No species labels found in label metadata")
    if not negative_keys:
        raise ValueError("No negative labels found in label metadata")

    records: list[dict] = []
    profile_cycle = ["strong_species", "medium_species", "genus_fallback", "negative", "unknown"]

    for index in range(total_examples):
        profile = profile_cycle[index % len(profile_cycle)]
        label_key = sample_species_key(species_keys, rng)
        meta = label_metadata[label_key]
        alt_key = safe_alt_species(label_key, label_metadata, family_to_species, genus_to_species, rng)
        alt_meta = label_metadata[alt_key] if alt_key else None

        if profile == "strong_species":
            candidates = [candidate_from_meta(meta, rng.uniform(0.72, 0.9))]
            if alt_meta:
                candidates.append(candidate_from_meta(alt_meta, rng.uniform(0.08, 0.18)))
            if meta.get("family"):
                candidates.append(candidate_from_meta(meta, rng.uniform(0.03, 0.09), label_override=str(meta["family"]), taxonomic_level_override="family"))
            payload = build_payload(f"synthetic-strong-{index}", candidates[:3], rng.uniform(0.74, 0.96), rng)
        elif profile == "medium_species":
            candidates = [candidate_from_meta(meta, rng.uniform(0.5, 0.68))]
            if alt_meta:
                candidates.append(candidate_from_meta(alt_meta, rng.uniform(0.18, 0.32)))
            if meta.get("family"):
                candidates.append(candidate_from_meta(meta, rng.uniform(0.08, 0.16), label_override=str(meta["family"]), taxonomic_level_override="family"))
            payload = build_payload(f"synthetic-medium-{index}", candidates[:3], rng.uniform(0.63, 0.78), rng)
        elif profile == "genus_fallback":
            candidates = [candidate_from_meta(meta, rng.uniform(0.36, 0.55))]
            if alt_meta:
                candidates.append(candidate_from_meta(alt_meta, rng.uniform(0.22, 0.35)))
            if meta.get("genus"):
                candidates.append(candidate_from_meta(meta, rng.uniform(0.12, 0.2), label_override=str(meta["genus"]), taxonomic_level_override="genus"))
            elif meta.get("family"):
                candidates.append(candidate_from_meta(meta, rng.uniform(0.12, 0.2), label_override=str(meta["family"]), taxonomic_level_override="family"))
            payload = build_payload(f"synthetic-genus-{index}", candidates[:3], rng.uniform(0.42, 0.6), rng)
        elif profile == "negative":
            neg_key = negative_keys[index % len(negative_keys)]
            neg_meta = label_metadata[neg_key]
            candidates = [
                candidate_from_meta(neg_meta, rng.uniform(0.58, 0.86)),
                {
                    "label": "unknown animal sound",
                    "scientific_name": None,
                    "taxonomic_level": "unknown",
                    "score": round(rng.uniform(0.08, 0.2), 4),
                    "genus": None,
                    "family": None,
                },
                candidate_from_meta(meta, rng.uniform(0.03, 0.08)),
            ]
            payload = build_payload(f"synthetic-negative-{index}", candidates[:3], rng.uniform(0.2, 0.48), rng)
        else:
            candidates = [
                {
                    "label": "unknown animal sound",
                    "scientific_name": None,
                    "taxonomic_level": "unknown",
                    "score": round(rng.uniform(0.32, 0.48), 4),
                    "genus": None,
                    "family": None,
                },
                candidate_from_meta(meta, rng.uniform(0.18, 0.28)),
            ]
            if alt_meta:
                candidates.append(candidate_from_meta(alt_meta, rng.uniform(0.12, 0.22)))
            payload = build_payload(f"synthetic-unknown-{index}", candidates[:3], rng.uniform(0.12, 0.34), rng)

        records.append(message_record(payload, build_response(payload)))

    return records


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", required=True)
    parser.add_argument("--output-jsonl", required=True)
    parser.add_argument("--examples", type=int, default=4000)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()

    artifact_dir = Path(args.artifact_dir)
    output_path = Path(args.output_jsonl)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    records = generate_records(load_label_metadata(artifact_dir), total_examples=args.examples, seed=args.seed)
    with output_path.open("w") as outfile:
        for record in records:
            outfile.write(json.dumps(record, ensure_ascii=True) + "\n")

    print(f"Wrote {len(records)} records to {output_path}")


if __name__ == "__main__":
    main()
