#!/usr/bin/env python3
"""Regression coverage for package/product baseline version separation."""
from __future__ import annotations

from pathlib import Path
import importlib.util
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "check_v122_documentation", ROOT / "scripts/check_v122_documentation.py"
)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def validate(baseline: dict) -> list[dict[str, str]]:
    errors: list[dict[str, str]] = []

    def require(condition: bool, code: str, message: str) -> None:
        if not condition:
            errors.append({"code": code, "message": message})

    MODULE.validate_project_baseline(baseline, require)
    return errors


class DocumentationBaselineVersionTest(unittest.TestCase):
    def test_v123_package_with_v122_product_baseline_is_valid(self) -> None:
        self.assertEqual(
            validate({"package_version": "1.2.3", "product_spec_baseline": "V1.2.2"}),
            [],
        )

    def test_wrong_product_baseline_is_rejected(self) -> None:
        errors = validate(
            {"package_version": "1.2.3", "product_spec_baseline": "V1.2.3"}
        )
        self.assertEqual([error["code"] for error in errors], ["BASELINE_PRODUCT_SPEC_VERSION"])

    def test_missing_package_version_is_rejected(self) -> None:
        errors = validate({"product_spec_baseline": "V1.2.2"})
        self.assertEqual([error["code"] for error in errors], ["BASELINE_PACKAGE_VERSION"])


if __name__ == "__main__":
    unittest.main()
