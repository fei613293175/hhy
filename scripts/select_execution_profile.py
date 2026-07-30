#!/usr/bin/env python3
"""Select the auditable Codex execution model for one concrete work item."""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / "config" / "DEVELOPMENT_RUNTIME.yaml"
COMPLEXITIES = ("complex", "medium", "light")


def load_policy(path: Path = POLICY_PATH) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def normalized_models(models: list[str] | None) -> set[str]:
    if models:
        return {model.strip().lower() for model in models if model.strip()}
    configured = os.environ.get("CODEX_AVAILABLE_MODELS", "")
    if configured.strip():
        return {model.strip().lower() for model in configured.split(",") if model.strip()}
    # This Codex runtime currently exposes Sol and Terra. Luna must be explicitly
    # advertised before it can be selected.
    return {"sol", "terra"}


def select_profile(complexity: str, available_models: set[str], policy: dict) -> dict:
    if complexity not in COMPLEXITIES:
        raise ValueError(f"unsupported complexity: {complexity}")
    routing = policy["model_routing"]
    key = {
        "complex": "complex_or_high_risk",
        "medium": "medium",
        "light": "lightweight_or_mechanical_or_read_only",
    }[complexity]
    requested = routing[key]
    requested_model = requested["model"]
    result = {
        "complexity": complexity,
        "requested_model": requested_model,
        "selected_model": requested_model,
        "reasoning_effort": requested["reasoning_effort"],
        "available_models": sorted(available_models),
        "audit_status": "ROUTED",
        "fallback": None,
    }
    if requested_model.lower() in available_models:
        return result
    if complexity != "light":
        raise RuntimeError(f"required model unavailable: {requested_model}")
    fallback = requested["unavailable_fallback"]
    if fallback["model"].lower() not in available_models:
        raise RuntimeError("Luna is unavailable and its approved Terra fallback is unavailable")
    result.update({
        "selected_model": fallback["model"],
        "reasoning_effort": fallback["reasoning_effort"],
        "audit_status": fallback["audit_status"],
        "fallback": {
            "from_model": requested_model,
            "to_model": fallback["model"],
            "reason": fallback["prohibition"],
        },
    })
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--complexity", required=True, choices=COMPLEXITIES)
    parser.add_argument("--available-model", action="append", default=[], help="Runtime-advertised model; repeatable")
    parser.add_argument("--json", action="store_true", help="Emit JSON (the default output is also JSON for audit safety)")
    args = parser.parse_args(argv)
    try:
        payload = select_profile(args.complexity, normalized_models(args.available_model), load_policy())
    except (OSError, ValueError, RuntimeError, KeyError) as error:
        print(json.dumps({"status": "BLOCKED", "error": str(error)}, ensure_ascii=False), file=sys.stderr)
        return 2
    print(json.dumps(payload, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
