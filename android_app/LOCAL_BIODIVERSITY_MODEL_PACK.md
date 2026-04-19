# Local Biodiversity Model Pack

The Android app now supports on-device biodiversity model packs from either bundled assets or app-writable storage.

Preferred search order at runtime:

- `filesDir/biodiversity_model/`
- `getExternalFilesDir(null)/biodiversity_model/`
- `app/src/main/assets/biodiversity/`

To bundle the model into the APK, place the exported Brev bundle under:

`app/src/main/assets/biodiversity/`

To sideload a future pack onto a device without rebuilding the APK, copy the same files into one of:

- `<app internal files>/biodiversity_model/`
- `<external app files>/biodiversity_model/`

Required files:

- `model_manifest.json`
- `perch_encoder.tflite`
- `classifier_weights.bin`
- `classifier_bias.bin`
- `classifier_classes.json`
- `prototype_embeddings.bin`
- `prototype_bank.json`
- `label_metadata.json`

Optional explainer files:

- `../biodiversity_explainer/explainer_manifest.json`
- `../biodiversity_explainer/*.gguf`

Expected `model_manifest.json` shape:

```json
{
  "model_file": "perch_encoder.tflite",
  "classifier_weights_file": "classifier_weights.bin",
  "classifier_bias_file": "classifier_bias.bin",
  "classifier_classes_file": "classifier_classes.json",
  "prototype_embeddings_file": "prototype_embeddings.bin",
  "prototype_embeddings_dtype": "float16",
  "prototype_bank_file": "prototype_bank.json",
  "label_metadata_file": "label_metadata.json",
  "embedding_dim": 1280,
  "window_sample_rate_hz": 32000,
  "window_seconds": 5.0,
  "model_version": "perch_8"
}
```

Binary layout:

- `classifier_weights.bin`: little-endian `float32`, row-major `[num_classes, embedding_dim]`
- `classifier_bias.bin`: little-endian `float32`, `[num_classes]`
- `prototype_embeddings.bin`: little-endian `float32` or `float16`, row-major `[num_prototypes, embedding_dim]`

Runtime behavior:

- If the full bundle is present, the app runs TFLite embedding + linear head + prototype retrieval locally.
- The currently bundled demo pack is an exported `perch_8` encoder plus a retrained linear head and prototype bank produced on Brev.
- The app records the model source in metadata as `internal_files`, `external_files`, or `app_assets`.
- If the bundle is missing, the app falls back to the existing lightweight heuristic path so the capture flow still works.
- The final label path remains deterministic on device. The app does not require backend classification anymore.
- The optional GGUF explainer pack is exported on Brev for future Android integration, but the current Android runtime still uses deterministic one-sentence explanations.
