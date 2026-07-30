#!/usr/bin/env python3
"""Generate a deterministic, read-only development boundary for one release."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
from pathlib import Path
from typing import Any, Iterable

import yaml


ROOT = Path(__file__).resolve().parents[1]
SCHEMA = "hhy.release-slice/v1"
CATALOGS = (
    ("CLIENT", "catalogs/api_endpoints.csv", "contracts/openapi.yaml"),
    ("ADMIN", "catalogs/admin_api_endpoints.csv", "contracts/admin-openapi.yaml"),
)
UNION_FIELDS = (
    "page_ids",
    "operation_ids",
    "api_contracts",
    "requirement_ids",
    "config_keys",
    "data_tables",
    "test_ids",
    "owner_roles",
)


class ReleaseSliceError(RuntimeError):
    """Raised when release facts cannot be joined without ambiguity."""


def _relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def _operation_index(spec_path: Path) -> dict[tuple[str, str], str]:
    document = yaml.safe_load(spec_path.read_text(encoding="utf-8"))
    index: dict[tuple[str, str], str] = {}
    seen_ids: set[str] = set()
    for path, path_item in document.get("paths", {}).items():
        for method, operation in path_item.items():
            if method.lower() not in {"get", "post", "put", "patch", "delete"}:
                continue
            operation_id = operation.get("operationId")
            if not operation_id:
                raise ReleaseSliceError(f"missing operationId: {method.upper()} {path}")
            if operation_id in seen_ids:
                raise ReleaseSliceError(f"duplicate operationId in {_relative(spec_path)}: {operation_id}")
            seen_ids.add(operation_id)
            index[(method.upper(), path)] = operation_id
    return index


def _as_list(value: Any, *, field: str, story_id: str) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list) or not all(isinstance(item, str) and item for item in value):
        raise ReleaseSliceError(f"{story_id}.{field} must be a list of non-empty strings")
    return value


def _sorted_unique(values: Iterable[str]) -> list[str]:
    return sorted(set(values))


def build_release_slice(release: str, *, root: Path = ROOT) -> dict[str, Any]:
    global ROOT
    original_root = ROOT
    ROOT = root.resolve()
    try:
        stories_path = ROOT / "releases" / release / "STORIES.yaml"
        if not stories_path.is_file():
            raise ReleaseSliceError(f"stories file not found: {_relative(stories_path)}")
        story_document = yaml.safe_load(stories_path.read_text(encoding="utf-8"))
        stories = story_document.get("stories") if isinstance(story_document, dict) else None
        if not isinstance(stories, list) or not stories:
            raise ReleaseSliceError(f"{_relative(stories_path)} must contain non-empty stories")

        sources = [stories_path]
        catalog_operations: list[dict[str, str]] = []
        operation_by_id: dict[str, dict[str, str]] = {}
        for surface, catalog_relative, contract_relative in CATALOGS:
            catalog_path = ROOT / catalog_relative
            contract_path = ROOT / contract_relative
            sources.extend((catalog_path, contract_path))
            contract_index = _operation_index(contract_path)
            for row_number, row in enumerate(_read_csv(catalog_path), start=2):
                method = row.get("方法", "").upper()
                path = row.get("路径", "")
                operation_id = contract_index.get((method, path))
                if not operation_id:
                    raise ReleaseSliceError(
                        f"{catalog_relative}:{row_number} missing from {contract_relative}: {method} {path}"
                    )
                entry = {
                    "operation_id": operation_id,
                    "surface": surface,
                    "module": row.get("模块", ""),
                    "method": method,
                    "path": path,
                    "permission": row.get("权限", ""),
                    "idempotent": row.get("幂等", ""),
                    "catalog_release": row.get("计划版本", ""),
                }
                if operation_id in operation_by_id:
                    raise ReleaseSliceError(f"operationId appears in multiple catalogs: {operation_id}")
                operation_by_id[operation_id] = entry
                if row.get("计划版本") == release:
                    catalog_operations.append(entry)

        normalized_stories: list[dict[str, Any]] = []
        story_ids: set[str] = set()
        unions: dict[str, list[str]] = {field: [] for field in UNION_FIELDS}
        for position, story in enumerate(stories, start=1):
            if not isinstance(story, dict):
                raise ReleaseSliceError(f"story #{position} must be an object")
            story_id = story.get("story_id")
            if not isinstance(story_id, str) or not story_id:
                raise ReleaseSliceError(f"story #{position} has no story_id")
            if story_id in story_ids:
                raise ReleaseSliceError(f"duplicate story_id: {story_id}")
            story_ids.add(story_id)
            if story.get("release") != release:
                raise ReleaseSliceError(f"{story_id}.release must be {release}")

            normalized: dict[str, Any] = {
                "story_id": story_id,
                "title": story.get("title", ""),
                "platform": story.get("platform", ""),
                "module": story.get("module", ""),
                "template_id": story.get("template_id", ""),
                "status": story.get("status", ""),
            }
            for field in UNION_FIELDS:
                items = _as_list(story.get(field), field=field, story_id=story_id)
                normalized[field] = items
                unions[field].extend(items)
            for operation_id in normalized["operation_ids"]:
                if operation_id not in operation_by_id:
                    raise ReleaseSliceError(f"{story_id} references unknown operationId: {operation_id}")
            normalized_stories.append(normalized)

        normalized_stories.sort(key=lambda item: item["story_id"])
        union_payload = {field: _sorted_unique(values) for field, values in unions.items()}
        story_operation_details = [operation_by_id[item] for item in union_payload["operation_ids"]]
        catalog_operations.sort(key=lambda item: item["operation_id"])

        return {
            "schema": SCHEMA,
            "release": release,
            "sources": [
                {"path": _relative(path), "sha256": _sha256(path)}
                for path in sorted(set(sources), key=_relative)
            ],
            "counts": {
                "stories": len(normalized_stories),
                "catalog_release_exact_operations": len(catalog_operations),
                "story_unique_operations": len(story_operation_details),
                "story_unique_pages": len(union_payload["page_ids"]),
                "story_unique_requirements": len(union_payload["requirement_ids"]),
                "story_unique_configs": len(union_payload["config_keys"]),
                "story_unique_tables": len(union_payload["data_tables"]),
                "story_unique_tests": len(union_payload["test_ids"]),
            },
            "catalog_release_exact_operations": catalog_operations,
            "story_unique_operation_details": story_operation_details,
            "story_union": union_payload,
            "stories": normalized_stories,
        }
    finally:
        ROOT = original_root


def render_release_slice(payload: dict[str, Any], suffix: str = ".json") -> str:
    if suffix.lower() in {".yaml", ".yml"}:
        return yaml.safe_dump(payload, allow_unicode=True, sort_keys=False, width=120)
    if suffix.lower() != ".json":
        raise ReleaseSliceError("output extension must be .json, .yaml, or .yml")
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def write_or_check(output: Path, content: str, *, check: bool) -> bool:
    if check:
        if not output.is_file():
            print(f"RELEASE_SLICE_DRIFT missing={output}", file=sys.stderr)
            return False
        actual = output.read_text(encoding="utf-8")
        if actual != content:
            print(f"RELEASE_SLICE_DRIFT changed={output}", file=sys.stderr)
            return False
        print(f"RELEASE_SLICE_OK output={output}")
        return True
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(content, encoding="utf-8", newline="\n")
    print(f"RELEASE_SLICE_WRITTEN output={output}")
    return True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", default="R02", help="release identifier, default: R02")
    parser.add_argument("--output", type=Path, help="write JSON/YAML; without it JSON is printed")
    parser.add_argument("--check", action="store_true", help="compare --output without writing")
    args = parser.parse_args(argv)
    if args.check and args.output is None:
        parser.error("--check requires --output")
    try:
        payload = build_release_slice(args.release)
        suffix = args.output.suffix if args.output else ".json"
        content = render_release_slice(payload, suffix)
        if args.output:
            return 0 if write_or_check(args.output, content, check=args.check) else 1
        sys.stdout.write(content)
        return 0
    except (OSError, KeyError, TypeError, yaml.YAMLError, ReleaseSliceError) as exc:
        print(f"RELEASE_SLICE_FAIL {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
