#!/usr/bin/env python3
"""Enforce exact visual-model contracts before a release may close."""
from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import csv
import json
import re
import sys
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
PAGE_CATALOG = Path("catalogs/ui_page_specifications.csv")
VISUAL_CATALOG = Path("catalogs/ui_visual_acceptance.csv")
VISUAL_PLATFORMS = {"ANDROID", "H5", "ADMIN"}
ALLOWED_COVERAGE = {"EXACT", "APPROVED_SUPPLEMENT", "STANDARD_TEMPLATE", "PARTIAL", "MISSING"}
PASS_COVERAGE = {"EXACT", "APPROVED_SUPPLEMENT", "STANDARD_TEMPLATE"}
ALLOWED_STATUS = {
    "PASS",
    "IN_REVIEW",
    "BLOCKED_REDESIGN",
    "BLOCKED_VISUAL_SPEC",
}
PANEL = re.compile(r"^(B\d{2})/(P\d{2})$")
PANEL_RANGE = re.compile(r"P\d{2}\s*[-–—]\s*P\d{2}", re.IGNORECASE)
QUALITY_MARKERS = (
    "肉眼丰富度=PASS",
    "信息层级=PASS",
    "组件精致度=PASS",
    "真实业务映射=PASS",
    "状态完整性=PASS",
    "AI对照结论=PASS",
)
RELEASE_ID = re.compile(r"^(P|R)(\d{2})$")

REQUIRED_COLUMNS = (
    "页面ID",
    "计划版本",
    "平台",
    "页面名称",
    "视觉来源",
    "覆盖状态",
    "布局建模约束",
    "业务过滤说明",
    "Token源",
    "实现路径",
    "参考证据",
    "实现截图证据",
    "验收状态",
    "说明",
)


