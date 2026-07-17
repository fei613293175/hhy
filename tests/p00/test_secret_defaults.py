#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[2]


def boolean(value: object, *, key: str) -> bool:
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise AssertionError(f"{key}: secret must be a boolean or the exact string True/False, got {value!r}")


class SecretDefaultPolicyTest(unittest.TestCase):
    def test_secret_registry_uses_references_and_has_no_plaintext_defaults(self) -> None:
        document = yaml.safe_load(
            (ROOT / "config/CONFIG_REGISTRY.yaml").read_text(encoding="utf-8")
        ) or {}
        items = document.get("items")
        self.assertIsInstance(items, list)
        self.assertTrue(items)
        keys: set[str] = set()
        secret_count = 0
        for item in items:
            self.assertIsInstance(item, dict)
            key = item.get("key")
            self.assertIsInstance(key, str)
            self.assertTrue(key)
            self.assertNotIn(key, keys, f"duplicate configuration key: {key}")
            keys.add(key)
            is_secret = boolean(item.get("secret"), key=key)
            if is_secret:
                secret_count += 1
                self.assertIn(item.get("type"), {"secret_ref", "certificate_ref"}, key)
                self.assertIn(item.get("default"), {"", None}, key)
                self.assertEqual(item.get("secret_ref_policy"), "VAULT_OR_KMS_REFERENCE_ONLY", key)
                self.assertEqual(item.get("control_type"), "SECRET_REF_PICKER", key)
                self.assertEqual(item.get("lifecycle"), "SECRET_REFERENCE", key)
                self.assertEqual(item.get("approval"), "TWO_PERSON", key)
                self.assertEqual(item.get("change_risk"), "CRITICAL", key)
            else:
                self.assertNotIn(item.get("type"), {"secret_ref", "certificate_ref"}, key)
                self.assertEqual(item.get("secret_ref_policy"), "NOT_SECRET", key)
        self.assertGreater(secret_count, 0, "registry must contain and validate actual secret references")


if __name__ == "__main__":
    unittest.main()
