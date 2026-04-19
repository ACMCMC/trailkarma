from __future__ import annotations

import argparse
import csv
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
    parser.add_argument("--inputs", nargs="+", required=True, help="CSV manifests to merge")
    parser.add_argument("--output-csv", required=True)
    args = parser.parse_args()

    output_path = Path(args.output_csv)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    seen_source_ids: set[str] = set()
    rows: list[dict[str, str]] = []
    for input_path in args.inputs:
        with open(input_path, newline="") as infile:
            for row in csv.DictReader(infile):
                source_id = row.get("source_id") or Path(row["source_path"]).stem
                if source_id in seen_source_ids:
                    continue
                seen_source_ids.add(source_id)
                rows.append({field: row.get(field, "") for field in FIELDNAMES})

    with open(output_path, "w", newline="") as outfile:
        writer = csv.DictWriter(outfile, fieldnames=FIELDNAMES)
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    main()
