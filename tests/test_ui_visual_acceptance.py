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

from check_ui_visual_acceptance import (  # noqa: E402
    REQUIRED_COLUMNS,
    validate_historical,
    validate_release,
)


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
            "说明": (
                "已逐项核对；肉眼丰富度=PASS；信息层级=PASS；组件精致度=PASS；"
                "真实业务映射=PASS；状态完整性=PASS；AI对照结论=PASS"
                if status == "PASS" else "等待补充视觉规格"
            ),
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

    def test_catalog_only_allows_planned_implementation_path(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root, status="IN_REVIEW")
            implementation = root / "apps/android/feature/test/TestScreen.kt"
            implementation.unlink()

            catalog_errors, _count = validate_release(root, "R99", require_pass=False)
            self.assertEqual([], catalog_errors)

            close_errors, _count = validate_release(root, "R99", require_pass=True)
            self.assertIn(
                "UI_VISUAL_IMPLEMENTATION_MISSING",
                {code for code, _message in close_errors},
            )

    def test_every_release_page_requires_one_visual_contract(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root)
            write_csv(root / "catalogs/ui_visual_acceptance.csv", list(REQUIRED_COLUMNS), [])
            errors, _count = validate_release(root, "R99", require_pass=True)
            self.assertIn("UI_VISUAL_CONTRACT_MISSING", {code for code, _message in errors})

    def test_admin_standard_template_is_a_valid_visual_contract(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root)
            page_path = root / "catalogs/ui_page_specifications.csv"
            with page_path.open(encoding="utf-8-sig", newline="") as handle:
                pages = list(csv.DictReader(handle))
            pages[0]["平台"] = "ADMIN"
            write_csv(page_path, ["页面ID", "平台", "页面名称", "计划版本"], pages)
            contract_path = root / "catalogs/ui_visual_acceptance.csv"
            with contract_path.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            source = "docs/02-ui/管理后台页面与运营操作完整规格_V1.2.2.md"
            (root / source).parent.mkdir(parents=True, exist_ok=True)
            (root / source).write_text("ADM标准模板", encoding="utf-8")
            rows[0]["平台"] = "ADMIN"
            rows[0]["覆盖状态"] = "STANDARD_TEMPLATE"
            rows[0]["视觉来源"] = f"SPEC:{source}"
            rows[0]["参考证据"] = source
            write_csv(contract_path, list(REQUIRED_COLUMNS), rows)
            errors, count = validate_release(root, "R99", require_pass=True)
            self.assertEqual(1, count)
            self.assertEqual([], errors)

    def test_standard_template_is_rejected_for_android(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root)
            path = root / "catalogs/ui_visual_acceptance.csv"
            with path.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            source = "docs/02-ui/管理后台页面与运营操作完整规格_V1.2.2.md"
            (root / source).parent.mkdir(parents=True, exist_ok=True)
            (root / source).write_text("ADM标准模板", encoding="utf-8")
            rows[0]["覆盖状态"] = "STANDARD_TEMPLATE"
            rows[0]["视觉来源"] = f"SPEC:{source}"
            rows[0]["参考证据"] = source
            write_csv(path, list(REQUIRED_COLUMNS), rows)
            errors, _count = validate_release(root, "R99", require_pass=True)
            self.assertIn("UI_VISUAL_STANDARD_TEMPLATE_PLATFORM_INVALID", {code for code, _message in errors})

    def test_token_and_screenshot_only_cannot_pass_effect_level_review(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root)
            path = root / "catalogs/ui_visual_acceptance.csv"
            with path.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            rows[0]["说明"] = "Token、截图和功能旅程均通过"
            write_csv(path, list(REQUIRED_COLUMNS), rows)
            errors, _count = validate_release(root, "R99", require_pass=True)
            self.assertIn("UI_VISUAL_EFFECT_LEVEL_REVIEW_NOT_PASS", {code for code, _message in errors})

    def test_r08_close_and_historical_visual_reaudit_are_separate_gates(self) -> None:
        with tempfile.TemporaryDirectory(prefix="hhy-ui-visual-") as temp:
            root = Path(temp)
            fixture(root)
            page_path = root / "catalogs/ui_page_specifications.csv"
            with page_path.open(encoding="utf-8-sig", newline="") as handle:
                pages = list(csv.DictReader(handle))
            pages.extend([
                {"页面ID": "SCR-OLD-001", "平台": "ANDROID", "页面名称": "历史页", "计划版本": "R02"},
                {"页面ID": "SCR-NEW-001", "平台": "ANDROID", "页面名称": "当前页", "计划版本": "R08"},
            ])
            write_csv(page_path, ["页面ID", "平台", "页面名称", "计划版本"], pages)
            visual_path = root / "catalogs/ui_visual_acceptance.csv"
            with visual_path.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            current = dict(rows[0])
            current["页面ID"] = "SCR-NEW-001"
            current["计划版本"] = "R08"
            current["页面名称"] = "当前页"
            rows.append(current)
            write_csv(visual_path, list(REQUIRED_COLUMNS), rows)
            errors, _count = validate_release(root, "R08", require_pass=True)
            self.assertEqual([], errors)

            errors, count = validate_historical(root, "R08")
            self.assertGreaterEqual(count, 2)
            self.assertIn("UI_VISUAL_CONTRACT_MISSING", {code for code, _message in errors})


if __name__ == "__main__":
    unittest.main()
