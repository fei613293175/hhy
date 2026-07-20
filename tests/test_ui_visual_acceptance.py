#!/usr/bin/env python3
"""Regression tests for the exact-panel UI visual acceptance gate."""
from __future__ import annotations

from pathlib import Path
import csv
import json
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from check_ui_visual_acceptance import REQUIRED_COLUMNS, validate_release  # noqa: E402


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def fixture(root: Path, *, status: str = "PASS", coverage: str = "EXACT") -> None:
    write_csv(
        root / "catalogs/ui_page_specifications.csv",
        ["页面ID", "平台", "页面名称", "计划版本"],
        [{"页面ID": "SCR-TEST-001", "平台": "ANDROID", "页面名称": "测试页", "计划版本": "R99"}],
    )
    token = "design/tokens/hhy_design_tokens_v1.2.2.json"
    implementation = "apps/android/feature/test/TestScreen.kt"
    reference = "design/effect-previews/B12/HHY_B12_8PAGE_UI_REFERENCE.png"
    screenshot = "artifacts/validation/r99-ui/SCR-TEST-001.png"
    for relative in [token, implementation, reference, screenshot]:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"evidence")
    manifest = root / "design/effect-previews/B12/HHY_B12_MANIFEST.json"
    manifest.write_text(
        json.dumps({"panels": [{"panel": "P04", "name": "测试参考"}]}, ensure_ascii=False),
        encoding="utf-8",
    )
    write_csv(
        root / "catalogs/ui_visual_acceptance.csv",
        list(REQUIRED_COLUMNS),
        [{
            "页面ID": "SCR-TEST-001",
            "计划版本": "R99",
            "平台": "ANDROID",
            "页面名称": "测试页",
            "视觉来源": "B12/P04" if coverage != "MISSING" else "",
            "覆盖状态": coverage,
            "布局建模约束": "按P04还原区域顺序、层级和卡片布局",
            "业务过滤说明": "过滤示例数据，字段来自页面规格",
            "Token源": token,
            "实现路径": implementation,
            "参考证据": reference if coverage != "MISSING" else "",
            "实现截图证据": screenshot if status == "PASS" else "",
            "验收状态": status,
            "说明": "已逐项核对" if status == "PASS" else "等待补充视觉规格",
        }],
    )


class UiVisualAcceptanceTest(unittest.TestCase):
    def test_exact_panel_token_and_screenshot_pass(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root)
            errors, count = validate_release(root, "R99", require_pass=True)
            self.assertEqual(1, count)
            self.assertEqual([], errors)

    def test_panel_range_is_never_a_construction_contract(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root)
            path = root / "catalogs/ui_visual_acceptance.csv"
            with path.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            rows[0]["视觉来源"] = "B12/P01-P08"
            write_csv(path, list(REQUIRED_COLUMNS), rows)
            errors, _count = validate_release(root, "R99", require_pass=True)
            codes = {code for code, _message in errors}
            self.assertIn("UI_VISUAL_PANEL_RANGE_FORBIDDEN", codes)

    def test_blocked_visual_spec_is_truthful_but_blocks_release_close(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root, status="BLOCKED_VISUAL_SPEC", coverage="MISSING")
            catalog_errors, _count = validate_release(root, "R99", require_pass=False)
            self.assertEqual([], catalog_errors)
            close_errors, _count = validate_release(root, "R99", require_pass=True)
            codes = {code for code, _message in close_errors}
            self.assertIn("UI_VISUAL_COVERAGE_NOT_READY", codes)
            self.assertIn("UI_VISUAL_NOT_PASS", codes)
            self.assertIn("UI_VISUAL_REFERENCE_EVIDENCE_MISSING", codes)
            self.assertIn("UI_VISUAL_SCREENSHOT_EVIDENCE_MISSING", codes)

    def test_every_release_page_requires_one_visual_contract(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root)
            write_csv(root / "catalogs/ui_visual_acceptance.csv", list(REQUIRED_COLUMNS), [])
            errors, _count = validate_release(root, "R99", require_pass=True)
            self.assertIn("UI_VISUAL_CONTRACT_MISSING", {code for code, _message in errors})


if __name__ == "__main__":
    unittest.main()
