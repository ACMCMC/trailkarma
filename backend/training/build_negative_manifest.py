from __future__ import annotations

import argparse
import csv
import re
from pathlib import Path


NEGATIVE_LABEL = "negative_background"
DISPLAY_LABELS = {
    "speech": "Human speech",
    "voice": "Human speech",
    "footstep": "Footsteps / gear rustle",
    "footsteps": "Footsteps / gear rustle",
    "gear": "Footsteps / gear rustle",
    "rustle": "Footsteps / gear rustle",
    "wind": "Wind / stream noise",
    "stream": "Wind / stream noise",
    "water": "Wind / stream noise",
    "traffic": "Traffic noise",
    "car": "Traffic noise",
    "dog": "Dog bark",
    "bark": "Dog bark",
    "silence": "Silence",
}


def canonical_display_label(raw_category: str) -> str:
    normalized = re.sub(r"[^a-z]+", " ", raw_category.lower()).strip()
    for key, display in DISPLAY_LABELS.items():
        if key in normalized:
            return display
    return raw_category.replace("_", " ").replace("-", " ").title()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", required=True, help="Directory containing subfolders like speech/, wind/, stream/")
    parser.add_argument("--output-csv", required=True)
    args = parser.parse_args()

    input_dir = Path(args.input_dir)
    rows = []
    for audio_file in sorted(input_dir.rglob("*")):
        if audio_file.is_dir():
            continue
        category = audio_file.parent.name
        display_label = canonical_display_label(category)
        rows.append(
            {
                "source_path": str(audio_file),
                "common_name": display_label,
                "scientific_name": NEGATIVE_LABEL,
                "genus": "",
                "family": "",
                "source": "negative-curation",
                "source_id": audio_file.stem,
                "split": "train",
                "is_negative": "true",
            }
        )

    with open(args.output_csv, "w", newline="") as outfile:
        writer = csv.DictWriter(
            outfile,
            fieldnames=[
                "source_path",
                "common_name",
                "scientific_name",
                "genus",
                "family",
                "source",
                "source_id",
                "split",
                "is_negative",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    main()
