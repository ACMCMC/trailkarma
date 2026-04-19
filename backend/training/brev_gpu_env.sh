#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${VIRTUAL_ENV:-}" ]]; then
  echo "Activate the Python virtualenv before sourcing brev_gpu_env.sh" >&2
  return 1 2>/dev/null || exit 1
fi

NVIDIA_LIBS="$(
python - <<'PY'
import pathlib
import site

parts = []
for root in map(pathlib.Path, site.getsitepackages()):
    nvidia = root / "nvidia"
    if not nvidia.exists():
        continue
    for child in sorted(nvidia.iterdir()):
        lib = child / "lib"
        if lib.exists():
            parts.append(str(lib))
print(":".join(parts))
PY
)"

if [[ -n "${NVIDIA_LIBS}" ]]; then
  export LD_LIBRARY_PATH="${NVIDIA_LIBS}${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"
fi

echo "LD_LIBRARY_PATH configured for TensorFlow/PyTorch CUDA libs"
