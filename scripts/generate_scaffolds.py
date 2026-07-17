#!/usr/bin/env python3
"""Synchronize non-destructive frontend catalog scaffolds.

This script deliberately does not overwrite backend, infrastructure, package-manager,
or build-tool configuration. Those files are curated engineering baselines. It only
regenerates catalog-derived assets whose source of truth is under ``catalogs/`` and
regenerates shared domain enum types.
"""
from __future__ import annotations

import csv
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read_csv(relative: str) -> list[dict[str, str]]:
    with (ROOT / relative).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_json(relative: str, value: object) -> None:
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def vue_route(path: str) -> str:
    import re

    return re.sub(r"\{([^}]+)\}", r":\1", path)


def main() -> int:
    admin_pages = read_csv("catalogs/admin_pages.csv")
    h5_pages = read_csv("catalogs/h5_screens.csv")
    page_specs = {row["页面ID"]: row for row in read_csv("catalogs/ui_page_specifications.csv")}
    admin_operations = {row["页面ID"]: row for row in read_csv("catalogs/admin_page_operation_specs.csv")}

    if len(admin_pages) != 74:
        raise SystemExit(f"expected 74 admin pages, got {len(admin_pages)}")
    if len(h5_pages) != 13:
        raise SystemExit(f"expected 13 H5 pages, got {len(h5_pages)}")
    if len({row["ID"] for row in admin_pages}) != len(admin_pages):
        raise SystemExit("duplicate admin page ID")
    if len({row["路由"] for row in admin_pages}) != len(admin_pages):
        raise SystemExit("duplicate admin route")
    if len({row["ID"] for row in h5_pages}) != len(h5_pages):
        raise SystemExit("duplicate H5 page ID")
    if len({row["路由"] for row in h5_pages}) != len(h5_pages):
        raise SystemExit("duplicate H5 route")

    enriched_admin = []
    for row in admin_pages:
        spec = page_specs.get(row["ID"])
        operations = admin_operations.get(row["ID"])
        if not spec or not operations:
            raise SystemExit(f"missing enriched admin specification for {row['ID']}")
        enriched_admin.append({
            **row,
            "模板ID": spec["模板ID"],
            "DoR状态": spec["DoR状态"],
            "筛选项": operations["筛选项"],
            "表格列": operations["表格列"],
            "行操作": operations["行操作"],
            "文档版本": "1.2.2",
        })
    write_json("apps/admin-web/src/generated/admin-pages.json", enriched_admin)
    enriched_h5 = []
    for row in h5_pages:
        spec = page_specs.get(row["ID"])
        if not spec:
            raise SystemExit(f"missing enriched H5 specification for {row['ID']}")
        enriched_h5.append({
            **row,
            "vueRoute": vue_route(row["路由"]),
            "模板ID": spec["模板ID"],
            "DoR状态": spec["DoR状态"],
            "文档版本": "1.2.2",
        })
    write_json(
        "apps/h5/src/generated/h5-pages.json",
        enriched_h5,
    )
    subprocess.run(
        [sys.executable, str(ROOT / "scripts/generate_frontend_types.py")],
        cwd=ROOT,
        check=True,
    )
    print(f"synced admin_pages={len(admin_pages)} h5_pages={len(h5_pages)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
