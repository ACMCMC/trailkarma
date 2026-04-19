from __future__ import annotations

import argparse
import csv
import json
import tarfile
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


def choose_targets(
    annotations: dict,
    max_per_species: int,
    max_species_per_supercategory: int,
    supercategories: set[str],
) -> tuple[dict[str, dict[str, str]], list[dict[str, str]]]:
    categories = {int(category["id"]): category for category in annotations["categories"]}
    audio_items = {int(audio["id"]): audio for audio in annotations["audio"]}

    category_to_audio: dict[int, list[dict]] = defaultdict(list)
    for ann in annotations["annotations"]:
        category_to_audio[int(ann["category_id"])].append(audio_items[int(ann["audio_id"])])

    selected_members: dict[str, dict[str, str]] = {}
    manifest_rows: list[dict[str, str]] = []
    chosen_species_by_super: dict[str, int] = defaultdict(int)

    for category_id, category in sorted(categories.items(), key=lambda item: item[1]["name"]):
        supercategory = category.get("supercategory", "")
        if supercategory not in supercategories:
            continue
        if chosen_species_by_super[supercategory] >= max_species_per_supercategory:
            continue

        recordings = sorted(category_to_audio.get(category_id, []), key=lambda item: int(item["id"]))
        if not recordings:
            continue
        chosen_species_by_super[supercategory] += 1

        category_dir = category.get("audio_dir_name") or str(category_id)
        for index, audio in enumerate(recordings[:max_per_species]):
            file_name = str(audio["file_name"]).lstrip("./")
            if "/" in file_name:
                relative_member = file_name
            else:
                relative_member = f"val/{category_dir}/{file_name}"
            manifest_rows.append(
                {
                    "relative_member": relative_member,
                    "common_name": category.get("common_name", category["name"]),
                    "scientific_name": category["name"],
                    "genus": category.get("genus", ""),
                    "family": category.get("family", ""),
                    "source": "iNatSounds",
                    "source_id": f"inat-{audio['id']}",
                    "split": "val" if index % 5 == 0 else "train",
                    "is_negative": "false",
                }
            )
            selected_members[relative_member] = manifest_rows[-1]
    return selected_members, manifest_rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--annotations-json", required=True)
    parser.add_argument("--audio-tar", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--output-csv", required=True)
    parser.add_argument("--max-per-species", type=int, default=8)
    parser.add_argument("--max-species-per-supercategory", type=int, default=80)
    parser.add_argument(
        "--supercategories",
        nargs="*",
        default=["Aves", "Insecta", "Amphibia", "Mammalia", "Reptilia"],
    )
    args = parser.parse_args()

    annotations = json.loads(Path(args.annotations_json).read_text())
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    selected_members, manifest_rows = choose_targets(
        annotations=annotations,
        max_per_species=args.max_per_species,
        max_species_per_supercategory=args.max_species_per_supercategory,
        supercategories=set(args.supercategories),
    )
    extracted_rows: list[dict[str, str]] = []

    with tarfile.open(args.audio_tar, "r:gz") as archive:
        for member in archive:
            if not member.isfile():
                continue
            member_name = member.name.lstrip("./")
            if member_name not in selected_members:
                continue

            metadata = selected_members[member_name]
            extracted_path = output_dir / member_name
            extracted_path.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as source, open(extracted_path, "wb") as destination:
                if source is None:
                    continue
                destination.write(source.read())

            extracted_rows.append(
                {
                    "source_path": str(extracted_path),
                    "common_name": metadata["common_name"],
                    "scientific_name": metadata["scientific_name"],
                    "genus": metadata["genus"],
                    "family": metadata["family"],
                    "source": metadata["source"],
                    "source_id": metadata["source_id"],
                    "split": metadata["split"],
                    "is_negative": metadata["is_negative"],
                }
            )

    with open(args.output_csv, "w", newline="") as outfile:
        writer = csv.DictWriter(outfile, fieldnames=FIELDNAMES)
        writer.writeheader()
        writer.writerows(extracted_rows)


if __name__ == "__main__":
    main()
