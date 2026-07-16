#!/usr/bin/env python3
"""Generate transfer-integrity checksums for the clean development package."""
from __future__ import annotations

import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "MANIFEST_SHA256.txt"
IGNORED_PARTS = {"node_modules", "dist", "target", "build", ".gradle", "__pycache__"}


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


files = sorted(
    path
    for path in ROOT.rglob("*")
    if path.is_file()
    and path != OUTPUT
    and not any(part in IGNORED_PARTS for part in path.relative_to(ROOT).parts)
    and path.suffix != ".pyc"
)
OUTPUT.write_text(
    "".join(f"{digest(path)}  {path.relative_to(ROOT)}\n" for path in files),
    encoding="utf-8",
    newline="\n",
)
print(f"wrote {len(files)} checksums to {OUTPUT.name}")
