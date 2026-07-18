#!/usr/bin/env python3
"""Reject technical diagnostics accidentally rendered by production UI sources."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOTS = (ROOT / "apps/android/feature", ROOT / "apps/android/app", ROOT / "apps/admin-web/src", ROOT / "apps/h5/src")
SOURCE_SUFFIXES = {".kt", ".vue", ".tsx", ".jsx", ".html"}
SKIP_PARTS = {"test", "tests", "__tests__"}
FORBIDDEN = (
    ("请求编号", re.compile(r"请求编号")),
    ("请求标识", re.compile(r"请求标识")),
    ("敏感信息审计提示", re.compile(r"敏感信息仅用于本次认证")),
    ("技术验证码按钮", re.compile(r"创建安全验证|加载当前注册协议|校验邀请码")),
    ("技术验证码字段", re.compile(r"安全验证结果")),
)

def production_sources() -> list[Path]:
    files: list[Path] = []
    for root in SOURCE_ROOTS:
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if path.is_file() and path.suffix.lower() in SOURCE_SUFFIXES:
                parts = {part.lower() for part in path.relative_to(root).parts}
                if not (parts & SKIP_PARTS) and ".test." not in path.name:
                    files.append(path)
    return sorted(files)

def violations() -> list[str]:
    found: list[str] = []
    for path in production_sources():
        text = path.read_text(encoding="utf-8")
        for label, pattern in FORBIDDEN:
            for match in pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                found.append(f"{path.relative_to(ROOT).as_posix()}:{line}: {label}: {match.group(0)}")
    return found

def main() -> int:
    found = violations()
    if found:
        print("COMMERCIAL_UI_BOUNDARY=FAIL")
        print("\n".join(found))
        return 1
    print(f"COMMERCIAL_UI_BOUNDARY=PASS sources={len(production_sources())}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
