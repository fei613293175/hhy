#!/usr/bin/env python3
"""Validate the deterministic design-token derivatives used by every client."""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
TOKEN_RELATIVE_PATH = Path("design/tokens/hhy_design_tokens_v1.2.2.json")
ANDROID_ASSET_RELATIVE_PATH = Path(
    "apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json"
)
KOTLIN_RELATIVE_PATH = Path(
    "apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyTokens.kt"
)
REQUIRED_TOKEN_GROUPS = (
    "color",
    "spacingDp",
    "radiusDp",
    "borderWidthDp",
    "elevationDp",
    "shadow",
    "opacity",
    "motionMs",
    "componentDp",
    "imageRatio",
    "breakpointsPx",
    "adminWeb",
)
RAW_HEX = re.compile(r"#[0-9A-Fa-f]{3,8}\b")
INLINE_STYLE = re.compile(r"(?:\s|:)style\s*=", re.IGNORECASE)
RAW_COMPOSE_VALUE = re.compile(
    r"\b\d+(?:\.\d+)?\.(?:dp|sp)\b|Color\(0x[0-9A-Fa-f]+\)|Color\.[A-Z]\w*"
)
CSS_DECLARATION = re.compile(r"(?m)^\s*(--[\w-]+)\s*:\s*([^;]+);\s*$")


def _camel_to_kebab(value: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "-", value).lower()


def _flatten_colors(colors: dict[str, Any]) -> Iterable[tuple[str, str]]:
    for group, values in colors.items():
        if not isinstance(values, dict):
            continue
        for name, value in values.items():
            yield f"--hhy-color-{group}-{name}", str(value)


def expected_css_declarations(
    tokens: dict[str, Any], *, include_admin: bool
) -> list[tuple[str, str]]:
    declarations = list(_flatten_colors(tokens.get("color", {})))
    declarations.extend(
        (f"--hhy-space-{value}", f"{value}px")
        for value in tokens.get("spacingDp", [])
    )
    declarations.extend(
        (f"--hhy-radius-{name}", f"{value}px")
        for name, value in tokens.get("radiusDp", {}).items()
    )
    declarations.extend(
        (f"--hhy-motion-{name}", f"{value}ms")
        for name, value in tokens.get("motionMs", {}).items()
    )
    if include_admin:
        declarations.extend(
            (f"--hhy-admin-{_camel_to_kebab(name)}", f"{value}px")
            for name, value in tokens.get("adminWeb", {}).items()
        )
    return declarations


def render_css(tokens: dict[str, Any], *, include_admin: bool) -> str:
    lines = [":root {"]
    lines.extend(
        f"  {name}: {value};"
        for name, value in expected_css_declarations(tokens, include_admin=include_admin)
    )
    lines.append("}")
    return "\n".join(lines) + "\n"


def _kotlin_color(value: str) -> str:
    normalized = value.removeprefix("#").upper()
    if len(normalized) == 6:
        normalized = "FF" + normalized
    return f"Color(0x{normalized})"


