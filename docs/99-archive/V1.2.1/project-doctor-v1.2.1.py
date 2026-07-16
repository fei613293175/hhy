#!/usr/bin/env python3
"""Semantic quality gate for the 合伙云 Pro V1.2.2 development baseline.

The doctor validates the relationships between catalogs, OpenAPI, WebSocket events,
state machines, Flyway migrations, frontend route/action bindings, release artifacts,
and executable scaffolds. It intentionally checks semantics rather than only whether
files exist.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import sys
import zipfile
from collections import Counter, defaultdict, deque
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path
from typing import Any, Iterable
import time

try:
    import yaml
except ImportError as exc:  # pragma: no cover - environment diagnostic
    raise SystemExit("PyYAML is required: python3 -m pip install -r requirements-dev.txt") from exc

try:
    from pglast import parse_sql
except ImportError as exc:  # pragma: no cover - environment diagnostic
    raise SystemExit("pglast is required: python3 -m pip install -r requirements-dev.txt") from exc

ROOT = Path(__file__).resolve().parents[1]
HTTP_METHODS = {"get", "post", "put", "patch", "delete"}
EXPECTED_COUNTS = {
    "catalogs/requirements_catalog.csv": 75,
    "catalogs/android_screens.csv": 96,
    "catalogs/h5_screens.csv": 13,
    "catalogs/admin_pages.csv": 60,
    "catalogs/api_endpoints.csv": 131,
    "catalogs/admin_api_endpoints.csv": 162,
    "catalogs/data_tables.csv": 188,
    "catalogs/config_registry.csv": 314,
    "catalogs/test_cases.csv": 280,
    "catalogs/frontend_backend_matrix.csv": 169,
    "catalogs/ui_action_matrix.csv": 546,
    "catalogs/TRACEABILITY_MATRIX.csv": 75,
    "catalogs/release_plan.csv": 33,
}
REQUIRED_FILES = [
    "PROJECT_MANIFEST.yaml",
    "README.md",
    "START_HERE.md",
    "contracts/openapi.yaml",
    "contracts/admin-openapi.yaml",
    "contracts/websocket-events.yaml",
    "contracts/error-codes.csv",
    "contracts/contract_status.csv",
    "database/enum_registry.yaml",
    "database/state_machines.yaml",
    "database/schema_dictionary.csv",
    "database/schema_traceability.csv",
    "config/CONFIG_REGISTRY.yaml",
    "releases/RELEASE_DEPENDENCIES.yaml",
    "services/backend/pom.xml",
    "services/backend/mvnw",
    "apps/android/gradlew",
    "apps/admin-web/package.json",
    "apps/h5/package.json",
    "DEVELOPMENT_RISK_REGISTER.md",
    "V1.2.2_正式开发发布说明.md",
    "artifacts/reports/2026-07-16/FINAL_VALIDATION_SUMMARY.md",
    "artifacts/reports/2026-07-16/FINAL_VALIDATION_SUMMARY.json",
    "catalogs/development_risk_register.csv",
    "database/tests/postgres_smoke_success.sql",
    "scripts/run_postgres_migration_smoke.sh",
    ".github/workflows/ci.yml",
]


@lru_cache(maxsize=None)
def read_csv(relative: str) -> list[dict[str, str]]:
    path = ROOT / relative
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


@lru_cache(maxsize=None)
def load_yaml(relative: str) -> Any:
    return yaml.safe_load((ROOT / relative).read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def split_semicolon(value: str | None) -> list[str]:
    return [item.strip() for item in (value or "").split(";") if item.strip()]


def operation_key(method: str, path: str) -> str:
    return f"{method.upper()} {path}"


def resolve_local_ref(document: dict[str, Any], ref: str) -> Any:
    if not ref.startswith("#/"):
        raise KeyError(f"external reference is not allowed: {ref}")
    current: Any = document
    for raw in ref[2:].split("/"):
        token = raw.replace("~1", "/").replace("~0", "~")
        current = current[token]
    return current


def iter_refs(value: Any) -> Iterable[str]:
    if isinstance(value, dict):
        for key, item in value.items():
            if key == "$ref" and isinstance(item, str):
                yield item
            else:
                yield from iter_refs(item)
    elif isinstance(value, list):
        for item in value:
            yield from iter_refs(item)


def text_files(root: Path) -> Iterable[Path]:
    """Yield text candidates while pruning large generated trees during traversal."""
    ignored = {
        ".git",
        "node_modules",
        "dist",
        "target",
        "build",
        ".gradle",
        "99-archive",
        "__pycache__",
    }
    binary_suffixes = {
        ".png", ".jpg", ".jpeg", ".webp", ".gif", ".pdf", ".zip", ".jar",
        ".woff", ".woff2", ".ttf", ".otf", ".apk", ".keystore", ".jks",
    }
    for current, directories, filenames in os.walk(root):
        directories[:] = [name for name in directories if name not in ignored]
        base = Path(current)
        for filename in filenames:
            path = base / filename
            if path.suffix.lower() in binary_suffixes or path.suffix.lower() == ".pyc":
                continue
            yield path


@dataclass
class Finding:
    section: str
    level: str
    message: str


@dataclass
class Doctor:
    strict: bool
    allow_build_artifacts: bool
    verify_package_integrity: bool
    release: str | None
    findings: list[Finding] = field(default_factory=list)
    metrics: dict[str, Any] = field(default_factory=dict)

    def error(self, section: str, message: str) -> None:
        self.findings.append(Finding(section, "ERROR", message))

    def warning(self, section: str, message: str) -> None:
        self.findings.append(Finding(section, "WARNING", message))

    def ok_metric(self, key: str, value: Any) -> None:
        self.metrics[key] = value

    def require(self, condition: bool, section: str, message: str) -> None:
        if not condition:
            self.error(section, message)

    def run(self) -> None:
        started = time.perf_counter()
        self.check_files_and_manifest()
        self.check_catalogs()
        operations = self.check_openapi()
        self.check_websocket()
        self.check_states()
        self.check_database()
        self.check_ui(operations)
        self.check_traceability(operations)
        self.check_config()
        self.check_development_risks()
        self.check_releases(operations)
        self.check_scaffolds()
        self.check_security_and_portability()
        if self.verify_package_integrity:
            self.check_integrity_manifest()
        self.ok_metric("doctor_elapsed_seconds", round(time.perf_counter() - started, 3))

    def check_files_and_manifest(self) -> None:
        section = "baseline"
        for relative in REQUIRED_FILES:
            self.require((ROOT / relative).is_file(), section, f"missing required file: {relative}")
        if not (ROOT / "PROJECT_MANIFEST.yaml").exists():
            return
        manifest = load_yaml("PROJECT_MANIFEST.yaml")
        self.require(str(manifest.get("package_version")) == "1.2.2", section, "package_version must be 1.2.2")
        self.require(manifest.get("status") == "READY_FOR_FEATURE_DEVELOPMENT", section, "manifest status must be READY_FOR_FEATURE_DEVELOPMENT")
        self.require(manifest.get("quality_gate") == "scripts/project-doctor.py --strict", section, "quality_gate command drift")
        canonical = manifest.get("canonical_document")
        self.require(bool(canonical and (ROOT / canonical).is_file()), section, f"canonical document missing: {canonical}")
        counts = manifest.get("counts", {})
        for relative, expected in EXPECTED_COUNTS.items():
            key = Path(relative).name
            self.require(counts.get(key) == expected, section, f"manifest count {key} must be {expected}")
        baseline = load_yaml("PROJECT_BASELINE.yaml")
        self.require(manifest == baseline, section, "PROJECT_MANIFEST.yaml and PROJECT_BASELINE.yaml differ")
        baseline_json = json.loads((ROOT / "PROJECT_BASELINE.json").read_text(encoding="utf-8"))
        self.require(baseline_json == manifest, section, "PROJECT_BASELINE.json differs from YAML baseline")
        self.ok_metric("package_version", "1.2.2")

    def check_catalogs(self) -> None:
        section = "catalogs"
        count_metrics: dict[str, int] = {}
        for relative, expected in EXPECTED_COUNTS.items():
            path = ROOT / relative
            if not path.exists():
                continue
            rows = read_csv(relative)
            count_metrics[Path(relative).name] = len(rows)
            self.require(len(rows) == expected, section, f"{relative}: expected {expected}, got {len(rows)}")
        self.ok_metric("catalog_counts", count_metrics)

        uniqueness = {
            "catalogs/requirements_catalog.csv": "需求ID",
            "catalogs/android_screens.csv": "ID",
            "catalogs/h5_screens.csv": "ID",
            "catalogs/admin_pages.csv": "ID",
            "catalogs/data_tables.csv": "表名",
            "catalogs/config_registry.csv": "key",
            "catalogs/test_cases.csv": "测试ID",
            "catalogs/ui_action_matrix.csv": "动作ID",
            "catalogs/TRACEABILITY_MATRIX.csv": "需求ID",
            "catalogs/release_plan.csv": "版本",
        }
        for relative, field_name in uniqueness.items():
            rows = read_csv(relative)
            values = [row[field_name] for row in rows]
            duplicates = [key for key, count in Counter(values).items() if count > 1]
            self.require(not duplicates, section, f"{relative}: duplicate {field_name}: {duplicates[:10]}")
            self.require(all(values), section, f"{relative}: blank {field_name}")

        req = {row["需求ID"]: row for row in read_csv("catalogs/requirements_catalog.csv")}
        self.require(req.get("REQ-AUTH-005", {}).get("计划版本") == "R02", section, "REQ-AUTH-005 must only be planned in R02")
        banned_markers = {"TBD", "ADMIN_ROUTE_TBD", "RELEASE_BOUND", "CATALOG_BOUND"}
        for relative in [
            "catalogs/admin_pages.csv",
            "catalogs/frontend_backend_matrix.csv",
            "catalogs/ui_action_matrix.csv",
            "catalogs/TRACEABILITY_MATRIX.csv",
        ]:
            for row_number, row in enumerate(read_csv(relative), 2):
                joined = " | ".join(row.values())
                for marker in banned_markers:
                    if marker in joined:
                        self.error(section, f"{relative}:{row_number} contains unresolved marker {marker}")

    def openapi_operations(self, relative: str, catalog_relative: str, contract_type: str) -> dict[str, dict[str, Any]]:
        section = "openapi"
        document = load_yaml(relative)
        self.require(document.get("openapi") == "3.1.0", section, f"{relative}: OpenAPI must be 3.1.0")
        self.require(str(document.get("info", {}).get("version")) == "1.2.2", section, f"{relative}: info.version drift")
        schemas = document.get("components", {}).get("schemas", {})
        self.require("GenericCommand" not in schemas, section, f"{relative}: GenericCommand is forbidden")
        placeholder_schemas = sorted(name for name in schemas if str(name).upper() in {"NONE", "TBD", "UNKNOWN", "PLACEHOLDER"})
        self.require(not placeholder_schemas, section, f"{relative}: placeholder schemas are forbidden: {placeholder_schemas}")
        catalog = read_csv(catalog_relative)
        expected = {operation_key(row["方法"], row["路径"]): row for row in catalog}
        actual: dict[str, dict[str, Any]] = {}
        operation_ids: list[str] = []
        error_codes = {row["错误码"] for row in read_csv("contracts/error-codes.csv")}

        for path, path_item in document.get("paths", {}).items():
            for method, operation in path_item.items():
                if method.lower() not in HTTP_METHODS:
                    continue
                key = operation_key(method, path)
                actual[key] = operation
                opid = operation.get("operationId")
                operation_ids.append(opid)
                self.require(isinstance(opid, str) and bool(opid), section, f"{relative}: {key} missing operationId")
                self.require(operation.get("x-contract-maturity") == "FROZEN", section, f"{relative}: {key} not FROZEN")
                permission = str(operation.get("x-permission", ""))
                security = operation.get("security")
                if permission.startswith("公开"):
                    self.require(security == [], section, f"{relative}: public operation {key} must declare security: []")
                else:
                    self.require(isinstance(security, list) and len(security) > 0, section, f"{relative}: protected operation {key} lacks security")

                expected_path_parameters = set(re.findall(r"\{([^}]+)\}", path))
                parameter_names: set[str] = set()
                idempotency_header = False
                for parameter in operation.get("parameters", []):
                    resolved = resolve_local_ref(document, parameter["$ref"]) if "$ref" in parameter else parameter
                    name = resolved.get("name")
                    if name:
                        parameter_names.add(name)
                    if resolved.get("in") == "path" and name in expected_path_parameters:
                        self.require(resolved.get("required") is True, section, f"{relative}: {key} path parameter {name} must be required")
                    if resolved.get("in") == "header" and name == "X-Idempotency-Key":
                        idempotency_header = True
                self.require(expected_path_parameters <= parameter_names, section, f"{relative}: {key} missing path parameters {sorted(expected_path_parameters - parameter_names)}")
                if operation.get("x-idempotent") is True:
                    self.require(idempotency_header, section, f"{relative}: {key} is idempotent but lacks X-Idempotency-Key")

                request_contract_ref = operation.get("x-request-schema")
                self.require(isinstance(request_contract_ref, str) and request_contract_ref.startswith("#/components/schemas/"), section, f"{relative}: {key} missing x-request-schema")
                if isinstance(request_contract_ref, str) and request_contract_ref.startswith("#/"):
                    try:
                        resolve_local_ref(document, request_contract_ref)
                    except (KeyError, TypeError) as exc:
                        self.error(section, f"{relative}: {key} unresolved request contract {request_contract_ref}: {exc}")
                if method.lower() in {"post", "put", "patch"}:
                    body = operation.get("requestBody")
                    if body:
                        schema = body.get("content", {}).get("application/json", {}).get("schema", {})
                        self.require("$ref" in schema, section, f"{relative}: {key} request body must use a named schema")
                        self.require(schema.get("$ref") == request_contract_ref, section, f"{relative}: {key} requestBody and x-request-schema differ")
                else:
                    self.require("requestBody" not in operation, section, f"{relative}: {key} GET/DELETE must not declare requestBody")
                success = operation.get("responses", {}).get("200") or operation.get("responses", {}).get(200)
                self.require(bool(success), section, f"{relative}: {key} lacks 200 response")
                if success:
                    schema = success.get("content", {}).get("application/json", {}).get("schema", {})
                    self.require("$ref" in schema, section, f"{relative}: {key} response must use a named schema")
                codes = operation.get("x-error-codes", [])
                self.require(bool(codes), section, f"{relative}: {key} has no x-error-codes")
                unknown_codes = sorted(set(codes) - error_codes)
                self.require(not unknown_codes, section, f"{relative}: {key} references unknown errors {unknown_codes}")

        self.require(set(actual) == set(expected), section, f"{relative}: operation set differs from {catalog_relative}; missing={sorted(set(expected)-set(actual))[:5]}, extra={sorted(set(actual)-set(expected))[:5]}")
        duplicates = [key for key, count in Counter(operation_ids).items() if count > 1]
        self.require(not duplicates, section, f"{relative}: duplicate operationIds {duplicates[:10]}")
        for ref in iter_refs(document):
            try:
                resolve_local_ref(document, ref)
            except (KeyError, TypeError) as exc:
                self.error(section, f"{relative}: unresolved ref {ref}: {exc}")
        self.ok_metric(f"{contract_type.lower()}_operations", len(actual))
        return actual

    def check_openapi(self) -> dict[str, dict[str, Any]]:
        client = self.openapi_operations("contracts/openapi.yaml", "catalogs/api_endpoints.csv", "CLIENT")
        admin = self.openapi_operations("contracts/admin-openapi.yaml", "catalogs/admin_api_endpoints.csv", "ADMIN")
        operations = {**client, **admin}
        section = "openapi"
        statuses = read_csv("contracts/contract_status.csv")
        api_statuses = [row for row in statuses if row["契约类型"] in {"CLIENT_API", "ADMIN_API"}]
        self.require(len(api_statuses) == len(operations), section, f"contract_status API rows expected {len(operations)}, got {len(api_statuses)}")
        status_keys = {row["契约标识"] for row in api_statuses}
        self.require(status_keys == set(operations), section, "contract_status API identifiers do not match OpenAPI")
        hashes = {
            "contracts/openapi.yaml": sha256(ROOT / "contracts/openapi.yaml"),
            "contracts/admin-openapi.yaml": sha256(ROOT / "contracts/admin-openapi.yaml"),
        }
        opid_to_status: dict[str, dict[str, str]] = {}
        for row in api_statuses:
            self.require(row["成熟度"] == "FROZEN", section, f"{row['契约标识']} is not frozen in contract_status")
            self.require(row["事实源SHA256"] == hashes[row["事实源"]], section, f"{row['契约标识']} has stale source hash")
            self.require(row["operationId"] not in opid_to_status, section, f"duplicate status operationId {row['operationId']}")
            opid_to_status[row["operationId"]] = row
            op = operations.get(row["契约标识"], {})
            self.require(op.get("operationId") == row["operationId"], section, f"{row['契约标识']} operationId differs from status registry")
            request_schema = row["请求Schema"]
            response_schema = row["响应Schema"]
            source_doc = load_yaml(row["事实源"])
            schemas = source_doc.get("components", {}).get("schemas", {})
            self.require(request_schema in schemas, section, f"{row['契约标识']} missing request schema {request_schema}")
            self.require(response_schema in schemas, section, f"{row['契约标识']} missing response schema {response_schema}")
            self.require(op.get("x-request-schema") == f"#/components/schemas/{request_schema}", section, f"{row['契约标识']} request schema extension differs from status registry")
            method = row["契约标识"].split(" ", 1)[0]
            if method in {"GET", "DELETE"}:
                self.require(request_schema.endswith("Parameters"), section, f"{row['契约标识']} must register a Parameters schema")
                self.require("requestBody" not in op, section, f"{row['契约标识']} unexpectedly has a request body")
            else:
                self.require(request_schema.endswith("Request"), section, f"{row['契约标识']} must register a Request schema")
        self.ok_metric("rest_operations", len(operations))
        self.ok_metric("openapi_schemas", {
            "client": len(load_yaml("contracts/openapi.yaml")["components"]["schemas"]),
            "admin": len(load_yaml("contracts/admin-openapi.yaml")["components"]["schemas"]),
        })
        return operations

    def check_websocket(self) -> None:
        section = "websocket"
        document = load_yaml("contracts/websocket-events.yaml")
        self.require(str(document.get("version")) == "1.2.2", section, "WebSocket version drift")
        self.require(document.get("contract_maturity") == "FROZEN", section, "WebSocket root contract not frozen")
        self.require(str(document.get("endpoint", "")).startswith("wss://"), section, "WebSocket endpoint must use wss")
        authentication = document.get("authentication", {})
        self.require(authentication.get("ticket_ttl_seconds", 999) <= 60, section, "WebSocket ticket TTL must be <= 60 seconds")
        self.require(authentication.get("token_in_url_logging_forbidden") is True, section, "WebSocket URL token logging must be forbidden")
        delivery = document.get("delivery", {})
        for key in ["client_message_id_unique_scope", "server_sequence_scope", "ack_timeout_seconds", "max_redeliveries", "resume", "retention_hours", "ordering"]:
            self.require(key in delivery, section, f"WebSocket delivery rule missing: {key}")
        events = document.get("events", [])
        self.require(len(events) == 10, section, f"expected 10 WebSocket events, got {len(events)}")
        codes = [event.get("code") for event in events]
        self.require(len(set(codes)) == len(codes), section, "duplicate WebSocket event code")
        for event in events:
            code = event.get("code")
            self.require(event.get("contract_maturity") == "FROZEN", section, f"{code} is not frozen")
            self.require(event.get("direction") in {"C2S", "S2C", "BIDIRECTIONAL"}, section, f"{code} invalid direction")
            payload = event.get("payload", {})
            self.require(payload.get("type") == "object", section, f"{code} payload must be an object")
            properties = payload.get("properties", {})
            required = payload.get("required", [])
            self.require(set(required) <= set(properties), section, f"{code} required payload properties missing")
            if "serverSequence" in properties:
                self.require(properties["serverSequence"].get("type") == "integer", section, f"{code}.serverSequence must be integer")
        statuses = [row for row in read_csv("contracts/contract_status.csv") if row["契约类型"] == "WEBSOCKET_EVENT"]
        self.require(len(statuses) == 10, section, f"contract_status must contain 10 WebSocket rows, got {len(statuses)}")
        self.require({row["契约标识"] for row in statuses} == set(codes), section, "WebSocket status registry mismatch")
        source_hash = sha256(ROOT / "contracts/websocket-events.yaml")
        for row in statuses:
            self.require(row["成熟度"] == "FROZEN", section, f"{row['契约标识']} status not frozen")
            self.require(row["事实源SHA256"] == source_hash, section, f"{row['契约标识']} stale WebSocket source hash")
        self.ok_metric("websocket_events", len(events))

    def check_states(self) -> None:
        section = "state-machines"
        enum_doc = load_yaml("database/enum_registry.yaml")
        state_doc = load_yaml("database/state_machines.yaml")
        self.require(str(enum_doc.get("version")) == "1.2.2", section, "enum version drift")
        self.require(str(state_doc.get("version")) == "1.2.2", section, "state machine version drift")
        enums: dict[str, set[str]] = {}
        for item in enum_doc.get("enums", []):
            code = item.get("code")
            values = item.get("values", [])
            self.require(code not in enums, section, f"duplicate enum {code}")
            self.require(len(values) == len(set(values)), section, f"enum {code} has duplicate values")
            self.require(all(re.fullmatch(r"[A-Z][A-Z0-9_]*", value or "") for value in values), section, f"enum {code} contains non-machine code")
            enums[code] = set(values)
        machines = state_doc.get("machines", [])
        seen_machine: set[str] = set()
        transition_count = 0
        for machine in machines:
            code = machine.get("code")
            self.require(code in enums, section, f"state machine {code} has no matching enum")
            self.require(code not in seen_machine, section, f"duplicate state machine {code}")
            seen_machine.add(code)
            values = enums.get(code, set())
            self.require(machine.get("initial") in values, section, f"{code} initial state not in enum")
            self.require(set(machine.get("terminal", [])) <= values, section, f"{code} terminal state outside enum")
            signatures: set[tuple[str, str, str]] = set()
            for transition in machine.get("transitions", []):
                transition_count += 1
                source = transition.get("from")
                target = transition.get("to")
                event = transition.get("event")
                self.require(source == "*" or source in values, section, f"{code}: invalid source state {source}")
                self.require(target in values, section, f"{code}: invalid target state {target}")
                self.require(bool(event and re.fullmatch(r"[A-Z][A-Z0-9_]*", event)), section, f"{code}: invalid event {event}")
                signature = (str(source), str(target), str(event))
                self.require(signature not in signatures, section, f"{code}: duplicate transition {signature}")
                signatures.add(signature)
                self.require(bool(transition.get("actor")), section, f"{code}/{event}: actor missing")
                self.require(bool(transition.get("guard")), section, f"{code}/{event}: guard missing")
                self.require(bool(transition.get("side_effects")), section, f"{code}/{event}: side effects missing")
                self.require(bool(transition.get("idempotency")), section, f"{code}/{event}: idempotency rule missing")
        self.require({"CONTENT_STATUS", "RED_PACKET_STATUS", "ORDER_STATUS", "REWARD_STATUS", "WITHDRAWAL_STATUS", "IDENTITY_STATUS"} <= seen_machine, section, "critical state machines are missing")
        self.ok_metric("enums", len(enums))
        self.ok_metric("state_machines", len(machines))
        self.ok_metric("state_transitions", transition_count)

    def check_database(self) -> None:
        section = "database"
        table_rows = read_csv("catalogs/data_tables.csv")
        dictionary = read_csv("database/schema_dictionary.csv")
        trace = read_csv("database/schema_traceability.csv")
        catalog_tables = {row["表名"] for row in table_rows}
        dictionary_tables = {row["表名"] for row in dictionary}
        trace_tables = {row["表名"] for row in trace}
        self.require(len(catalog_tables) == 188, section, "table catalog must have 188 unique tables")
        self.require(catalog_tables == dictionary_tables == trace_tables, section, "table catalog, dictionary and traceability differ")
        by_table: dict[str, list[dict[str, str]]] = defaultdict(list)
        for row in dictionary:
            by_table[row["表名"]].append(row)
            self.require(row["成熟度"] == "FROZEN", section, f"{row['表名']}.{row['字段名']} is not FROZEN")
        for table, fields in by_table.items():
            names = [field["字段名"] for field in fields]
            self.require("id" in names, section, f"{table} has no id primary key")
            self.require(len(names) == len(set(names)), section, f"{table} has duplicate field names")
            id_row = next((field for field in fields if field["字段名"] == "id"), None)
            self.require(bool(id_row and "PRIMARY KEY" in id_row["索引建议"]), section, f"{table}.id is not marked as primary key")

        migration_paths = sorted((ROOT / "database/migrations").glob("V*.sql"))
        self.require(len(migration_paths) == 8, section, f"expected 8 Flyway migrations, got {len(migration_paths)}")
        expected_names = [f"V{i:03d}" for i in range(1, 9)]
        self.require([path.name[:4] for path in migration_paths] == expected_names, section, "Flyway migration sequence must be V001..V008")
        all_sql = "\n".join(path.read_text(encoding="utf-8") for path in migration_paths)
        for path in migration_paths:
            try:
                parse_sql(path.read_text(encoding="utf-8"))
            except Exception as exc:  # pragma: no cover - diagnostic
                self.error(section, f"{path.relative_to(ROOT)} does not parse as PostgreSQL: {exc}")
        created = set(re.findall(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?hhy\.([a-z0-9_]+)", all_sql, flags=re.IGNORECASE))
        self.require(created == catalog_tables, section, f"migration tables differ: missing={sorted(catalog_tables-created)[:10]}, extra={sorted(created-catalog_tables)[:10]}")
        unique_count = len(re.findall(r"\bUNIQUE\b", all_sql, flags=re.IGNORECASE))
        fk_count = len(re.findall(r"\bFOREIGN\s+KEY\b", all_sql, flags=re.IGNORECASE))
        index_count = len(re.findall(r"\bCREATE\s+(?:UNIQUE\s+)?INDEX\b", all_sql, flags=re.IGNORECASE))
        self.require(unique_count >= 70, section, f"expected >=70 unique constraints, got {unique_count}")
        self.require(fk_count >= 150, section, f"expected >=150 foreign keys, got {fk_count}")
        self.require(index_count >= 550, section, f"expected >=550 explicit indexes, got {index_count}")
        lower_sql = all_sql.lower()
        for required in [
            "outbox_events", "inbox_messages", "ledger_accounts", "accounting_transactions",
            "accounting_entries", "balance_snapshots", "reconciliation_runs", "reconciliation_differences",
        ]:
            self.require(required in created, section, f"required platform table missing: {required}")
        for invariant in [
            "trg_accounting_transactions_balance", "prevent_immutable_mutation",
            "ck_red_packet_stock_capacity", "trg_accounting_entries_immutable",
        ]:
            self.require(invariant.lower() in lower_sql, section, f"database invariant missing: {invariant}")
        self.require(not re.search(r"INSERT\s+INTO\s+hhy\.admin_users", all_sql, flags=re.IGNORECASE), section, "migrations must not create a default administrator")

        backend_migrations = ROOT / "services/backend/boot/src/main/resources/db/migration"
        for path in migration_paths:
            copy = backend_migrations / path.name
            self.require(copy.is_file(), section, f"backend migration copy missing: {path.name}")
            if copy.exists():
                self.require(path.read_bytes() == copy.read_bytes(), section, f"backend migration copy drift: {path.name}")
        trace_by_table = {row["表名"]: row for row in trace}
        for table, row in trace_by_table.items():
            migration = ROOT / row["迁移文件"]
            self.require(migration.is_file(), section, f"{table} references missing migration {row['迁移文件']}")
            self.require(row["成熟度"] == "FROZEN", section, f"{table} traceability not frozen")
            self.require(int(row["字段数"]) == len(by_table[table]), section, f"{table} field count mismatch")
        self.ok_metric("database_tables", len(created))
        self.ok_metric("database_fields", len(dictionary))
        self.ok_metric("database_constraints", {"unique": unique_count, "foreign_keys": fk_count, "indexes": index_count})
        self.ok_metric("flyway_migrations", len(migration_paths))

    def check_ui(self, operations: dict[str, dict[str, Any]]) -> None:
        section = "ui-mapping"
        android = read_csv("catalogs/android_screens.csv")
        h5 = read_csv("catalogs/h5_screens.csv")
        admin = read_csv("catalogs/admin_pages.csv")
        ui_rows = android + h5 + admin
        ui_ids = {row["ID"] for row in ui_rows}
        self.require(len(ui_ids) == 169, section, "expected 169 unique UI IDs")
        route_segment = re.compile(r"(?:[a-z0-9][a-z0-9-]*|\{[A-Za-z][A-Za-z0-9_-]*\??\}|:[A-Za-z][A-Za-z0-9_-]*)")
        native_sheet_route = re.compile(r"^sheet://[a-z0-9][a-z0-9-]*$")
        for name, rows in [("Android", android), ("H5", h5), ("Admin", admin)]:
            routes = [row["路由"] for row in rows]
            self.require(len(routes) == len(set(routes)), section, f"{name} routes are not unique")
            for row in rows:
                route = row["路由"]
                if name == "Android" and native_sheet_route.fullmatch(route):
                    continue
                self.require(route.startswith("/"), section, f"{name}/{row['ID']} route must start with '/': {route}")
                self.require("//" not in route, section, f"{name}/{row['ID']} route contains double slash: {route}")
                self.require(not re.search(r"\s", route), section, f"{name}/{row['ID']} route contains whitespace: {route}")
                self.require(route == "/" or not route.endswith("/"), section, f"{name}/{row['ID']} route must not end with '/': {route}")
                invalid_segments = [segment for segment in route.split("/")[1:] if segment and not route_segment.fullmatch(segment)]
                self.require(not invalid_segments, section, f"{name}/{row['ID']} route contains invalid segments {invalid_segments}: {route}")
        permission_pattern = re.compile(r"^[a-z][a-z0-9_-]*(?:\.[a-z][a-z0-9_-]*)+$")
        self.require(all(row.get("成熟度") == "ROUTE_PERMISSION_FROZEN" for row in admin), section, "all admin routes/permissions must be frozen")
        self.require(all(row.get("读取权限") and row.get("写入权限") for row in admin), section, "admin page permissions cannot be blank")
        for row in admin:
            for field_name in ["读取权限", "写入权限"]:
                permission = row.get(field_name, "")
                self.require(bool(permission_pattern.fullmatch(permission)), section, f"Admin/{row['ID']} invalid {field_name}: {permission}")

        matrix = read_csv("catalogs/frontend_backend_matrix.csv")
        self.require(len(matrix) == len(ui_rows), section, "frontend/backend matrix must have one row per UI")
        self.require({row["UI_ID"] for row in matrix} == ui_ids, section, "frontend/backend matrix UI set mismatch")
        self.require(all(row["绑定成熟度"] == "EXACT_ACTION_BOUND" for row in matrix), section, "all page mappings must be EXACT_ACTION_BOUND")

        requirements = {row["需求ID"] for row in read_csv("catalogs/requirements_catalog.csv")}
        tests = {row["测试ID"] for row in read_csv("catalogs/test_cases.csv")}
        tables = {row["表名"] for row in read_csv("catalogs/data_tables.csv")}
        actions = read_csv("catalogs/ui_action_matrix.csv")
        self.require(len(actions) == 546, section, "expected 546 UI actions")
        action_ids = [row["动作ID"] for row in actions]
        self.require(len(action_ids) == len(set(action_ids)), section, "duplicate UI action IDs")
        operations_by_id = {op.get("operationId"): (key, op) for key, op in operations.items()}
        for row_number, row in enumerate(actions, 2):
            self.require(row["成熟度"] == "FROZEN", section, f"ui_action_matrix:{row_number} not FROZEN")
            self.require(row["UI_ID"] in ui_ids, section, f"ui_action_matrix:{row_number} unknown UI {row['UI_ID']}")
            self.require(row["API契约"] in operations, section, f"ui_action_matrix:{row_number} unknown API {row['API契约']}")
            known = operations_by_id.get(row["operationId"])
            self.require(bool(known), section, f"ui_action_matrix:{row_number} unknown operationId {row['operationId']}")
            if known:
                self.require(known[0] == row["API契约"], section, f"ui_action_matrix:{row_number} operationId/API mismatch")
                request_schema = row["请求Schema"]
                status = next((item for item in read_csv("contracts/contract_status.csv") if item["operationId"] == row["operationId"]), None)
                self.require(bool(status and status["请求Schema"] == request_schema), section, f"ui_action_matrix:{row_number} request schema mismatch")
            self.require(set(split_semicolon(row["需求ID"])) <= requirements, section, f"ui_action_matrix:{row_number} unknown requirement")
            self.require(set(split_semicolon(row["测试ID"])) <= tests, section, f"ui_action_matrix:{row_number} unknown test")
            if row["幂等键"] != "不要求":
                self.require(bool(row["幂等键"]), section, f"ui_action_matrix:{row_number} blank idempotency strategy")

        for row_number, row in enumerate(matrix, 2):
            self.require(set(split_semicolon(row["需求ID"])) <= requirements, section, f"frontend_backend_matrix:{row_number} unknown requirement")
            self.require(set(split_semicolon(row["API契约"])) <= set(operations), section, f"frontend_backend_matrix:{row_number} unknown API")
            self.require(set(split_semicolon(row["数据表"])) <= tables, section, f"frontend_backend_matrix:{row_number} unknown table")
            self.require(set(split_semicolon(row["测试ID"])) <= tests, section, f"frontend_backend_matrix:{row_number} unknown test")
            bound_actions = [action for action in actions if action["UI_ID"] == row["UI_ID"]]
            self.require(bool(bound_actions), section, f"{row['UI_ID']} has no action-level binding")
            matrix_apis = set(split_semicolon(row["API契约"]))
            action_apis = {action["API契约"] for action in bound_actions}
            self.require(action_apis <= matrix_apis, section, f"{row['UI_ID']} page matrix misses action APIs {sorted(action_apis-matrix_apis)}")
        self.ok_metric("ui_pages", {"android": len(android), "h5": len(h5), "admin": len(admin), "total": len(ui_rows)})
        self.ok_metric("ui_actions", len(actions))

    def check_traceability(self, operations: dict[str, dict[str, Any]]) -> None:
        section = "traceability"
        req_rows = read_csv("catalogs/requirements_catalog.csv")
        trace_rows = read_csv("catalogs/TRACEABILITY_MATRIX.csv")
        req_ids = {row["需求ID"] for row in req_rows}
        self.require({row["需求ID"] for row in trace_rows} == req_ids, section, "traceability requirement set mismatch")
        ui_ids = {row["ID"] for row in read_csv("catalogs/android_screens.csv") + read_csv("catalogs/h5_screens.csv") + read_csv("catalogs/admin_pages.csv")}
        tables = {row["表名"] for row in read_csv("catalogs/data_tables.csv")}
        tests = {row["测试ID"] for row in read_csv("catalogs/test_cases.csv")}
        release_ids = {row["版本"] for row in read_csv("catalogs/release_plan.csv")}
        for row_number, row in enumerate(trace_rows, 2):
            req_id = row["需求ID"]
            self.require(row["状态"] == "FROZEN_READY", section, f"{req_id} traceability is not FROZEN_READY")
            self.require(set(split_semicolon(row["前端页面"])) <= ui_ids, section, f"{req_id} references unknown UI")
            self.require(set(split_semicolon(row["客户端API"])) <= set(operations), section, f"{req_id} references unknown client API")
            self.require(set(split_semicolon(row["后台API"])) <= set(operations), section, f"{req_id} references unknown admin API")
            self.require(set(split_semicolon(row["数据表"])) <= tables, section, f"{req_id} references unknown table")
            self.require(set(split_semicolon(row["测试用例"])) <= tests, section, f"{req_id} references unknown test")
            for task_path in split_semicolon(row["任务清单"]):
                self.require((ROOT / task_path).is_file(), section, f"{req_id} task file missing: {task_path}")
            versions = set(re.findall(r"(?:P|R)\d{2}", row["版本"]))
            self.require(versions <= release_ids, section, f"{req_id} references unknown release")
            if req_id == "REQ-AUTH-005":
                self.require(versions == {"R02"}, section, "REQ-AUTH-005 must not leak into R27")
        test_req_ids = {row["需求ID"] for row in read_csv("catalogs/test_cases.csv") if row["需求ID"]}
        self.require(req_ids <= test_req_ids, section, f"requirements without tests: {sorted(req_ids-test_req_ids)}")
        self.ok_metric("traceable_requirements", len(req_ids))

    def check_config(self) -> None:
        section = "configuration"
        csv_rows = read_csv("catalogs/config_registry.csv")
        yaml_doc = load_yaml("config/CONFIG_REGISTRY.yaml")
        yaml_rows = yaml_doc.get("items", [])
        self.require(len(csv_rows) == len(yaml_rows) == 314, section, "configuration registry must have 314 items in CSV and YAML")
        csv_by_key = {row["key"]: row for row in csv_rows}
        yaml_by_key = {row["key"]: row for row in yaml_rows}
        self.require(set(csv_by_key) == set(yaml_by_key), section, "configuration CSV/YAML key sets differ")
        allowed_lifecycle = {
            "STARTUP_CONFIGURATION",
            "SECRET_REFERENCE",
            "ACTIVATION_GATED_CONFIGURATION",
            "VERSIONED_BUSINESS_CONFIGURATION",
        }
        allowed_reload = {"RESTART_REQUIRED", "ACTIVATE_VERSION", "ATOMIC_HOT_RELOAD"}
        for key, row in csv_by_key.items():
            lifecycle = row["lifecycle"]
            reload_strategy = row["reload_strategy"]
            self.require(lifecycle in allowed_lifecycle, section, f"{key}: invalid lifecycle")
            self.require(reload_strategy in allowed_reload, section, f"{key}: invalid reload strategy")
            self.require(row["required_in"], section, f"{key}: required_in missing")
            self.require(row["validator"], section, f"{key}: validator missing")
            self.require(row["owner"], section, f"{key}: owner missing")
            self.require(row["change_risk"] in {"LOW", "MEDIUM", "HIGH", "CRITICAL"}, section, f"{key}: invalid change risk")
            secret = row["secret"].lower() == "true"
            restart = row["restart_required"].lower() == "true"
            runtime_reload = row["runtime_reload"].lower() == "true"
            if secret:
                self.require(lifecycle == "SECRET_REFERENCE", section, f"{key}: secret lifecycle must be SECRET_REFERENCE")
                self.require(row["secret_ref_policy"] == "VAULT_OR_KMS_REFERENCE_ONLY", section, f"{key}: secret must be reference-only")
                self.require(not row["default"], section, f"{key}: secret default must be blank")
            else:
                self.require(lifecycle != "SECRET_REFERENCE", section, f"{key}: non-secret cannot use SECRET_REFERENCE lifecycle")
                self.require(row["secret_ref_policy"] == "NOT_SECRET", section, f"{key}: non-secret policy drift")
            if lifecycle == "STARTUP_CONFIGURATION":
                self.require(reload_strategy == "RESTART_REQUIRED" and restart and not runtime_reload, section, f"{key}: startup configuration must require restart")
            elif lifecycle in {"SECRET_REFERENCE", "ACTIVATION_GATED_CONFIGURATION"}:
                self.require(reload_strategy == "ACTIVATE_VERSION" and not restart and not runtime_reload, section, f"{key}: activation-gated configuration strategy drift")
            elif lifecycle == "VERSIONED_BUSINESS_CONFIGURATION":
                self.require(reload_strategy == "ATOMIC_HOT_RELOAD" and not restart and runtime_reload, section, f"{key}: versioned business configuration must hot reload atomically")
            if row["required"].lower() == "true":
                self.require(bool(row["required_in"]), section, f"{key}: required configuration must declare required_in")
        for key in csv_by_key:
            csv_normalized = {k: str(v) for k, v in csv_by_key[key].items() if k != "enum"}
            yaml_normalized = {k: str(v) for k, v in yaml_by_key[key].items() if k != "enum"}
            for field_name in ["group", "type", "default", "secret", "required", "lifecycle", "required_in", "reload_strategy", "restart_required", "secret_ref_policy", "owner", "change_risk"]:
                self.require(csv_normalized[field_name].lower() == yaml_normalized[field_name].lower(), section, f"{key}: CSV/YAML mismatch in {field_name}")
        rules = yaml_doc.get("rules", {})
        self.require(rules.get("secret_storage") == "VAULT_OR_KMS_REFERENCE_ONLY", section, "secret storage policy drift")
        self.require(rules.get("secrets_never_returned_after_save") is True, section, "saved secrets must never be returned")
        self.ok_metric("config_items", len(csv_rows))
        self.ok_metric("secret_config_items", sum(row["secret"].lower() == "true" for row in csv_rows))

    def check_development_risks(self) -> None:
        section = "development-risks"
        rows = read_csv("catalogs/development_risk_register.csv")
        self.require(len(rows) >= 12, section, f"development risk register is unexpectedly small: {len(rows)}")
        ids = [row["risk_id"] for row in rows]
        self.require(len(ids) == len(set(ids)), section, "duplicate risk IDs")
        allowed_status = {"EXTERNAL_GATE", "DEVELOPMENT_GATE", "MONITOR", "CLOSED"}
        allowed_severity = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
        required_fields = ["category", "title", "affected_release", "current_evidence", "mitigation", "owner", "trigger", "acceptance_evidence"]
        for row_number, row in enumerate(rows, 2):
            risk_id = row.get("risk_id", "")
            self.require(bool(re.fullmatch(r"[A-Z][A-Z0-9_-]+", risk_id)), section, f"row {row_number}: invalid risk ID {risk_id}")
            self.require(row.get("status") in allowed_status, section, f"{risk_id}: invalid status")
            self.require(row.get("severity") in allowed_severity, section, f"{risk_id}: invalid severity")
            for field_name in required_fields:
                self.require(bool(row.get(field_name)), section, f"{risk_id}: {field_name} is required")
            self.require(row.get("blocks_current_development") in {"true", "false"}, section, f"{risk_id}: invalid current blocker flag")
        hard_blockers = [row["risk_id"] for row in rows if row["blocks_current_development"] == "true" and row["status"] != "CLOSED"]
        self.require(not hard_blockers, section, f"current baseline has unresolved hard blockers: {hard_blockers}")
        self.ok_metric("development_risks", {
            "total": len(rows),
            "external_gates": sum(row["status"] == "EXTERNAL_GATE" for row in rows),
            "development_gates": sum(row["status"] == "DEVELOPMENT_GATE" for row in rows),
            "monitors": sum(row["status"] == "MONITOR" for row in rows),
            "current_hard_blockers": len(hard_blockers),
        })

    def check_releases(self, operations: dict[str, dict[str, Any]]) -> None:
        section = "releases"
        plan = read_csv("catalogs/release_plan.csv")
        release_ids = [row["版本"] for row in plan]
        expected_ids = ["P00"] + [f"R{i:02d}" for i in range(1, 33)]
        self.require(release_ids == expected_ids, section, "release plan must be P00 then R01..R32")
        dependency_doc = load_yaml("releases/RELEASE_DEPENDENCIES.yaml")
        dependencies: dict[str, list[str]] = dependency_doc.get("dependencies", {})
        self.require(set(dependencies) == set(expected_ids), section, "release dependency key set mismatch")
        self.require(set(dependency_doc.get("milestones", {})) == set(expected_ids), section, "release milestone key set mismatch")
        for release, deps in dependencies.items():
            self.require(set(deps) <= set(expected_ids), section, f"{release} has unknown dependency")
            self.require(release not in deps, section, f"{release} depends on itself")
        indegree = {release: 0 for release in expected_ids}
        outgoing: dict[str, list[str]] = defaultdict(list)
        for release, deps in dependencies.items():
            for dep in deps:
                outgoing[dep].append(release)
                indegree[release] += 1
        queue = deque(sorted([release for release, degree in indegree.items() if degree == 0]))
        visited: list[str] = []
        while queue:
            current = queue.popleft()
            visited.append(current)
            for target in outgoing[current]:
                indegree[target] -= 1
                if indegree[target] == 0:
                    queue.append(target)
        self.require(len(visited) == len(expected_ids), section, "release dependency graph contains a cycle")

        req_ids = {row["需求ID"] for row in read_csv("catalogs/requirements_catalog.csv")}
        ui_ids = {row["ID"] for row in read_csv("catalogs/android_screens.csv") + read_csv("catalogs/h5_screens.csv") + read_csv("catalogs/admin_pages.csv")}
        tables = {row["表名"] for row in read_csv("catalogs/data_tables.csv")}
        tests = {row["测试ID"] for row in read_csv("catalogs/test_cases.csv")}
        selected = [self.release] if self.release else expected_ids
        for release in selected:
            if release not in expected_ids:
                self.error(section, f"unknown --release {release}")
                continue
            directory = ROOT / "releases" / release
            manifest_path = directory / "RELEASE_MANIFEST.yaml"
            tasks_path = directory / "TASKS.yaml"
            acceptance_path = directory / "ACCEPTANCE_MATRIX.csv"
            for path in [manifest_path, tasks_path, acceptance_path]:
                self.require(path.is_file(), section, f"missing release artifact {path.relative_to(ROOT)}")
            if not manifest_path.exists() or not tasks_path.exists() or not acceptance_path.exists():
                continue
            manifest = yaml.safe_load(manifest_path.read_text(encoding="utf-8"))
            tasks = yaml.safe_load(tasks_path.read_text(encoding="utf-8"))
            acceptance = read_csv(str(acceptance_path.relative_to(ROOT)))
            self.require(manifest.get("release") == release, section, f"{release} manifest identity drift")
            self.require(set(manifest.get("depends_on", [])) == set(dependencies[release]), section, f"{release} manifest dependencies differ")
            self.require(set(manifest.get("requirements", [])) <= req_ids, section, f"{release} unknown requirement")
            all_ui = set(manifest.get("ui", {}).get("android", [])) | set(manifest.get("ui", {}).get("h5", [])) | set(manifest.get("ui", {}).get("admin", []))
            self.require(all_ui <= ui_ids, section, f"{release} unknown UI")
            client_api = set(manifest.get("contracts", {}).get("client_api", []))
            admin_api = set(manifest.get("contracts", {}).get("admin_api", []))
            self.require(client_api | admin_api <= set(operations), section, f"{release} unknown REST contract")
            self.require(set(manifest.get("database_tables", [])) <= tables, section, f"{release} unknown table")
            self.require(set(manifest.get("tests", [])) <= tests, section, f"{release} unknown test")
            task_items = tasks.get("tasks", [])
            self.require(len(task_items) == 8, section, f"{release} must contain 8 concrete tasks")
            task_ids = [item.get("id") for item in task_items]
            self.require(len(task_ids) == len(set(task_ids)), section, f"{release} duplicate task IDs")
            for item in task_items:
                self.require(item.get("requirements"), section, f"{item.get('id')} has no requirement linkage")
                self.require(item.get("deliverables"), section, f"{item.get('id')} has no deliverables")
                self.require(item.get("acceptance"), section, f"{item.get('id')} has no acceptance")
                self.require(item.get("session_log_required") is True, section, f"{item.get('id')} must require session log")
                self.require(set(item.get("depends_on", [])) <= set(task_ids), section, f"{item.get('id')} unknown task dependency")
            self.require(len(acceptance) == 6, section, f"{release} acceptance matrix must have 6 rows")
            self.require(all(row["阻断级别"] == "BLOCKING" for row in acceptance), section, f"{release} acceptance gates must be blocking")
            if release == "R27":
                self.require("REQ-AUTH-005" not in manifest.get("requirements", []), section, "R27 must not include REQ-AUTH-005")
                self.require("H5-013" not in all_ui, section, "R27 must not include H5 invitation registration")
        self.ok_metric("release_stages", len(expected_ids))

    def check_scaffolds(self) -> None:
        section = "scaffolds"
        # Backend Maven modular monolith.
        backend = ROOT / "services/backend"
        pom_text = (backend / "pom.xml").read_text(encoding="utf-8") if (backend / "pom.xml").exists() else ""
        modules = re.findall(r"<module>([^<]+)</module>", pom_text)
        expected_modules = ["shared-kernel", "access", "content", "commerce", "incentive", "platform", "boot"]
        self.require(modules == expected_modules, section, f"backend modules must be {expected_modules}, got {modules}")
        for module in expected_modules:
            self.require((backend / module / "pom.xml").is_file(), section, f"backend module missing pom: {module}")
        wrapper_props = (backend / ".mvn/wrapper/maven-wrapper.properties").read_text(encoding="utf-8")
        self.require("repo.maven.apache.org" in wrapper_props and "apache-maven-3.9.11-bin.zip" in wrapper_props, section, "Maven Wrapper must use official Maven 3.9.11 distribution")
        self.require(not list(backend.rglob("*.gradle.kts.reference")), section, "stale Gradle reference files are forbidden in Maven backend")
        for required_test in [
            "services/backend/boot/src/test/java/cc/orbexa/hhy/ModuleBoundaryTest.java",
            "services/backend/boot/src/test/java/cc/orbexa/hhy/PublicEndpointsTest.java",
        ]:
            self.require((ROOT / required_test).is_file(), section, f"backend quality test missing: {required_test}")
        for contract in ["openapi.yaml", "admin-openapi.yaml", "websocket-events.yaml", "error-codes.csv"]:
            source = ROOT / "contracts" / contract
            copy = backend / "boot/src/main/resources/contracts" / contract
            self.require(copy.is_file(), section, f"backend contract copy missing: {contract}")
            if copy.exists():
                self.require(source.read_bytes() == copy.read_bytes(), section, f"backend contract copy drift: {contract}")

        # Web workspace and generated artifacts.
        package = json.loads((ROOT / "package.json").read_text(encoding="utf-8"))
        self.require(package.get("packageManager") == "pnpm@11.13.1", section, "pnpm version drift")
        self.require(package.get("devDependencies", {}).get("typescript") == "5.9.3", section, "TypeScript version drift")
        self.require(package.get("devDependencies", {}).get("openapi-typescript") == "7.13.0", section, "openapi-typescript version drift")
        admin_json = json.loads((ROOT / "apps/admin-web/src/generated/admin-pages.json").read_text(encoding="utf-8"))
        h5_json = json.loads((ROOT / "apps/h5/src/generated/h5-pages.json").read_text(encoding="utf-8"))
        self.require(len(admin_json) == 60, section, "generated admin page catalog must contain 60 pages")
        self.require(len(h5_json) == 13, section, "generated H5 page catalog must contain 13 pages")
        self.require({row["ID"] for row in admin_json} == {row["ID"] for row in read_csv("catalogs/admin_pages.csv")}, section, "generated admin page catalog is stale")
        self.require({row["ID"] for row in h5_json} == {row["ID"] for row in read_csv("catalogs/h5_screens.csv")}, section, "generated H5 page catalog is stale")
        generated_client = (ROOT / "packages/api-client/src/client.generated.ts").read_text(encoding="utf-8")
        generated_admin = (ROOT / "packages/api-client/src/admin.generated.ts").read_text(encoding="utf-8")
        self.require("export interface paths" in generated_client and "export interface components" in generated_client, section, "client OpenAPI TypeScript output missing")
        self.require("export interface paths" in generated_admin and "export interface components" in generated_admin, section, "admin OpenAPI TypeScript output missing")

        # Android multi-module scaffold and catalog/token sync.
        android = ROOT / "apps/android"
        settings = (android / "settings.gradle.kts").read_text(encoding="utf-8")
        for module in [":app", ":core:designsystem", ":core:network", ":feature:shell"]:
            self.require(f'include("{module}")' in settings, section, f"Android module missing: {module}")
        version_catalog = (android / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        for pin in ['agp = "9.2.1"', 'kotlin = "2.3.21"', 'compose-bom = "2026.06.01"']:
            self.require(pin in version_catalog, section, f"Android version pin missing: {pin}")
        app_gradle = (android / "app/build.gradle.kts").read_text(encoding="utf-8")
        for pin in ["compileSdk = 37", "targetSdk = 36", "minSdk = 26", 'versionName = "1.2.2"']:
            self.require(pin in app_gradle, section, f"Android app build pin missing: {pin}")
        gradle_wrapper = (android / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
        self.require("gradle-9.4.1-bin.zip" in gradle_wrapper and "services.gradle.org" in gradle_wrapper, section, "Android Gradle wrapper must be official 9.4.1")
        try:
            with zipfile.ZipFile(android / "gradle/wrapper/gradle-wrapper.jar") as archive:
                names = set(archive.namelist())
                self.require(any(name.endswith("GradleWrapperMain.class") for name in names), section, "Gradle wrapper jar lacks GradleWrapperMain")
        except zipfile.BadZipFile:
            self.error(section, "Android gradle-wrapper.jar is corrupt")
        screens = json.loads((android / "app/src/main/assets/android-screens.v1.2.2.json").read_text(encoding="utf-8"))
        self.require(screens.get("count") == 96 and len(screens.get("screens", [])) == 96, section, "Android generated screen asset must contain 96 screens")
        root_token = ROOT / "design/tokens/hhy_design_tokens_v1.2.2.json"
        android_token = android / "core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json"
        self.require(root_token.read_bytes() == android_token.read_bytes(), section, "Android design token copy drift")
        gradle_sources = "\n".join(path.read_text(encoding="utf-8") for path in android.rglob("*.gradle.kts"))
        self.require(not re.search(r"\bprojects\.", gradle_sources), section, "Android uses disabled typesafe project accessors")
        page_sources = "\n".join(path.read_text(encoding="utf-8") for base in [android / "app/src/main", android / "feature"] for path in base.rglob("*.kt"))
        self.require(not re.search(r"\b\d+(?:\.\d+)?\.(?:dp|sp)\b", page_sources), section, "Android page code contains raw dp/sp instead of tokens")
        self.require(not re.search(r"\bColor\(", page_sources), section, "Android page code contains raw Color values instead of tokens")
        self.require((android / "app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt").is_file(), section, "Android release policy test missing")
        self.require((android / "core/network/src/test/java/cc/orbexa/hhy/network/RequestIdPolicyTest.kt").is_file(), section, "Android request ID policy test missing")

        # CI command alignment.
        ci = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        for command in [
            "python scripts/project-doctor.py --strict",
            "pnpm generate && pnpm typecheck && pnpm test && pnpm build",
            "./mvnw clean verify",
            "scripts/run_postgres_migration_smoke.sh",
            "lintDebug testDebugUnitTest assembleDebug",
            "platforms;android-37",
        ]:
            self.require(command in ci, section, f"CI is missing quality command: {command}")
        self.ok_metric("backend_modules", len(expected_modules))
        self.ok_metric("frontend_apps", 2)
        self.ok_metric("android_modules", 4)

    def check_security_and_portability(self) -> None:
        section = "security-portability"
        forbidden_patterns = {
            "hardcoded /mnt/data path": re.compile(r"/mnt/data/"),
            "internal CAAS marker": re.compile(r"applied-caas|caas\.internal", re.IGNORECASE),
            "internal OpenAI host": re.compile(r"(?:artifactory|packages)\.[a-z0-9.-]*openai\.(?:org|com)", re.IGNORECASE),
            "private key material": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        }
        for path in text_files(ROOT):
            relative = path.relative_to(ROOT)
            if relative in {Path("MANIFEST_SHA256.txt"), Path("scripts/project-doctor.py")}:
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            for label, pattern in forbidden_patterns.items():
                if pattern.search(text):
                    self.error(section, f"{relative}: {label}")
        self.require(not (ROOT / ".env").exists(), section, "runtime .env must not be packaged")
        compose = load_yaml("infra/docker-compose.yml")
        images = [service.get("image", "") for service in compose.get("services", {}).values()]
        self.require(all(image and not image.endswith(":latest") for image in images), section, f"all infrastructure images must be pinned: {images}")
        self.require(any(image.startswith("postgres:17.10") for image in images), section, "PostgreSQL image must be pinned to 17.10")
        self.require(any(image.startswith("redis:7.4.9") for image in images), section, "Redis image must be pinned to 7.4.9")
        self.require(any(image.startswith("rabbitmq:4.1.8") for image in images), section, "RabbitMQ image must be pinned to 4.1.8")
        self.require(any(image.startswith("minio/minio:RELEASE.2025-09-07") for image in images), section, "MinIO image must be pinned to the selected release")
        if self.strict and not self.allow_build_artifacts:
            artifact_patterns = [
                "node_modules",
                "dist",
                "target",
                ".gradle",
                "build",
                "__pycache__",
                "*.pyc",
            ]
            found: list[str] = []
            for pattern in artifact_patterns:
                for path in ROOT.rglob(pattern):
                    if any(part == "99-archive" for part in path.parts):
                        continue
                    found.append(str(path.relative_to(ROOT)))
                    if len(found) >= 30:
                        break
                if len(found) >= 30:
                    break
            self.require(not found, section, f"build/cache artifacts must be removed from package: {found[:30]}")
        self.ok_metric("infra_images", images)

    def check_integrity_manifest(self) -> None:
        section = "package-integrity"
        manifest_path = ROOT / "MANIFEST_SHA256.txt"
        self.require(manifest_path.is_file(), section, "MANIFEST_SHA256.txt missing")
        if not manifest_path.exists():
            return
        listed: dict[str, str] = {}
        for line_number, line in enumerate(manifest_path.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
            if not match:
                self.error(section, f"MANIFEST_SHA256.txt:{line_number} invalid format")
                continue
            digest, relative = match.groups()
            self.require(relative not in listed, section, f"duplicate integrity entry: {relative}")
            listed[relative] = digest
        actual_files = {
            str(path.relative_to(ROOT))
            for path in ROOT.rglob("*")
            if path.is_file()
            and path != manifest_path
            and not any(part in {"node_modules", "dist", "target", "build", ".gradle", "__pycache__"} for part in path.relative_to(ROOT).parts)
            and path.suffix != ".pyc"
        }
        self.require(set(listed) == actual_files, section, f"integrity file set mismatch: missing={sorted(actual_files-set(listed))[:10]}, stale={sorted(set(listed)-actual_files)[:10]}")
        for relative in sorted(actual_files & set(listed)):
            self.require(sha256(ROOT / relative) == listed[relative], section, f"checksum mismatch: {relative}")
        self.ok_metric("integrity_files", len(actual_files))

    def report(self) -> dict[str, Any]:
        errors = [finding for finding in self.findings if finding.level == "ERROR"]
        warnings = [finding for finding in self.findings if finding.level == "WARNING"]
        return {
            "project": "合伙云 Pro",
            "version": "1.2.2",
            "status": "PASS" if not errors else "FAIL",
            "strict": self.strict,
            "release_scope": self.release or "ALL",
            "metrics": self.metrics,
            "summary": {"errors": len(errors), "warnings": len(warnings), "checks_with_metrics": len(self.metrics)},
            "findings": [finding.__dict__ for finding in self.findings],
        }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true", help="enable packaging cleanliness checks")
    parser.add_argument("--allow-build-artifacts", action="store_true", help="allow local build outputs during an intermediate validation run")
    parser.add_argument("--verify-package-integrity", action="store_true", help="verify MANIFEST_SHA256.txt against the clean package")
    parser.add_argument("--release", help="validate release-specific artifacts for one stage, e.g. P00")
    parser.add_argument("--json-report", help="write machine-readable report to this path")
    args = parser.parse_args()

    doctor = Doctor(
        strict=args.strict,
        allow_build_artifacts=args.allow_build_artifacts,
        verify_package_integrity=args.verify_package_integrity,
        release=args.release,
    )
    doctor.run()
    report = doctor.report()
    if args.json_report:
        output = Path(args.json_report)
        if not output.is_absolute():
            output = ROOT / output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")

    print(f"PROJECT_DOCTOR {report['status']} version={report['version']} release={report['release_scope']}")
    for key, value in sorted(report["metrics"].items()):
        print(f"  {key}: {json.dumps(value, ensure_ascii=False, sort_keys=True)}")
    if doctor.findings:
        print("findings:")
        for finding in doctor.findings:
            print(f"  [{finding.level}] {finding.section}: {finding.message}")
    print(f"summary: errors={report['summary']['errors']} warnings={report['summary']['warnings']}")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
