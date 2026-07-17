#!/usr/bin/env python3
"""Validate the generated configuration registry without truthy-string bugs."""

from __future__ import annotations

from pathlib import Path
from typing import Any, Iterable, Mapping
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
ALLOWED_SECRET_DEFAULTS = {"", None, "PROTECTED_CI_SECRET", "GENERATE_AT_P00"}


def boolean(value: Any, *, field: str, key: str) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"true", "yes", "1"}:
            return True
        if normalized in {"false", "no", "0", ""}:
            return False
    raise ValueError(f"CONFIG_BOOLEAN_INVALID {key} {field}={value!r}")


def validate(items: Iterable[Mapping[str, Any]]) -> list[str]:
    rows = list(items)
    keys = [str(item.get("key", "")).strip() for item in rows]
    errors: list[str] = []
    if any(not key for key in keys):
        errors.append("EMPTY_CONFIG_KEY")
    if len(keys) != len(set(keys)):
        errors.append("DUP_CONFIG_KEYS")
    for item, key in zip(rows, keys):
        try:
            secret = boolean(item.get("secret", False), field="secret", key=key)
        except ValueError as error:
            errors.append(str(error))
            continue
        if secret and item.get("default") not in ALLOWED_SECRET_DEFAULTS:
            errors.append(f"SECRET_DEFAULT_FORBIDDEN {key}")
    return errors


def main() -> int:
    document = yaml.safe_load((ROOT / "config/CONFIG_REGISTRY.yaml").read_text(encoding="utf-8"))
    items = document.get("items") if isinstance(document, dict) else None
    if not isinstance(items, list):
        print("CONFIG_ITEMS_INVALID")
        return 1
    errors = validate(items)
    if errors:
        print("\n".join(errors))
        return 1
    print("CONFIG_OK", len(items))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