def render_kotlin_tokens(tokens: dict[str, Any]) -> str:
    colors = tokens["color"]
    radius = tokens["radiusDp"]
    typography = tokens["typographySp"]
    size = tokens["sizeDp"]
    component = tokens["componentDp"]
    border = tokens["borderWidthDp"]
    return f"""package cc.orbexa.hhy.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object HhyColors {{
    val BrandPrimary = {_kotlin_color(colors['brand']['primary'])}
    val BrandPrimaryDark = {_kotlin_color(colors['brand']['primaryDark'])}
    val BrandSecondary = {_kotlin_color(colors['brand']['secondary'])}
    val BrandTertiary = {_kotlin_color(colors['brand']['tertiary'])}
    val RewardRed = {_kotlin_color(colors['reward']['red'])}
    val RewardOrange = {_kotlin_color(colors['reward']['orange'])}
    val RewardGold = {_kotlin_color(colors['reward']['gold'])}
    val PageBackground = {_kotlin_color(colors['background']['page'])}
    val Surface = {_kotlin_color(colors['background']['surface'])}
    val SoftBlue = {_kotlin_color(colors['background']['softBlue'])}
    val TextPrimary = {_kotlin_color(colors['text']['primary'])}
    val TextSecondary = {_kotlin_color(colors['text']['secondary'])}
    val TextTertiary = {_kotlin_color(colors['text']['tertiary'])}
    val TextInverse = {_kotlin_color(colors['text']['inverse'])}
    val Border = {_kotlin_color(colors['border']['default'])}
    val Success = {_kotlin_color(colors['status']['success'])}
    val Warning = {_kotlin_color(colors['status']['warning'])}
    val Error = {_kotlin_color(colors['status']['error'])}
}}

object HhySpacing {{
    val Xs = {tokens['spacingDp'][0]}.dp
    val Sm = {tokens['spacingDp'][1]}.dp
    val Md = {tokens['spacingDp'][2]}.dp
    val Lg = {tokens['spacingDp'][3]}.dp
    val Xl = {tokens['spacingDp'][4]}.dp
    val Xxl = {tokens['spacingDp'][5]}.dp
    val Xxxl = {tokens['spacingDp'][6]}.dp
}}

object HhyRadius {{
    val LargeCard = {radius['largeCard']}.dp
    val NormalCard = {radius['normalCard']}.dp
    val Button = {radius['button']}.dp
    val Input = {radius['input']}.dp
    val Tag = {radius['tag']}.dp
    val Dialog = {radius['dialog']}.dp
    val BottomSheetTop = {radius['bottomSheetTop']}.dp
    val Pill = {radius['pill']}.dp
}}

/** Frozen Android type scale from hhy_design_tokens_v1.2.2.json. */
object HhyType {{
    val PageTitleSize = {typography['pageTitle']['size']}.sp
    val PageTitleLineHeight = {typography['pageTitle']['lineHeight']}.sp
    val SectionTitleSize = {typography['sectionTitle']['size']}.sp
    val SectionTitleLineHeight = {typography['sectionTitle']['lineHeight']}.sp
    val CardTitleSize = {typography['cardTitle']['size']}.sp
    val CardTitleLineHeight = {typography['cardTitle']['lineHeight']}.sp
    val ButtonSize = {typography['button']['size']}.sp
    val ButtonLineHeight = {typography['button']['lineHeight']}.sp
    val BodySize = {typography['body']['size']}.sp
    val BodyLineHeight = {typography['body']['lineHeight']}.sp
    val SecondaryBodySize = {typography['secondaryBody']['size']}.sp
    val SecondaryBodyLineHeight = {typography['secondaryBody']['lineHeight']}.sp
    val CaptionSize = {typography['caption']['size']}.sp
    val CaptionLineHeight = {typography['caption']['lineHeight']}.sp
    val NavigationSize = {typography['navigation']['size']}.sp
    val NavigationLineHeight = {typography['navigation']['lineHeight']}.sp
}}

/** Frozen component dimensions used by the B01 mobile authentication pages. */
object HhySize {{
    val Hairline = {border['standard']}.dp
    val TopAppBarHeight = {size['topAppBarContentHeight']}.dp
    val PrimaryButtonHeight = {size['primaryButtonHeight']}.dp
    val InputHeight = {size['singleLineInputHeight']}.dp
    val MinimumTouchTarget = {size['minimumTouchTarget']}.dp
    val AppLogo = {size['appLogo']}.dp
    val TabHeight = {component['tabHeight']}.dp
}}
"""


def validate_token_schema(tokens: dict[str, Any]) -> list[str]:
    errors = [f"TOKEN_MISSING {name}" for name in REQUIRED_TOKEN_GROUPS if name not in tokens]
    spacing = tokens.get("spacingDp")
    if isinstance(spacing, list) and (len(spacing) != len(set(spacing)) or spacing != sorted(spacing)):
        errors.append("TOKEN_SPACING_NOT_STRICTLY_ORDERED")
    return errors


def validate_css_text(
    relative_path: str, text: str, tokens: dict[str, Any], *, include_admin: bool
) -> list[str]:
    errors: list[str] = []
    declarations: dict[str, str] = {}
    duplicates: set[str] = set()
    for name, value in CSS_DECLARATION.findall(text):
        if name in declarations:
            duplicates.add(name)
        declarations[name] = value.strip()
    for name in sorted(duplicates):
        errors.append(f"CSS_DUPLICATE_TOKEN {relative_path} {name}")
    for name, expected in expected_css_declarations(tokens, include_admin=include_admin):
        actual = declarations.get(name)
        if actual is None:
            errors.append(f"CSS_TOKEN_MISSING {relative_path} {name}")
        elif actual != expected:
            errors.append(
                f"CSS_VALUE_DRIFT {relative_path} {name} expected={expected} actual={actual}"
            )
    if text.replace("\r\n", "\n") != render_css(tokens, include_admin=include_admin):
        errors.append(f"DERIVED_NOT_IDEMPOTENT {relative_path}")
    return errors


