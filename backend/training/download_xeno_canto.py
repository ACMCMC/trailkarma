from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

import requests


def fetch_recordings(species_name: str, limit: int) -> list[dict]:
    response = requests.get(
        "https://xeno-canto.org/api/2/recordings",
        params={"query": species_name},
        headers={"User-Agent": "TrailKarma-Bioacoustics/0.1"},
        timeout=30,
    )
    if response.status_code >= 400 and "Anubis" in response.text:
        raise RuntimeError(
            "xeno-canto blocked this headless request with its Anubis anti-bot gate. "
            "Use a browser-assisted/manual export path for xeno-canto recordings."
        )
    response.raise_for_status()
    recordings = response.json().get("recordings", [])
    return recordings[:limit]


def download_file(url: str, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with requests.get(url, timeout=60, stream=True) as response:
        response.raise_for_status()
        with open(path, "wb") as outfile:
            for chunk in response.iter_content(chunk_size=1 << 20):
                if chunk:
                    outfile.write(chunk)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--species-file", required=True, help="Text file with one species query per line")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--per-species", type=int, default=20)
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    metadata_path = output_dir / "xeno_canto_manifest.csv"
    output_dir.mkdir(parents=True, exist_ok=True)

    with open(args.species_file) as infile:
        species_queries = [line.strip() for line in infile if line.strip() and not line.startswith("#")]

    with open(metadata_path, "w", newline="") as outfile:
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

        for species_name in species_queries:
            recordings = fetch_recordings(species_name, args.per_species)
            for index, recording in enumerate(recordings):
                scientific_name = recording.get("gen", "").strip() + " " + recording.get("sp", "").strip()
                scientific_name = scientific_name.strip()
                source_id = f"xc-{recording['id']}"
                extension = Path(recording["file"]).suffix or ".mp3"
                output_path = output_dir / "audio" / species_name.replace(" ", "_") / f"{source_id}{extension}"
                download_file(recording["file"], output_path)
                writer.writerow(
                    {
                        "source_path": str(output_path),
                        "common_name": recording.get("en", species_name),
                        "scientific_name": scientific_name,
                        "genus": recording.get("gen", ""),
                        "family": "",
                        "source": "xeno-canto",
                        "source_id": source_id,
                        "split": "train" if index % 5 else "val",
                        "is_negative": "false",
                    }
                )


if __name__ == "__main__":
    main()
