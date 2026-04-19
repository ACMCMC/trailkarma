from __future__ import annotations

import argparse
import csv
import subprocess
from pathlib import Path


def ffmpeg_normalize(input_path: Path, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-i",
            str(input_path),
            "-ac",
            "1",
            "-ar",
            "16000",
            "-t",
            "5",
            str(output_path),
        ],
        check=True,
        capture_output=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-manifest", required=True, help="CSV with metadata and source paths")
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    manifest_out = output_dir / "normalized_manifest.csv"
    output_dir.mkdir(parents=True, exist_ok=True)

    with open(args.input_manifest, newline="") as infile, open(manifest_out, "w", newline="") as outfile:
        reader = csv.DictReader(infile)
        fieldnames = [
            "window_path",
            "common_name",
            "scientific_name",
            "genus",
            "family",
            "source",
            "source_id",
            "split",
            "is_negative",
        ]
        writer = csv.DictWriter(outfile, fieldnames=fieldnames)
        writer.writeheader()

        for row in reader:
            source_path = Path(row["source_path"])
            source_id = row.get("source_id", source_path.stem)
            target_path = output_dir / "windows" / f"{source_id}.wav"
            ffmpeg_normalize(source_path, target_path)
            writer.writerow(
                {
                    "window_path": str(target_path),
                    "common_name": row.get("common_name", ""),
                    "scientific_name": row.get("scientific_name", ""),
                    "genus": row.get("genus", ""),
                    "family": row.get("family", ""),
                    "source": row.get("source", ""),
                    "source_id": source_id,
                    "split": row.get("split", "train"),
                    "is_negative": row.get("is_negative", "false"),
                }
            )


if __name__ == "__main__":
    main()