def validate_page_source_text(relative_path: str, text: str) -> list[str]:
    errors: list[str] = []
    if RAW_HEX.search(text):
        errors.append(f"RAW_HEX_FORBIDDEN {relative_path}")
    suffix = Path(relative_path).suffix.lower()
    if suffix in {".vue", ".html"} and INLINE_STYLE.search(text):
        errors.append(f"INLINE_STYLE_FORBIDDEN {relative_path}")
    if suffix == ".kt" and RAW_COMPOSE_VALUE.search(text):
        errors.append(f"RAW_COMPOSE_VALUE_FORBIDDEN {relative_path}")
    return errors


def validate(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    token_path = root / TOKEN_RELATIVE_PATH
    try:
        token_bytes = token_path.read_bytes()
        tokens = json.loads(token_bytes.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        return [f"TOKEN_SOURCE_INVALID {TOKEN_RELATIVE_PATH.as_posix()} {exc}"]

    errors.extend(validate_token_schema(tokens))
    if errors:
        return errors

    asset_path = root / ANDROID_ASSET_RELATIVE_PATH
    try:
        if asset_path.read_bytes() != token_bytes:
            errors.append("ANDROID_TOKEN_ASSET_BYTE_DRIFT")
    except OSError:
        errors.append(f"ANDROID_TOKEN_ASSET_MISSING {ANDROID_ASSET_RELATIVE_PATH.as_posix()}")

    for relative_path, include_admin in (
        (Path("packages/design-tokens/h5.css"), False),
        (Path("packages/design-tokens/admin.css"), True),
    ):
        try:
            text = (root / relative_path).read_text(encoding="utf-8")
        except OSError:
            errors.append(f"CSS_DERIVED_MISSING {relative_path.as_posix()}")
            continue
        errors.extend(
            validate_css_text(relative_path.as_posix(), text, tokens, include_admin=include_admin)
        )

    kotlin_path = root / KOTLIN_RELATIVE_PATH
    try:
        kotlin_text = kotlin_path.read_text(encoding="utf-8").replace("\r\n", "\n")
        if kotlin_text != render_kotlin_tokens(tokens):
            errors.append(f"DERIVED_NOT_IDEMPOTENT {KOTLIN_RELATIVE_PATH.as_posix()}")
    except OSError:
        errors.append(f"KOTLIN_DERIVED_MISSING {KOTLIN_RELATIVE_PATH.as_posix()}")

    source_patterns = (
        (Path("apps/h5/src"), {".css", ".html", ".ts", ".vue"}),
        (Path("apps/admin-web/src"), {".css", ".html", ".ts", ".vue"}),
        (Path("apps/android/core/designsystem/src/main/java"), {".kt"}),
        (Path("apps/android/feature"), {".kt"}),
    )
    for source_root, suffixes in source_patterns:
        directory = root / source_root
        if not directory.exists():
            continue
        for path in sorted(p for p in directory.rglob("*") if p.suffix.lower() in suffixes):
            relative_path = path.relative_to(root).as_posix()
            if Path(relative_path) == KOTLIN_RELATIVE_PATH:
                continue
            errors.extend(
                validate_page_source_text(relative_path, path.read_text(encoding="utf-8"))
            )

    catalog_path = root / "design/component-catalog.csv"
    try:
        with catalog_path.open(encoding="utf-8-sig", newline="") as handle:
            component_count = len(list(csv.DictReader(handle)))
        if component_count < 30:
            errors.append(f"COMPONENT_CATALOG_TOO_SMALL actual={component_count} minimum=30")
    except OSError:
        errors.append("COMPONENT_CATALOG_MISSING")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args(argv)
    errors = validate(args.root.resolve())
    if errors:
        print("\n".join(errors))
        return 1
    with (args.root / "design/component-catalog.csv").open(
        encoding="utf-8-sig", newline=""
    ) as handle:
        component_count = len(list(csv.DictReader(handle)))
    print("UI_TOKENS_OK", component_count)
    return 0


if __name__ == "__main__":
    sys.exit(main())
