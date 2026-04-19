from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path
from typing import Any

import numpy as np


def _load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text())


def _write_bin(path: Path, values: np.ndarray, dtype: str) -> tuple[str, int]:
    normalized = np.asarray(values)
    if dtype == "float16":
        encoded = normalized.astype(np.float16)
        path.write_bytes(encoded.tobytes(order="C"))
        return "float16", int(encoded.size)
    encoded = normalized.astype(np.float32)
    path.write_bytes(encoded.tobytes(order="C"))
    return "float32", int(encoded.size)


def _copy_json(path: Path, destination: Path) -> None:
    destination.write_text(json.dumps(json.loads(path.read_text()), indent=2))


def _build_tflite_model(
    metadata: dict[str, Any],
    destination: Path,
    allow_select_ops: bool,
) -> dict[str, Any]:
    try:
        import tensorflow as tf
        from perch_hoplite.zoo import model_configs
    except Exception as exc:  # pragma: no cover - Brev/runtime dependent
        return {
            "status": "skipped",
            "reason": f"tensorflow/perch_hoplite unavailable: {exc}",
        }

    preset_name = metadata.get("model_preset_name") or metadata.get("model_key")
    if not preset_name:
        return {
            "status": "skipped",
            "reason": "metadata.json did not include model_preset_name or model_key",
        }

    preset = model_configs.get_preset_model_config(str(preset_name))
    embedding_model = preset.load_model()
    target_rate = int(
        metadata.get("sample_rate_hz")
        or getattr(embedding_model, "sample_rate", preset.model_config.sample_rate)
    )
    window_seconds = float(
        metadata.get("window_size_s")
        or getattr(embedding_model, "window_size_s", preset.model_config.window_size_s)
    )
    target_samples = int(round(target_rate * window_seconds))
    embedding_dim = int(metadata.get("embedding_dim") or preset.embedding_dim)

    def configure_converter(converter: Any) -> Any:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
        if allow_select_ops:
            converter.target_spec.supported_ops.append(tf.lite.OpsSet.SELECT_TF_OPS)
        return converter

    signature = getattr(getattr(embedding_model, "model", None), "signatures", {}).get("serving_default")
    if signature is not None:
        signature_ops = {op.type for op in signature.graph.get_operations()}
        if "XlaCallModule" not in signature_ops:
            class SignatureEmbeddingModule(tf.Module):
                def __init__(self, serving_fn: Any, output_dim: int) -> None:
                    super().__init__()
                    self._serving_fn = serving_fn
                    self._output_dim = output_dim

                @tf.function(input_signature=[tf.TensorSpec(shape=[None, target_samples], dtype=tf.float32)])
                def __call__(self, audio: tf.Tensor) -> tf.Tensor:
                    outputs = self._serving_fn(inputs=audio)
                    embeddings = tf.convert_to_tensor(outputs["embedding"])
                    embeddings = tf.reshape(
                        embeddings,
                        [tf.shape(audio)[0], -1, tf.shape(embeddings)[-1]],
                    )
                    projected = embeddings[:, 0, :]
                    if projected.shape.rank != 2:
                        projected = tf.reshape(projected, [tf.shape(audio)[0], self._output_dim])
                    return projected

            module = SignatureEmbeddingModule(signature, embedding_dim)
            concrete = module.__call__.get_concrete_function()
            converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete], module)
            tflite_model = configure_converter(converter).convert()
            destination.write_bytes(tflite_model)
            return {
                "status": "exported",
                "model_preset_name": preset.preset_name,
                "sample_rate_hz": target_rate,
                "window_seconds": window_seconds,
                "embedding_dim": embedding_dim,
                "select_tf_ops": allow_select_ops,
                "export_path": "savedmodel_signature",
            }

    class EmbeddingModule(tf.Module):
        def __init__(self, wrapped_model: Any, output_dim: int) -> None:
            super().__init__()
            self._wrapped_model = wrapped_model
            self._output_dim = output_dim

        @tf.function(input_signature=[tf.TensorSpec(shape=[None, target_samples], dtype=tf.float32)])
        def __call__(self, audio: tf.Tensor) -> tf.Tensor:
            outputs = self._wrapped_model.batch_embed(audio)
            embeddings = tf.convert_to_tensor(outputs.embeddings)
            embeddings = tf.reshape(
                embeddings,
                [tf.shape(audio)[0], -1, tf.shape(embeddings)[-1]],
            )
            projected = embeddings[:, 0, :]
            if projected.shape.rank != 2:
                projected = tf.reshape(projected, [tf.shape(audio)[0], self._output_dim])
            return projected

    module = EmbeddingModule(embedding_model, embedding_dim)
    concrete = module.__call__.get_concrete_function()
    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete], module)
    tflite_model = configure_converter(converter).convert()
    destination.write_bytes(tflite_model)
    return {
        "status": "exported",
        "model_preset_name": preset.preset_name,
        "sample_rate_hz": target_rate,
        "window_seconds": window_seconds,
        "embedding_dim": embedding_dim,
        "select_tf_ops": allow_select_ops,
        "export_path": "batch_embed_wrapper",
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Package trained biodiversity artifacts into the Android asset format."
    )
    parser.add_argument(
        "--artifact-dir",
        required=True,
        help="Directory containing classifier/prototype artifacts from train_open_world_head.py",
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        help="Destination directory, e.g. android_app/app/src/main/assets/biodiversity",
    )
    parser.add_argument(
        "--prototype-dtype",
        default="float16",
        choices=["float16", "float32"],
        help="Storage dtype for prototype embeddings in the mobile bundle.",
    )
    parser.add_argument(
        "--classifier-dtype",
        default="float32",
        choices=["float32"],
        help="Storage dtype for classifier weights and bias.",
    )
    parser.add_argument(
        "--skip-tflite-export",
        action="store_true",
        help="Package the head/prototype artifacts without attempting to export the Perch encoder to TFLite.",
    )
    parser.add_argument(
        "--allow-select-tf-ops",
        action="store_true",
        help="Allow SELECT_TF_OPS during TFLite conversion if the Perch graph requires them.",
    )
    args = parser.parse_args()

    artifact_dir = Path(args.artifact_dir)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    required = [
        artifact_dir / "classifier_coef.npy",
        artifact_dir / "classifier_intercept.npy",
        artifact_dir / "classifier_classes.json",
        artifact_dir / "label_metadata.json",
        artifact_dir / "prototype_bank.json",
        artifact_dir / "prototype_embeddings.npy",
        artifact_dir / "metadata.json",
    ]
    missing = [path for path in required if not path.exists()]
    if missing:
        raise FileNotFoundError(f"Missing required artifact files: {missing}")

    metadata = _load_json(artifact_dir / "metadata.json")
    classifier_coef = np.load(artifact_dir / "classifier_coef.npy")
    classifier_intercept = np.load(artifact_dir / "classifier_intercept.npy")
    prototype_embeddings = np.load(artifact_dir / "prototype_embeddings.npy")

    classifier_dtype, classifier_value_count = _write_bin(
        output_dir / "classifier_weights.bin",
        classifier_coef,
        args.classifier_dtype,
    )
    bias_dtype, bias_value_count = _write_bin(
        output_dir / "classifier_bias.bin",
        classifier_intercept,
        args.classifier_dtype,
    )
    prototype_dtype, prototype_value_count = _write_bin(
        output_dir / "prototype_embeddings.bin",
        prototype_embeddings,
        args.prototype_dtype,
    )

    _copy_json(artifact_dir / "classifier_classes.json", output_dir / "classifier_classes.json")
    _copy_json(artifact_dir / "label_metadata.json", output_dir / "label_metadata.json")
    _copy_json(artifact_dir / "prototype_bank.json", output_dir / "prototype_bank.json")

    tflite_result: dict[str, Any]
    if args.skip_tflite_export:
        tflite_result = {"status": "skipped", "reason": "skip flag set"}
    else:
        tflite_result = _build_tflite_model(
            metadata=metadata,
            destination=output_dir / "perch_encoder.tflite",
            allow_select_ops=args.allow_select_tf_ops,
        )

    manifest = {
        "model_file": "perch_encoder.tflite",
        "classifier_weights_file": "classifier_weights.bin",
        "classifier_bias_file": "classifier_bias.bin",
        "classifier_classes_file": "classifier_classes.json",
        "prototype_embeddings_file": "prototype_embeddings.bin",
        "prototype_embeddings_dtype": prototype_dtype,
        "prototype_bank_file": "prototype_bank.json",
        "label_metadata_file": "label_metadata.json",
        "embedding_dim": int(metadata.get("embedding_dim") or classifier_coef.shape[1]),
        "window_sample_rate_hz": int(metadata.get("sample_rate_hz") or 32000),
        "window_seconds": float(metadata.get("window_size_s") or 5.0),
        "model_version": metadata.get("model_preset_name") or metadata.get("model_key") or "perch-mobile-v1",
    }
    (output_dir / "model_manifest.json").write_text(json.dumps(manifest, indent=2))

    summary = {
        "source_artifact_dir": str(artifact_dir.resolve()),
        "output_dir": str(output_dir.resolve()),
        "classifier_shape": list(classifier_coef.shape),
        "classifier_dtype": classifier_dtype,
        "classifier_value_count": classifier_value_count,
        "bias_dtype": bias_dtype,
        "bias_value_count": bias_value_count,
        "prototype_shape": list(prototype_embeddings.shape),
        "prototype_dtype": prototype_dtype,
        "prototype_value_count": prototype_value_count,
        "class_count": len(json.loads((artifact_dir / "classifier_classes.json").read_text())),
        "prototype_count": int(prototype_embeddings.shape[0]),
        "tflite_export": tflite_result,
        "metadata_snapshot": metadata,
    }
    (output_dir / "pack_summary.json").write_text(json.dumps(summary, indent=2))

    # Keep a copy of the backend-side metadata for debugging/parity checks.
    shutil.copy2(artifact_dir / "metadata.json", output_dir / "backend_metadata.json")


if __name__ == "__main__":
    main()
