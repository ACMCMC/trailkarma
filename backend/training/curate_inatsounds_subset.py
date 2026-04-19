from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path


FIELDNAMES = [
    "source_path",
    "common_name",
    "scientific_name",
    "genus",
    "family",
    "source",
    "source_id",
    "split",
    "is_negative",
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--annotations-json", required=True, help="Extracted iNatSounds JSON annotations file")
    parser.add_argument("--audio-root", required=True, help="Directory containing extracted train/ or val/ audio folders")
    parser.add_argument("--output-csv", required=True)
    parser.add_argument("--max-per-species", type=int, default=8)
    parser.add_argument("--max-species-per-supercategory", type=int, default=80)
    parser.add_argument(
        "--supercategories",
        nargs="*",
        default=["Aves", "Insecta", "Amphibia", "Mammalia", "Reptilia"],
        help="Subset of iNatSounds supercategories to include",
    )
    args = parser.parse_args()

    annotations = json.loads(Path(args.annotations_json).read_text())
    audio_root = Path(args.audio_root)
    output_path = Path(args.output_csv)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    categories = {int(category["id"]): category for category in annotations["categories"]}
    audio_items = {int(audio["id"]): audio for audio in annotations["audio"]}

    category_to_audio: dict[int, list[dict]] = defaultdict(list)
    for ann in annotations["annotations"]:
        category_to_audio[int(ann["category_id"])].append(audio_items[int(ann["audio_id"])])

    chosen_species_by_super: dict[str, int] = defaultdict(int)
    rows: list[dict[str, str]] = []
    for category_id, category in sorted(categories.items(), key=lambda item: item[1]["name"]):
        supercategory = category.get("supercategory", "")
        if supercategory not in args.supercategories:
            continue
        if chosen_species_by_super[supercategory] >= args.max_species_per_supercategory:
            continue

        chosen_species_by_super[supercategory] += 1
        category_dir = category.get("audio_dir_name") or str(category_id)
        recordings = sorted(category_to_audio.get(category_id, []), key=lambda item: int(item["id"]))
        for index, audio in enumerate(recordings[: args.max_per_species]):
            relative_path = Path(category_dir) / audio["file_name"]
            absolute_path = audio_root / relative_path
            if not absolute_path.exists():
                fallback_path = audio_root / audio["file_name"]
                if fallback_path.exists():
                    absolute_path = fallback_path
                else:
                    continue

            scientific_name = category["name"]
            source_id = f"inat-{audio['id']}"
            rows.append(
                {
                    "source_path": str(absolute_path),
                    "common_name": category.get("common_name", scientific_name),
                    "scientific_name": scientific_name,
                    "genus": category.get("genus", ""),
                    "family": category.get("family", ""),
                    "source": "iNatSounds",
                    "source_id": source_id,
                    "split": "val" if index % 5 == 0 else "train",
                    "is_negative": "false",
                }
            )

    with open(output_path, "w", newline="") as outfile:
        writer = csv.DictWriter(outfile, fieldnames=FIELDNAMES)
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    main()
