from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from typing import Any

from .schemas import LlmDecision, StructuredCandidatePayload


EXPLAINER_EXAMPLES = [
    {
        "user": (
            "Locked label: California scrub-jay\n"
            "Locked taxonomic level: species\n"
            "Locked confidence band: medium-high\n"
            "Safe for rewarding: True\n"
            "Raw acoustic confidence: 0.7100\n"
            "Background flags: wind_low\n"
            "Top acoustic candidates:\n"
            "- California scrub-jay, species, score=0.7100, genus=Aphelocoma, family=Corvidae\n"
            "- Steller's jay, species, score=0.1800, genus=Cyanocitta, family=Corvidae\n"
            "- Corvidae, family, score=0.1100, family=Corvidae"
        ),
        "assistant": "Likely California scrub-jay because it is the strongest acoustic match and the alternatives are weaker.",
    },
    {
        "user": (
            "Locked label: Pseudacris\n"
            "Locked taxonomic level: genus\n"
            "Locked confidence band: medium\n"
            "Safe for rewarding: True\n"
            "Raw acoustic confidence: 0.5100\n"
            "Background flags: none\n"
            "Top acoustic candidates:\n"
            "- Pacific treefrog, species, score=0.5100, genus=Pseudacris, family=Hylidae\n"
            "- Baja California treefrog, species, score=0.2900, genus=Pseudacris, family=Hylidae\n"
            "- Hylidae, family, score=0.2000, family=Hylidae"
        ),
        "assistant": "This sounds like Pseudacris, but species-level resolution is not reliable enough from this clip alone.",
    },
    {
        "user": (
            "Locked label: Human speech\n"
            "Locked taxonomic level: unknown\n"
            "Locked confidence band: high\n"
            "Safe for rewarding: False\n"
            "Raw acoustic confidence: 0.1800\n"
            "Background flags: handling_low\n"
            "Top acoustic candidates:\n"
            "- Human speech, unknown, score=0.8100\n"
            "- unknown animal sound, unknown, score=0.1200\n"
            "- Wind / stream noise, unknown, score=0.0700"
        ),
        "assistant": "This clip is dominated by human speech rather than a reliable wildlife sound.",
    },
]


@dataclass
class _RuntimeConfig:
    backend: str
    model_id: str
    adapter_path: str | None
    device: str


