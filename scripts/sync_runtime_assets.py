#!/usr/bin/env python3
"""Synchronize immutable contracts and migrations into backend runtime resources.

The files under ``contracts/`` and ``database/migrations/`` are authoritative.
This command never rewrites them; it only copies their bytes to the Spring Boot
classpath and can run in read-only ``--check`` mode in CI.
"""
from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import hashlib
import json
import os


ROOT = Path(__file__).resolve().parents[1]
CONTRACT_NAMES = (
    "openapi.yaml",
    "admin-openapi.yaml",
    "websocket-events.yaml",
    "error-codes.csv",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def mappings() -> list[tuple[Path, Path]]:
    contract_target = ROOT / "services/backend/boot/src/main/resources/contracts"
    migration_target = ROOT / "services/backend/boot/src/main/resources/db/migration"
    rows = [
        (ROOT / "contracts" / name, contract_target / name)
        for name in CONTRACT_NAMES
    ]
    rows.extend(
        (source, migration_target / source.name)
        for source in sorted((ROOT / "database/migrations").glob("V*.sql"))
    )
    return rows


def atomic_copy(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".sync-tmp")
    try:
        temporary.write_bytes(source.read_bytes())
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify only; do not write")
    args = parser.parse_args()

    rows = mappings()
    expected_migrations = {source.name for source, _ in rows if source.parent.name == "migrations"}
    runtime_migrations = ROOT / "services/backend/boot/src/main/resources/db/migration"
    unexpected = sorted(path.name for path in runtime_migrations.glob("V*.sql") if path.name not in expected_migrations)
    if unexpected:
        print(json.dumps({"status": "FAIL", "unexpected_runtime_migrations": unexpected}, ensure_ascii=False))
        return 1

    changed: list[str] = []
    errors: list[str] = []
    evidence: list[dict[str, str]] = []
    for source, target in rows:
        relative_source = source.relative_to(ROOT).as_posix()
        relative_target = target.relative_to(ROOT).as_posix()
        if not source.is_file():
            errors.append(f"missing source: {relative_source}")
            continue
        source_hash = sha256(source)
        target_hash = sha256(target) if target.is_file() else None
        if target_hash != source_hash:
            if args.check:
                errors.append(f"runtime asset drift: {relative_target}")
            else:
                atomic_copy(source, target)
                changed.append(relative_target)
                target_hash = sha256(target)
        if target_hash != source_hash:
            errors.append(f"hash mismatch after sync: {relative_target}")
        evidence.append({"source": relative_source, "target": relative_target, "sha256": source_hash})

    payload = {
        "status": "PASS" if not errors else "FAIL",
        "mode": "CHECK" if args.check else "SYNC",
        "asset_count": len(rows),
        "changed": changed,
        "errors": errors,
        "assets": evidence,
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
