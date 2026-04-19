from __future__ import annotations

import argparse
import importlib
import os
import sys
from pathlib import Path
from typing import Iterable

# Keep JAX and TensorFlow on CPU during export to avoid GPU/XLA graph generation.
os.environ.setdefault("JAX_PLATFORMS", "cpu")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")

import jax  # noqa: E402
from jax.experimental import jax2tf  # noqa: E402
import jax.numpy as jnp  # noqa: E402
import tensorflow as tf  # noqa: E402


def _load_perch_modules(perch_repo: Path):
    sys.path.insert(0, str(perch_repo))
    from chirp import config_utils  # type: ignore
    from chirp.configs import config_globals  # type: ignore
    from chirp.train import classifier  # type: ignore

    return config_utils, config_globals, classifier


def _load_config(module_name: str, function_name: str, config_arg: str | None):
    module = importlib.import_module(module_name)
    config_fn = getattr(module, function_name)
    if config_arg is None:
        return config_fn()
    return config_fn(config_arg)


class FixedBatchEmbeddingModule(tf.Module):
    """Exports only the embedding head with a fixed input shape."""

    def __init__(
        self,
        infer_fn,
        variables,
        sample_count: int,
        embedding_dim: int,
        jit_compile: bool,
    ) -> None:
        super().__init__()
        self._structured_variables = tf.nest.map_structure(tf.Variable, variables)
        converted = jax2tf.convert(
            infer_fn,
            enable_xla=False,
            with_gradient=False,
        )

        def _infer(audio: tf.Tensor) -> tf.Tensor:
            outputs = converted(audio, self._structured_variables)
            embedding = outputs["embedding"]
            embedding = tf.convert_to_tensor(embedding)
            return tf.reshape(embedding, [1, embedding_dim])

        self.infer_tf = tf.function(
            _infer,
            jit_compile=jit_compile,
            input_signature=[tf.TensorSpec(shape=[1, sample_count], dtype=tf.float32)],
        )

    def __call__(self, audio: tf.Tensor) -> tf.Tensor:
        return self.infer_tf(audio)


def _normalize_output_keys(keys: Iterable[str] | None) -> list[str]:
    if not keys:
        return ["embedding"]
    normalized = [key.strip() for key in keys if key.strip()]
    if "embedding" not in normalized:
        normalized.insert(0, "embedding")
    return normalized


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Export a Perch JAX checkpoint to a mobile-friendly TFLite embedding "
            "model using non-XLA jax2tf conversion."
        )
    )
    parser.add_argument(
        "--perch-repo",
        required=True,
        help="Path to a local clone of google-research/perch.",
    )
    parser.add_argument(
        "--workdir",
        required=True,
        help="Perch training workdir containing checkpoint state.",
    )
    parser.add_argument(
        "--config-module",
        required=True,
        help="Python module path exposing get_config(), e.g. chirp.configs.baseline.",
    )
    parser.add_argument(
        "--config-function",
        default="get_config",
        help="Config factory name inside --config-module.",
    )
    parser.add_argument(
        "--config-arg",
        default=None,
        help="Optional single string arg passed to the config factory.",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Destination .tflite path.",
    )
    parser.add_argument(
        "--select-tf-ops",
        action="store_true",
        help="Allow SELECT_TF_OPS during conversion.",
    )
    parser.add_argument(
        "--jit-compile",
        action="store_true",
        help=(
            "Enable jit_compile on the TensorFlow wrapper. Leave this off by default "
            "to avoid recreating XLA-backed graphs."
        ),
    )
    parser.add_argument(
        "--output-key",
        action="append",
        default=None,
        help=(
            "Model output key to keep from the Perch model. Repeatable. "
            "Defaults to embedding only."
        ),
    )
    args = parser.parse_args()

    perch_repo = Path(args.perch_repo).resolve()
    workdir = Path(args.workdir).resolve()
    output_path = Path(args.output).resolve()

    if not perch_repo.exists():
        raise FileNotFoundError(f"Perch repo not found: {perch_repo}")
    if not workdir.exists():
        raise FileNotFoundError(f"Checkpoint workdir not found: {workdir}")

    config_utils, config_globals, classifier = _load_perch_modules(perch_repo)

    raw_config = _load_config(args.config_module, args.config_function, args.config_arg)
    config = config_utils.parse_config(raw_config, config_globals.get_globals())

    model_bundle, train_state = classifier.initialize_model(
        model_config=config.init_config.model_config,
        rng_seed=config.init_config.rng_seed,
        input_shape=tuple(config.init_config.input_shape),
        learning_rate=config.init_config.learning_rate,
        workdir=str(workdir),
        output_head_metadatas=config.init_config.output_head_metadatas,
        optimizer=None,
        for_inference=True,
    )
    train_state = model_bundle.ckpt.restore_or_initialize(train_state)

    output_keys = _normalize_output_keys(args.output_key)
    sample_count = int(tuple(config.init_config.input_shape)[0])

    variables = {"params": train_state.params, **train_state.model_state}

    def infer_fn(audio_batch, variables_dict):
        outputs = model_bundle.model.apply(variables_dict, audio_batch, train=False)
        filtered = {key: value for key, value in outputs.items() if key in output_keys}
        if "embedding" not in filtered:
            raise KeyError(f"Embedding output missing from model outputs: {tuple(outputs.keys())}")
        return filtered

    sample_embedding = infer_fn(jnp.zeros((1, sample_count), dtype=jnp.float32), variables)["embedding"]
    embedding_dim = int(sample_embedding.shape[-1])

    dummy = tf.zeros([1, sample_count], dtype=tf.float32)
    module = FixedBatchEmbeddingModule(
        infer_fn=infer_fn,
        variables=variables,
        sample_count=sample_count,
        embedding_dim=embedding_dim,
        jit_compile=args.jit_compile,
    )
    concrete = module.infer_tf.get_concrete_function(dummy)

    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete], module)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float32]
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    if args.select_tf_ops:
        converter.target_spec.supported_ops.append(tf.lite.OpsSet.SELECT_TF_OPS)

    tflite_model = converter.convert()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)

    print(f"Exported TFLite model to {output_path}")
    print(f"Checkpoint workdir: {workdir}")
    print(f"Config module: {args.config_module}")
    print(f"Input samples: {sample_count}")
    print(f"Embedding dim: {module.infer_tf.get_concrete_function(dummy).outputs[0].shape[-1]}")
    print(f"Select TF ops: {args.select_tf_ops}")
    print(f"jit_compile: {args.jit_compile}")


if __name__ == "__main__":
    main()