class LocalPostProcessor:
    def __init__(self) -> None:
        self.config = _RuntimeConfig(
            backend=os.getenv("TRAILKARMA_LLM_BACKEND", "deterministic"),
            model_id=os.getenv("TRAILKARMA_LLM_MODEL_ID", "unsloth/Llama-3.2-1B-Instruct"),
            adapter_path=os.getenv("TRAILKARMA_LLM_ADAPTER"),
            device=os.getenv("TRAILKARMA_LLM_DEVICE", "auto"),
        )
        self._generator = None

    def decide(self, payload: StructuredCandidatePayload) -> tuple[LlmDecision, dict[str, Any]]:
        locked_decision = self._fallback(payload)

        if self.config.backend == "local":
            try:
                explanation = self._call_local_explainer(payload, locked_decision)
                return locked_decision.model_copy(update={"explanation": explanation}), {
                    "provider": "local-transformers-explainer",
                    "model_id": self.config.model_id,
                    "adapter_path": self.config.adapter_path,
                    "decision_policy": "deterministic-rules",
                }
            except Exception as exc:
                return locked_decision, {
                    "provider": "deterministic-fallback",
                    "reason": str(exc),
                    "decision_policy": "deterministic-rules",
                }

        return locked_decision, {
            "provider": "deterministic-fallback",
            "reason": "TRAILKARMA_LLM_BACKEND not set to local",
            "decision_policy": "deterministic-rules",
        }

    def _call_local_explainer(self, payload: StructuredCandidatePayload, locked_decision: LlmDecision) -> str:
        generator = self._load_generator()
        prompt = self._build_explainer_prompt(payload, locked_decision)
        result = generator(prompt, max_new_tokens=80, do_sample=False, temperature=None)
        text = result[0]["generated_text"][len(prompt):].strip()
        explanation = self._extract_explanation(text)
        self._validate_explanation(explanation, payload, locked_decision)
        return explanation

    def _load_generator(self):
        if self._generator is not None:
            return self._generator

        from transformers import AutoModelForCausalLM, AutoTokenizer, pipeline

        model = AutoModelForCausalLM.from_pretrained(
            self.config.model_id,
            device_map=self.config.device,
            torch_dtype="auto",
        )

        if self.config.adapter_path:
            from peft import PeftModel

            model = PeftModel.from_pretrained(model, self.config.adapter_path)

        tokenizer = AutoTokenizer.from_pretrained(self.config.model_id)
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token

        self._generator = pipeline(
            "text-generation",
            model=model,
            tokenizer=tokenizer,
        )
        return self._generator

    def _build_explainer_prompt(self, payload: StructuredCandidatePayload, locked_decision: LlmDecision) -> str:
        system = (
            "You explain wildlife audio decisions. "
            "The backend decision is locked. Return one concise explanation sentence only. "
            "Keep the explanation concise and consistent with the locked decision. "
            "Do not change the label, taxonomic level, confidence band, or rewarding status. "
            "Do not repeat the prompt or output JSON."
        )
        candidate_lines = []
        for candidate in payload.top_candidates:
            parts = [candidate.label, candidate.taxonomic_level, f"score={candidate.score:.4f}"]
            if candidate.genus:
                parts.append(f"genus={candidate.genus}")
            if candidate.family:
                parts.append(f"family={candidate.family}")
            candidate_lines.append("- " + ", ".join(parts))

        user_prompt = "\n".join(
            [
                f"Locked label: {locked_decision.finalLabel}",
                f"Locked taxonomic level: {locked_decision.finalTaxonomicLevel}",
                f"Locked confidence band: {locked_decision.confidenceBand}",
                f"Safe for rewarding: {locked_decision.safeForRewarding}",
                f"Raw acoustic confidence: {payload.raw_confidence:.4f}",
                f"Background flags: {', '.join(payload.background_flags) if payload.background_flags else 'none'}",
                "Top acoustic candidates:",
                *candidate_lines,
            ]
        )
        examples = []
        for example in EXPLAINER_EXAMPLES:
            examples.append(f"<|eot_id|><|start_header_id|>user<|end_header_id|>\n{example['user']}")
            examples.append(f"<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n{example['assistant']}")

        return (
            f"<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n{system}"
            f"\n{''.join(examples)}"
            f"\n<|eot_id|><|start_header_id|>user<|end_header_id|>\n"
            f"{user_prompt}"
            f"\n<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n"
        )

    def _extract_json(self, text: str) -> str:
        start = text.find("{")
        end = text.rfind("}")
        if start == -1 or end == -1 or end <= start:
            raise ValueError(f"Model output did not contain valid JSON: {text[:300]}")
        return text[start : end + 1]

    def _extract_explanation(self, text: str) -> str:
        cleaned = text.replace("<|eot_id|>", " ").replace("<|end_of_text|>", " ").strip()

        try:
            json_blob = self._extract_json(cleaned)
            parsed = json.loads(json_blob)
            if isinstance(parsed, dict) and "explanation" in parsed:
                return str(parsed["explanation"]).strip()
        except Exception:
            pass

        line = next((part.strip() for part in cleaned.splitlines() if part.strip()), "")
        if line.lower().startswith("explanation:"):
            line = line.split(":", 1)[1].strip()
        line = line.strip().strip('"').strip("'")

        sentence_match = re.search(r"^(.+?[.!?])(\s|$)", line)
        if sentence_match:
            line = sentence_match.group(1).strip()

        return line[:240].strip()

    def _fallback(self, payload: StructuredCandidatePayload) -> LlmDecision:
        top = payload.top_candidates[0]
        background_labels = {
            "Human speech": ("high", "This clip is dominated by human speech rather than wildlife vocalization."),
            "Wind / stream noise": (
                "medium",
                "The strongest evidence is environmental background noise, so this is not safe to reward as a wildlife detection.",
            ),
            "Traffic noise": ("medium", "The clip is dominated by traffic or road noise rather than wildlife."),
            "Dog bark": ("medium", "The strongest signal is a domestic dog bark, not a trail biodiversity contribution."),
            "Footsteps / gear rustle": (
                "medium",
                "Handling noise and footsteps dominate the clip, so the wildlife evidence is not reliable.",
            ),
            "Silence": ("high", "There is not enough acoustic activity in the clip to support a wildlife detection."),
        }
        if top.label in background_labels:
            confidence_band, explanation = background_labels[top.label]
            return LlmDecision(
                finalLabel=top.label,
                finalTaxonomicLevel="unknown",
                confidenceBand=confidence_band,  # type: ignore[arg-type]
                explanation=explanation,
                safeForRewarding=False,
            )
        if top.taxonomic_level == "species" and payload.raw_confidence >= 0.65:
            return LlmDecision(
                finalLabel=top.label,
                finalTaxonomicLevel="species",
                confidenceBand="medium-high" if payload.raw_confidence < 0.8 else "high",
                explanation=f"Likely {top.label}. The top acoustic candidate is clearly strongest among the open-world matches.",
                safeForRewarding=True,
            )

        family_candidate = next((c for c in payload.top_candidates if c.taxonomic_level == "family"), None)
        if top.taxonomic_level == "species" and payload.raw_confidence >= 0.45:
            fallback_label = top.genus or (family_candidate.label if family_candidate else top.label)
            fallback_level = "genus" if top.genus else "family"
            return LlmDecision(
                finalLabel=fallback_label,
                finalTaxonomicLevel=fallback_level,  # type: ignore[arg-type]
                confidenceBand="medium",
                explanation=f"The clip appears wildlife-like, but species resolution is uncertain. {fallback_label} is the safest taxonomic level.",
                safeForRewarding=fallback_level == "genus",
            )

        return LlmDecision(
            finalLabel="unknown animal sound",
            finalTaxonomicLevel="unknown",
            confidenceBand="low",
            explanation="The clip likely contains an animal sound, but the evidence is too weak for a reliable species or genus call.",
            safeForRewarding=False,
        )

    def _validate_explanation(
        self,
        explanation: str,
        payload: StructuredCandidatePayload,
        locked_decision: LlmDecision,
    ) -> None:
        if not explanation:
            raise ValueError("Model returned an empty explanation")
        if len(explanation) > 280:
            raise ValueError("Model explanation is too long")
        if explanation.startswith("{") or "locked_decision" in explanation or "candidate_payload" in explanation:
            raise ValueError("Model explanation echoed internal payload structure")
        if explanation.casefold() == locked_decision.finalLabel.casefold():
            raise ValueError("Model explanation collapsed to the label only")
        if len(explanation.split()) < 6:
            raise ValueError("Model explanation is too short")

        normalized = explanation.casefold()
        locked_label = locked_decision.finalLabel.casefold()
        candidate_labels = {candidate.label.casefold() for candidate in payload.top_candidates}
        candidate_labels.update({"unknown animal sound", "human speech", "wind / stream noise"})

        conflicting = {
            label
            for label in candidate_labels
            if label != locked_label and label in normalized
        }
        if conflicting:
            raise ValueError(f"Model explanation referenced conflicting labels: {sorted(conflicting)}")