def _read_csv(root: Path, relative: Path) -> tuple[list[dict[str, str]], list[str]]:
    path = root / relative
    if not path.is_file():
        return [], [f"UI_VISUAL_CATALOG_MISSING {relative.as_posix()}"]
    try:
        with path.open(encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames is None:
                return [], [f"UI_VISUAL_CATALOG_HEADER_MISSING {relative.as_posix()}"]
            rows = list(reader)
            return rows, []
    except (OSError, UnicodeDecodeError, csv.Error) as exc:
        return [], [f"UI_VISUAL_CATALOG_INVALID {relative.as_posix()} {exc}"]


def _paths(value: str) -> Iterable[str]:
    return (item.strip() for item in value.split(";") if item.strip())


def _inside_file(root: Path, value: str) -> bool:
    path = (root / value).resolve()
    try:
        path.relative_to(root.resolve())
    except ValueError:
        return False
    return path.is_file()


def _validate_panel(root: Path, screen_id: str, source: str) -> list[str]:
    errors: list[str] = []
    if PANEL_RANGE.search(source):
        return [f"UI_VISUAL_PANEL_RANGE_FORBIDDEN {screen_id} {source}"]
    match = PANEL.fullmatch(source)
    if match:
        batch, panel = match.groups()
        directory = root / "design" / "effect-previews" / batch
        manifest_path = directory / f"HHY_{batch}_MANIFEST.json"
        image_path = directory / f"HHY_{batch}_8PAGE_UI_REFERENCE.png"
        if not manifest_path.is_file() or not image_path.is_file():
            errors.append(f"UI_VISUAL_PANEL_EVIDENCE_MISSING {screen_id} {source}")
            return errors
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            panels = {str(item.get("panel") or "") for item in manifest.get("panels", [])}
            if panel not in panels:
                errors.append(f"UI_VISUAL_PANEL_UNKNOWN {screen_id} {source}")
        except (OSError, UnicodeDecodeError, json.JSONDecodeError, AttributeError):
            errors.append(f"UI_VISUAL_PANEL_MANIFEST_INVALID {screen_id} {source}")
        return errors
    if source.startswith("SPEC:"):
        relative = source.removeprefix("SPEC:").strip()
        if not relative or not _inside_file(root, relative):
            errors.append(f"UI_VISUAL_SUPPLEMENT_MISSING {screen_id} {relative or 'EMPTY'}")
        return errors
    errors.append(f"UI_VISUAL_SOURCE_NOT_EXACT {screen_id} {source or 'EMPTY'}")
    return errors


def _validate_quality_review(screen_id: str, note: str) -> list[tuple[str, str]]:
    missing = [marker for marker in QUALITY_MARKERS if marker not in note]
    if not missing:
        return []
    return [(
        "UI_VISUAL_EFFECT_LEVEL_REVIEW_NOT_PASS",
        f"{screen_id} 未通过效果图级肉眼质量复核：{','.join(missing)}",
    )]


def validate_release(
    root: Path,
    release: str,
    *,
    require_pass: bool = True,
) -> tuple[list[tuple[str, str]], int]:
    errors: list[tuple[str, str]] = []
    page_rows, page_errors = _read_csv(root, PAGE_CATALOG)
    visual_rows, visual_errors = _read_csv(root, VISUAL_CATALOG)
    errors.extend((line.split(" ", 1)[0], line) for line in page_errors + visual_errors)
    if errors:
        return errors, 0

    with (root / VISUAL_CATALOG).open(encoding="utf-8-sig", newline="") as handle:
        visual_columns = set(csv.DictReader(handle).fieldnames or [])
    missing_columns = [name for name in REQUIRED_COLUMNS if name not in visual_columns]
    if missing_columns:
        errors.append(("UI_VISUAL_COLUMNS_MISSING", f"缺少列：{missing_columns}"))
        return errors, 0

    expected = {
        row.get("页面ID", "").strip(): row
        for row in page_rows
        if row.get("计划版本", "").strip() == release
        and row.get("平台", "").strip().upper() in VISUAL_PLATFORMS
    }
    release_rows = [row for row in visual_rows if row.get("计划版本", "").strip() == release]
    by_screen: dict[str, dict[str, str]] = {}
    duplicates: set[str] = set()
    for row in release_rows:
        screen_id = row.get("页面ID", "").strip()
        if screen_id in by_screen:
            duplicates.add(screen_id)
        by_screen[screen_id] = row
    for screen_id in sorted(duplicates):
        errors.append(("UI_VISUAL_DUPLICATE", f"{screen_id} 存在重复视觉合同"))

    for screen_id in sorted(set(expected) - set(by_screen)):
        errors.append(("UI_VISUAL_CONTRACT_MISSING", f"{screen_id} 缺少逐页视觉合同"))
    for screen_id in sorted(set(by_screen) - set(expected)):
        errors.append(("UI_VISUAL_CONTRACT_ORPHAN", f"{screen_id} 不属于 {release} 页面目录"))

    for screen_id in sorted(set(expected) & set(by_screen)):
        page = expected[screen_id]
        row = by_screen[screen_id]
        platform = row.get("平台", "").strip().upper()
        coverage = row.get("覆盖状态", "").strip().upper()
        status = row.get("验收状态", "").strip().upper()
        source = row.get("视觉来源", "").strip()
        if platform != page.get("平台", "").strip().upper():
            errors.append(("UI_VISUAL_PLATFORM_MISMATCH", f"{screen_id} 平台不一致"))
        if coverage not in ALLOWED_COVERAGE:
            errors.append(("UI_VISUAL_COVERAGE_INVALID", f"{screen_id} 覆盖状态非法：{coverage or 'EMPTY'}"))
        if status not in ALLOWED_STATUS:
            errors.append(("UI_VISUAL_STATUS_INVALID", f"{screen_id} 验收状态非法：{status or 'EMPTY'}"))
        for field in ("布局建模约束", "业务过滤说明", "Token源", "实现路径"):
            if not row.get(field, "").strip():
                errors.append(("UI_VISUAL_FIELD_EMPTY", f"{screen_id} {field} 为空"))

        token_source = row.get("Token源", "").strip()
        if token_source != "design/tokens/hhy_design_tokens_v1.2.2.json":
            errors.append(("UI_VISUAL_TOKEN_SOURCE_INVALID", f"{screen_id} Token源非法：{token_source or 'EMPTY'}"))
        elif not _inside_file(root, token_source):
            errors.append(("UI_VISUAL_TOKEN_SOURCE_MISSING", f"{screen_id} Token文件不存在"))

        for relative in _paths(row.get("实现路径", "")):
            if not _inside_file(root, relative):
                errors.append(("UI_VISUAL_IMPLEMENTATION_MISSING", f"{screen_id} 实现不存在：{relative}"))

        if source:
            for item in _paths(source):
                for line in _validate_panel(root, screen_id, item):
                    errors.append((line.split(" ", 1)[0], line))
        elif coverage != "MISSING":
            errors.append(("UI_VISUAL_SOURCE_EMPTY", f"{screen_id} 覆盖状态 {coverage} 但视觉来源为空"))

        if coverage == "APPROVED_SUPPLEMENT" and not all(
            item.startswith("SPEC:") for item in _paths(source)
        ):
            errors.append(("UI_VISUAL_SUPPLEMENT_REQUIRED", f"{screen_id} 必须绑定批准的补充视觉规格"))
        if coverage == "STANDARD_TEMPLATE" and platform != "ADMIN":
            errors.append(("UI_VISUAL_STANDARD_TEMPLATE_PLATFORM_INVALID", f"{screen_id} 标准模板合同仅适用于ADMIN页面"))
        if coverage == "STANDARD_TEMPLATE" and not all(
            item.startswith("SPEC:docs/02-ui/") for item in _paths(source)
        ):
            errors.append(("UI_VISUAL_STANDARD_TEMPLATE_SOURCE_INVALID", f"{screen_id} 必须绑定原始管理端页面与运营规格"))

        if status != "PASS" and not row.get("说明", "").strip():
            errors.append(("UI_VISUAL_BLOCK_REASON_MISSING", f"{screen_id} 非PASS状态缺少说明"))

        if status == "PASS" or require_pass:
            if coverage not in PASS_COVERAGE:
                errors.append(("UI_VISUAL_COVERAGE_NOT_READY", f"{screen_id} 覆盖状态为 {coverage or 'EMPTY'}"))
            if status != "PASS":
                errors.append(("UI_VISUAL_NOT_PASS", f"{screen_id} 验收状态为 {status or 'EMPTY'}"))
            for field, code in (
                ("参考证据", "UI_VISUAL_REFERENCE_EVIDENCE_MISSING"),
                ("实现截图证据", "UI_VISUAL_SCREENSHOT_EVIDENCE_MISSING"),
            ):
                values = list(_paths(row.get(field, "")))
                if not values:
                    errors.append((code, f"{screen_id} {field}为空"))
                for relative in values:
                    if not _inside_file(root, relative):
                        errors.append((code, f"{screen_id} 证据不存在：{relative}"))
        if require_pass:
            errors.extend(_validate_quality_review(screen_id, row.get("说明", "").strip()))

    return errors, len(expected)


def _release_key(release: str) -> tuple[int, int] | None:
    match = RELEASE_ID.fullmatch(release.strip().upper())
    if not match:
        return None
    prefix, number = match.groups()
    return (0 if prefix == "P" else 1, int(number))


def validate_historical(
    root: Path,
    through_release: str,
) -> tuple[list[tuple[str, str]], int]:
    """Validate every catalogued frontend release through the requested boundary.

    This deliberately reuses ``validate_release`` and the same CSV facts. It is
    an explicit global-remediation gate, not an implicit prerequisite of one
    historical release close.
    """
    target_key = _release_key(through_release)
    if target_key is None:
        return [("UI_VISUAL_HISTORICAL_RELEASE_INVALID", through_release)], 0

    page_rows, page_errors = _read_csv(root, PAGE_CATALOG)
    if page_errors:
        return [(line.split(" ", 1)[0], line) for line in page_errors], 0
    releases: set[str] = set()
    for row in page_rows:
        release = row.get("计划版本", "").strip().upper()
        key = _release_key(release)
        if (
            key is not None
            and key <= target_key
            and row.get("平台", "").strip().upper() in VISUAL_PLATFORMS
        ):
            releases.add(release)

    errors: list[tuple[str, str]] = []
    page_count = 0
    for release in sorted(releases, key=lambda value: _release_key(value) or (99, 99)):
        release_errors, count = validate_release(root, release, require_pass=True)
        errors.extend(release_errors)
        page_count += count
    return errors, page_count


def main(argv: list[str] | None = None) -> int:
    parser = ArgumentParser(description=__doc__)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--release")
    target.add_argument(
        "--historical-through",
        help="全局UI整改门禁：验证P00/R01起至目标版本的全部前端页面",
    )
    parser.add_argument(
        "--catalog-only",
        action="store_true",
        help="仅验证合同覆盖、精确来源和阻断原因；不要求全部页面PASS",
    )
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args(argv)
    if args.historical_through:
        if args.catalog_only:
            parser.error("--historical-through 不允许与 --catalog-only 同时使用")
        label_target = args.historical_through
        errors, count = validate_historical(args.root.resolve(), args.historical_through)
    else:
        label_target = args.release
        errors, count = validate_release(
            args.root.resolve(), args.release, require_pass=not args.catalog_only
        )
    if errors:
        print("UI_VISUAL_ACCEPTANCE_FAILED", label_target, len(errors))
        for code, message in errors:
            print(code, message)
        return 1
    if args.historical_through:
        label = "UI_VISUAL_HISTORICAL_ACCEPTANCE_OK"
    else:
        label = "UI_VISUAL_CATALOG_OK" if args.catalog_only else "UI_VISUAL_ACCEPTANCE_OK"
    print(label, label_target, f"pages={count}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
