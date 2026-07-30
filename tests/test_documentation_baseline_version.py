#!/usr/bin/env python3
"""Regression coverage for package/product baseline version separation."""
from __future__ import annotations

from pathlib import Path
import importlib.util
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "check_v122_documentation", ROOT / "scripts/check_v122_documentation.py"
)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

V123_SPEC = importlib.util.spec_from_file_location(
    "check_v123_documentation", ROOT / "scripts/check_v123_documentation.py"
)
assert V123_SPEC and V123_SPEC.loader
V123_MODULE = importlib.util.module_from_spec(V123_SPEC)
V123_SPEC.loader.exec_module(V123_MODULE)


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

    def test_main_document_pointer_resolves_in_both_document_gates(self) -> None:
        for module in (MODULE, V123_MODULE):
            with self.subTest(module=module.__name__), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                target = root / module.MAIN_DOCUMENT
                pointer = root / module.MAIN_DOCUMENT_POINTER
                target.write_text("# canonical\n", encoding="utf-8")
                pointer.parent.mkdir(parents=True)
                pointer.write_text(
                    f"请读取仓库根目录 `{module.MAIN_DOCUMENT}`。\n", encoding="utf-8"
                )
                errors: list[dict[str, str]] = []
                module.validate_main_document_pointer(
                    root,
                    lambda condition, code, message: errors.append(
                        {"code": code, "message": message}
                    )
                    if not condition
                    else None,
                )
                self.assertEqual(errors, [])

    def test_main_document_pointer_rejects_stale_target(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / MODULE.MAIN_DOCUMENT
            pointer = root / MODULE.MAIN_DOCUMENT_POINTER
            target.write_text("# canonical\n", encoding="utf-8")
            pointer.parent.mkdir(parents=True)
            pointer.write_text("请读取仓库根目录 `旧主开发文档.md`。\n", encoding="utf-8")
            errors: list[dict[str, str]] = []
            MODULE.validate_main_document_pointer(
                root,
                lambda condition, code, message: errors.append(
                    {"code": code, "message": message}
                )
                if not condition
                else None,
            )
            self.assertEqual([error["code"] for error in errors], ["MAIN_DOC_POINTER_DRIFT"])


if __name__ == "__main__":
    unittest.main()
