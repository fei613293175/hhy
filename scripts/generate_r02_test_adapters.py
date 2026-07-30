#!/usr/bin/env python3
"""Generate the closed R02 catalog-to-runner adapter inventory."""
from __future__ import annotations

import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "catalogs/test_cases.csv"
SHARED_ADMIN_IDS = {
    f"TST-ADMIN_OPS_001-{suffix}" for suffix in ("DRIFT", "HAPPY", "REJECT", "SECURITY")
}
AUTH_IDS = {
    f"TST-AUTH_{number:03d}-{suffix}"
    for number in range(1, 6)
    for suffix in ("HAPPY", "IDEMPOTENT", "REJECT", "SECURITY")
}
R02_IDS = SHARED_ADMIN_IDS | AUTH_IDS | {"TST-V122-011"}


def assignment(test_id: str) -> tuple[str, str]:
    if test_id == "TST-ADMIN_OPS_001-DRIFT":
        return "local", "documentation-r02"
    if test_id in SHARED_ADMIN_IDS:
        return "local", "admin-web-integration"
    return "external", "r02-java21-pg17-android-real-api"


def main() -> int:
    with CATALOG.open(encoding="utf-8-sig", newline="") as handle:
        rows = {row["测试ID"].strip(): row for row in csv.DictReader(handle)}
    missing = sorted(R02_IDS - set(rows))
    if missing:
        raise SystemExit(f"catalog missing R02 tests: {missing}")

    for test_id in sorted(R02_IDS):
        raw_path = rows[test_id]["自动化路径"].strip().replace("\\", "/")
        target = ROOT / raw_path
        target.parent.mkdir(parents=True, exist_ok=True)
        source, runner = assignment(test_id)
        if test_id in SHARED_ADMIN_IDS:
            payload = {
                "schema": "hhy.test-adapter/v2",
                "test_id": test_id,
                "assignments": {
                    "R01": {
                        "central_runner": "scripts/run_r01_test_matrix.py",
                        "assignment_source": "local",
                        "runner": "documentation-r01",
                    },
                    "R02": {
                        "central_runner": "scripts/run_r02_test_matrix.py",
                        "assignment_source": source,
                        "runner": runner,
                    },
                },
            }
        else:
            payload = {
                "schema": "hhy.r02.test-adapter/v1",
                "test_id": test_id,
                "central_runner": "scripts/run_r02_test_matrix.py",
                "assignment_source": source,
                "runner": runner,
            }
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(target.relative_to(ROOT).as_posix())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
