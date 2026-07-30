#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "check_config_registry", ROOT / "scripts/check_config_registry.py"
)
assert SPEC and SPEC.loader
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class ConfigRegistryCheckerTest(unittest.TestCase):
    def test_false_string_is_not_treated_as_secret(self) -> None:
        self.assertEqual(
            [],
            CHECKER.validate(
                [{"key": "platform.brand.name", "secret": "False", "default": "合伙云 Pro"}]
            ),
        )

    def test_true_string_rejects_plaintext_default(self) -> None:
        self.assertEqual(
            ["SECRET_DEFAULT_FORBIDDEN storage.secret"],
            CHECKER.validate(
                [{"key": "storage.secret", "secret": "True", "default": "plaintext"}]
            ),
        )

    def test_native_boolean_and_protected_default_pass(self) -> None:
        self.assertEqual(
            [],
            CHECKER.validate(
                [{"key": "storage.secret", "secret": True, "default": "PROTECTED_CI_SECRET"}]
            ),
        )

    def test_invalid_boolean_is_rejected(self) -> None:
        errors = CHECKER.validate(
            [{"key": "storage.secret", "secret": "sometimes", "default": None}]
        )
        self.assertEqual(1, len(errors))
        self.assertTrue(errors[0].startswith("CONFIG_BOOLEAN_INVALID storage.secret secret="))

    def test_duplicate_and_empty_keys_are_rejected(self) -> None:
        errors = CHECKER.validate(
            [
                {"key": "", "secret": False, "default": None},
                {"key": "same", "secret": False, "default": None},
                {"key": "same", "secret": False, "default": None},
            ]
        )
        self.assertEqual(["EMPTY_CONFIG_KEY", "DUP_CONFIG_KEYS"], errors)


if __name__ == "__main__":
    unittest.main()
