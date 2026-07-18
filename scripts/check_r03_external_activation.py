#!/usr/bin/env python3
"""Validate R03 external dependency, domain evidence, and APK release identity."""
from __future__ import annotations

import json
import re
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, code: str) -> None:
    if not condition:
        ERRORS.append(code)


ledger_path = ROOT / "artifacts/validation/r03-task007-external/external-activation-ledger.yaml"
domain_path = ROOT / "artifacts/validation/r03-task007-external/domain-probe.json"
ledger = yaml.safe_load(ledger_path.read_text(encoding="utf-8")) or {}
domains = json.loads(domain_path.read_text(encoding="utf-8"))

require(ledger.get("schema_version") == 1 and ledger.get("release") == "R03", "R03_EXTERNAL_LEDGER_IDENTITY")
providers = {row.get("provider"): row for row in ledger.get("providers") or [] if isinstance(row, dict)}
require(set(providers) == {"SMS", "STORAGE", "IDENTITY", "PAYMENT", "PAYOUT"}, "R03_EXTERNAL_PROVIDER_SET")
for provider, row in providers.items():
    status = row.get("status")
    require(status in {"VERIFIED", "BLOCKED"}, f"R03_EXTERNAL_STATUS_{provider}")
    if status == "VERIFIED":
        require(row.get("connection_test") == "VERIFIED", f"R03_EXTERNAL_CONNECTION_{provider}")
        require(row.get("secret_ref") in {"VERIFIED", "NOT_APPLICABLE"}, f"R03_EXTERNAL_SECRET_REF_{provider}")
    else:
        for field in ("owner", "deadline_release", "blocker", "required_action"):
            require(bool(str(row.get(field) or "").strip()), f"R03_EXTERNAL_BLOCKER_{provider}_{field}")
        require(re.fullmatch(r"R\d{2}", str(row.get("deadline_release") or "")) is not None, f"R03_EXTERNAL_DEADLINE_{provider}")
        require(row.get("connection_test") == "NOT_RUN", f"R03_EXTERNAL_NO_FALSE_TEST_{provider}")

require(domains.get("schema_version") == 1 and domains.get("release") == "R03", "R03_DOMAIN_EVIDENCE_IDENTITY")
rows = {row.get("host"): row for row in domains.get("rows") or [] if isinstance(row, dict)}
expected_hosts = {
    "www.orbexa.cc", "api.orbexa.cc", "ws.orbexa.cc", "admin.orbexa.cc", "h5.orbexa.cc",
    "download.orbexa.cc", "assets.orbexa.cc", "stg-api.orbexa.cc", "stg-ws.orbexa.cc",
    "stg-admin.orbexa.cc", "stg-h5.orbexa.cc", "stg-download.orbexa.cc",
}
require(set(rows) == expected_hosts, "R03_DOMAIN_HOST_SET")
for host, row in rows.items():
    passed = row.get("dns_status") == row.get("tls_status") == row.get("service_health_status") == "PASSED"
    if passed:
        require(re.fullmatch(r"[0-9a-f]{64}", str(row.get("certificate_sha256") or "")) is not None, f"R03_DOMAIN_CERT_{host}")
        require(isinstance(row.get("http_status"), int), f"R03_DOMAIN_HTTP_{host}")
    else:
        for field in ("owner", "deadline_release", "blocker", "error_type"):
            require(bool(str(row.get(field) or "").strip()), f"R03_DOMAIN_BLOCKER_{host}_{field}")
require(all(rows[host]["dns_status"] == "PASSED" for host in ("api.orbexa.cc", "download.orbexa.cc")), "R03_PUBLIC_DOMAIN_REGRESSION")

gradle = (ROOT / "apps/android/app/build.gradle.kts").read_text(encoding="utf-8")
policy = (ROOT / "apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt").read_text(encoding="utf-8")
version_test = (ROOT / "apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt").read_text(encoding="utf-8")
for source, code in ((gradle, "GRADLE"), (policy, "POLICY"), (version_test, "TEST")):
    require("10203" in source, f"R03_ANDROID_VERSION_CODE_{code}")

registry = (ROOT / "docs/03-continuity/PROBLEM_REGISTRY.yaml").read_text(encoding="utf-8")
for problem in ("PROB-0012", "PROB-0030", "PROB-0031"):
    require(problem in registry, f"R03_EXTERNAL_PROBLEM_MISSING_{problem}")

combined = ledger_path.read_text(encoding="utf-8") + domain_path.read_text(encoding="utf-8")
for forbidden in ("BEGIN PRIVATE KEY", "access_key_secret", "store_password", "key_password"):
    require(forbidden.lower() not in combined.lower(), f"R03_EXTERNAL_SECRET_LEAK_{forbidden}")

if ERRORS:
    print("\n".join(ERRORS))
    print("R03_EXTERNAL_ACTIVATION_FAILED", len(ERRORS))
    raise SystemExit(1)
print("R03_EXTERNAL_ACTIVATION_OK")
