#!/usr/bin/env python3
"""Enforce the shared Android icon, navigation, back and motion foundation."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps" / "android"
FORBIDDEN_ICON_LITERALS = ("‹", "←", "✓", "●", "◷", "相", "人", "首", "包", "发", "信", "我")


def _text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def find_violations(root: Path = ROOT) -> list[str]:
    android = root / "apps" / "android"
    violations: list[str] = []
    kotlin_files = [path for path in android.rglob("*.kt") if "build" not in path.parts]

    for path in kotlin_files:
        text = _text(path)
        relative = path.relative_to(root).as_posix()
        for literal in FORBIDDEN_ICON_LITERALS:
            if re.search(rf'(["\']){re.escape(literal)}\1', text):
                violations.append(f"{relative}: forbidden text/Unicode icon literal {literal!r}")
        if "compactLabel" in text:
            violations.append(f"{relative}: text compactLabel must not be used as an icon")
        if re.search(r"mutableStateOf\s*<[^>]*(Destination|Route)", text):
            violations.append(f"{relative}: route state must use Jetpack Navigation back stack")
        if re.search(r"navigationIcon\s*=\s*\{\s*Text", text):
            violations.append(f"{relative}: navigation icon must come from HhyIcons")

    required_markers = {
        "apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt": (
            "rememberNavController()",
            "NavHost(",
            "popEnterTransition",
            "popExitTransition",
            "AuthenticatedRoute.Home",
            "AuthenticatedRoute.Me",
            "saveState = true",
            "launchSingleTop = true",
            "restoreState = true",
        ),
        "apps/android/feature/identity/src/main/java/cc/orbexa/hhy/identity/IdentityFlowScreen.kt": (
            "rememberNavController()",
            "NavHost(",
            "HhyBackButton",
        ),
        "apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt": (
            "rememberNavController()",
            "NavHost(",
            "popEnterTransition",
            "popExitTransition",
            "AnimatedContent(",
            "HhyMotion.peerContent()",
            "HhyBackButton",
        ),
        "apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt": (
            "HhyTopLevelDestination",
            "AnimatedContent(",
            "HhyMotion.peerContent()",
            "HhyIcons.",
            "HhyIcon(",
        ),
        "apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyIcons.kt": (
            "object HhyIcons",
            "fun HhyBackButton",
        ),
        "apps/android/core/designsystem/src/main/java/cc/orbexa/hhy/designsystem/HhyMotion.kt": (
            "StandardMillis = 200",
            "fun forwardEnter()",
            "fun backwardExit()",
        ),
    }
    for relative, markers in required_markers.items():
        path = root / relative
        if not path.exists():
            violations.append(f"{relative}: required shared UI foundation file is missing")
            continue
        text = _text(path)
        for marker in markers:
            if marker not in text:
                violations.append(f"{relative}: missing required marker {marker!r}")

    auth = android / "feature" / "auth" / "src" / "main" / "java" / "cc" / "orbexa" / "hhy" / "auth" / "AuthScreen.kt"
    if auth.exists():
        auth_text = _text(auth)
        if re.search(r"\bvar\s+route\s+by\s+remember", auth_text):
            violations.append("apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt: auth destinations must use a real Navigation back stack")
        if "HhyMotion.forwardContent()" in auth_text:
            violations.append("apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt: peer login tabs must not use directional page motion")

    shell = android / "feature" / "shell" / "src" / "main" / "java" / "cc" / "orbexa" / "hhy" / "shell" / "HhyShellScreen.kt"
    if shell.exists():
        shell_text = _text(shell)
        if re.search(r"\bvar\s+selectedIndex\s+by\s+remember", shell_text):
            violations.append("apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt: bottom navigation must derive selection from the typed NavDestination")
        if "ShellPlaceholder(" in shell_text:
            violations.append("apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt: disabled top-level destinations must not open placeholder pages")

    catalog = _text(android / "gradle" / "libs.versions.toml")
    match = re.search(r'^navigation\s*=\s*"([^"]+)"', catalog, flags=re.MULTILINE)
    if not match:
        violations.append("apps/android/gradle/libs.versions.toml: stable Jetpack Navigation version is missing")
    elif re.search(r"(?:alpha|beta|rc|snapshot)", match.group(1), flags=re.IGNORECASE):
        violations.append("apps/android/gradle/libs.versions.toml: pre-release navigation dependency is forbidden")

    return sorted(set(violations))


def main() -> int:
    violations = find_violations()
    if violations:
        print("ANDROID_UI_FOUNDATION_GATE=FAIL")
        for violation in violations:
            print(f"- {violation}")
        return 1
    print("ANDROID_UI_FOUNDATION_GATE=PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
