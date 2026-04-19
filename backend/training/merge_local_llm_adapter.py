from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Merge a local LoRA adapter into its base model for downstream GGUF conversion."
    )
    parser.add_argument("--base-model", required=True, help="Base HF model id or path.")
    parser.add_argument("--adapter-path", required=True, help="Directory containing the trained PEFT adapter.")
    parser.add_argument("--output-dir", required=True, help="Where to save the merged model.")
    args = parser.parse_args()

    from peft import PeftModel
    from transformers import AutoModelForCausalLM, AutoTokenizer

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    model = AutoModelForCausalLM.from_pretrained(args.base_model, torch_dtype="auto", device_map="cpu")
    model = PeftModel.from_pretrained(model, args.adapter_path)
    merged = model.merge_and_unload()

    merged.save_pretrained(output_dir)
    tokenizer.save_pretrained(output_dir)


if __name__ == "__main__":
    main()
