from __future__ import annotations

import argparse
import json
from pathlib import Path


SYSTEM_PROMPT = (
    "You are given candidate wildlife detections from an audio model. "
    "Return valid JSON with keys finalLabel, finalTaxonomicLevel, confidenceBand, explanation, safeForRewarding. "
    "Do not invent a species not present in the candidate list."
)


def synthesize_response(payload: dict) -> dict:
    top = payload["top_candidates"][0]
    if top["label"] in {"Human speech", "Wind / stream noise"}:
        return {
            "finalLabel": top["label"],
            "finalTaxonomicLevel": "unknown",
            "confidenceBand": "high" if top["label"] == "Human speech" else "medium",
            "explanation": f"The strongest signal is {top['label'].lower()}, so this should not be rewarded as wildlife.",
            "safeForRewarding": False,
        }
    if top["taxonomic_level"] == "species" and payload["raw_confidence"] >= 0.65:
        return {
            "finalLabel": top["label"],
            "finalTaxonomicLevel": "species",
            "confidenceBand": "medium-high",
            "explanation": f"Likely {top['label']}. The top candidate is distinctly stronger than the alternatives.",
            "safeForRewarding": True,
        }
    genus = top.get("genus")
    if genus:
        return {
            "finalLabel": genus,
            "finalTaxonomicLevel": "genus",
            "confidenceBand": "medium",
            "explanation": f"The clip appears wildlife-like, but genus-level wording is safer than a species claim here.",
            "safeForRewarding": True,
        }
    return {
        "finalLabel": "unknown animal sound",
        "finalTaxonomicLevel": "unknown",
        "confidenceBand": "low",
        "explanation": "The clip likely contains an animal sound, but the evidence is too weak for a reliable taxonomic label.",
        "safeForRewarding": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-jsonl", required=True, help="Classifier output payloads in JSONL format")
    parser.add_argument("--output-jsonl", required=True)
    args = parser.parse_args()

    output_path = Path(args.output_jsonl)
    with open(args.input_jsonl) as infile, open(output_path, "w") as outfile:
        for line in infile:
            payload = json.loads(line)
            record = {
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": json.dumps(payload)},
                    {"role": "assistant", "content": json.dumps(synthesize_response(payload))},
                ]
            }
            outfile.write(json.dumps(record) + "\n")


if __name__ == "__main__":
    main()
