from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report


def load_inputs(embeddings_path: Path, manifest_path: Path) -> tuple[np.ndarray, list[dict[str, str]]]:
    embeddings = np.load(embeddings_path)
    with open(manifest_path, newline="") as infile:
        rows = list(csv.DictReader(infile))
    if len(rows) != len(embeddings):
        raise ValueError("Embedding count does not match manifest row count")
    return embeddings, rows


def canonical_label_key(row: dict[str, str]) -> str:
    if row.get("is_negative", "").lower() == "true":
        common_name = row.get("common_name", "background").strip().lower().replace(" ", "_")
        return f"negative::{common_name}"
    if row.get("scientific_name"):
        return f"species::{row['scientific_name']}"
    if row.get("genus"):
        return f"genus::{row['genus']}"
    if row.get("family"):
        return f"family::{row['family']}"
    return "unknown::animal_sound"


def label_metadata_for_row(row: dict[str, str]) -> dict[str, str | bool | None]:
    is_negative = row.get("is_negative", "").lower() == "true"
    if is_negative:
        return {
            "display_label": row.get("common_name") or "Background noise",
            "scientific_name": None,
            "genus": None,
            "family": None,
            "taxonomic_level": "unknown",
            "is_negative": True,
        }
    if row.get("scientific_name"):
        return {
            "display_label": row.get("common_name") or row["scientific_name"],
            "scientific_name": row["scientific_name"],
            "genus": row.get("genus") or None,
            "family": row.get("family") or None,
            "taxonomic_level": "species",
            "is_negative": False,
        }
    if row.get("genus"):
        return {
            "display_label": row["genus"],
            "scientific_name": None,
            "genus": row["genus"],
            "family": row.get("family") or None,
            "taxonomic_level": "genus",
            "is_negative": False,
        }
    if row.get("family"):
        return {
            "display_label": row["family"],
            "scientific_name": None,
            "genus": None,
            "family": row["family"],
            "taxonomic_level": "family",
            "is_negative": False,
        }
    return {
        "display_label": "unknown animal sound",
        "scientific_name": None,
        "genus": None,
        "family": None,
        "taxonomic_level": "unknown",
        "is_negative": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--embeddings", required=True, help="NumPy .npy file from Hoplite embedding export")
    parser.add_argument("--manifest", required=True, help="Normalized manifest CSV")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--prototype-count", type=int, default=8)
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    embeddings, rows = load_inputs(Path(args.embeddings), Path(args.manifest))
    embedding_metadata_path = Path(args.embeddings).with_name("embedding_metadata.json")
    embedding_metadata = json.loads(embedding_metadata_path.read_text()) if embedding_metadata_path.exists() else {}

    label_keys = [canonical_label_key(row) for row in rows]
    label_metadata = {}
    for row, label_key in zip(rows, label_keys, strict=True):
        label_metadata.setdefault(label_key, label_metadata_for_row(row))

    train_indices = [i for i, row in enumerate(rows) if row.get("split", "train") == "train"]
    val_indices = [i for i, row in enumerate(rows) if row.get("split", "train") != "train"]

    x_train = embeddings[train_indices]
    y_train = [label_keys[i] for i in train_indices]
    classifier = LogisticRegression(max_iter=2000, class_weight="balanced")
    classifier.fit(x_train, y_train)

    if val_indices:
        predictions = classifier.predict(embeddings[val_indices])
        report = classification_report([label_keys[i] for i in val_indices], predictions, output_dict=True)
    else:
        report = {"warning": "no validation rows provided"}

    by_taxon: dict[str, list[tuple[float, dict[str, str], np.ndarray]]] = defaultdict(list)
    probabilities = classifier.predict_proba(embeddings)
    classes = classifier.classes_
    class_lookup = {label: int(index) for index, label in enumerate(classes)}
    for index, (row, label_key) in enumerate(zip(rows, label_keys, strict=True)):
        class_index = class_lookup[label_key]
        confidence = float(probabilities[index, class_index])
        by_taxon[label_key].append((confidence, row, embeddings[index]))

    prototype_bank: list[dict[str, str | float | bool | None]] = []
    prototype_embeddings: list[np.ndarray] = []
    for label_key, ranked_rows in by_taxon.items():
        for confidence, row, embedding in sorted(ranked_rows, key=lambda item: item[0], reverse=True)[: args.prototype_count]:
            meta = label_metadata[label_key]
            prototype_bank.append(
                {
                    "label_key": label_key,
                    "label": meta["display_label"],
                    "confidence": confidence,
                    "scientific_name": meta["scientific_name"],
                    "genus": meta["genus"],
                    "family": meta["family"],
                    "taxonomic_level": meta["taxonomic_level"],
                    "is_negative": meta["is_negative"],
                    "source": row.get("source") or None,
                    "source_id": row.get("source_id") or None,
                    "window_path": row.get("window_path") or row.get("source_path") or None,
                }
            )
            prototype_embeddings.append(embedding)

    (output_dir / "prototype_bank.json").write_text(json.dumps(prototype_bank, indent=2))
    np.save(output_dir / "prototype_embeddings.npy", np.stack(prototype_embeddings, axis=0))
    (output_dir / "label_metadata.json").write_text(json.dumps(label_metadata, indent=2))
    (output_dir / "metadata.json").write_text(
        json.dumps(
            {
                "encoder": "Perch-Hoplite",
                "classifier": "logistic-regression",
                "classifier_score_transform": "softmax",
                "retrieval_metric": "cosine",
                "prototype_count_per_taxon": args.prototype_count,
                "class_count": len(classes),
                "training_rows": len(train_indices),
                "validation_rows": len(val_indices),
                "validation_report": report,
                **embedding_metadata,
            },
            indent=2,
        )
    )
    np.save(output_dir / "classifier_coef.npy", classifier.coef_)
    np.save(output_dir / "classifier_intercept.npy", classifier.intercept_)
    (output_dir / "classifier_classes.json").write_text(json.dumps(classifier.classes_.tolist(), indent=2))


if __name__ == "__main__":
    main()
