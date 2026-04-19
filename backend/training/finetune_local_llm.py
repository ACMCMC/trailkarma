from __future__ import annotations

import argparse
from pathlib import Path

from datasets import load_dataset
from peft import LoraConfig
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from trl import SFTConfig, SFTTrainer


def format_example(record: dict) -> str:
    messages = record["messages"]
    text = "<|begin_of_text|>"
    for message in messages:
        text += f"<|start_header_id|>{message['role']}<|end_header_id|>\n{message['content']}\n<|eot_id|>"
    return text


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True, help="JSONL dataset from generate_impulse_jsonl.py")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--model-id", default="unsloth/Llama-3.2-1B-Instruct")
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--grad-accum", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    args = parser.parse_args()

    quant_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype="bfloat16",
    )

    tokenizer = AutoTokenizer.from_pretrained(args.model_id)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    model = AutoModelForCausalLM.from_pretrained(
        args.model_id,
        quantization_config=quant_config,
        device_map="auto",
    )

    dataset = load_dataset("json", data_files=args.dataset, split="train")
    dataset = dataset.map(lambda row: {"text": format_example(row)}, remove_columns=dataset.column_names)

    peft_config = LoraConfig(
        r=16,
        lora_alpha=32,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )

    trainer = SFTTrainer(
        model=model,
        processing_class=tokenizer,
        train_dataset=dataset,
        peft_config=peft_config,
        args=SFTConfig(
            output_dir=args.output_dir,
            per_device_train_batch_size=args.batch_size,
            gradient_accumulation_steps=args.grad_accum,
            num_train_epochs=args.epochs,
            learning_rate=args.learning_rate,
            logging_steps=10,
            save_strategy="epoch",
            bf16=True,
            report_to="none",
            dataset_text_field="text",
            max_seq_length=1024,
        ),
    )
    trainer.train()
    trainer.model.save_pretrained(Path(args.output_dir) / "adapter")
    tokenizer.save_pretrained(Path(args.output_dir) / "adapter")


if __name__ == "__main__":
    main()
