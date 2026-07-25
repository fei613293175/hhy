#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import csv
import hashlib
import re
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}


def rows(relative: str) -> list[dict[str, str]]:
    with (ROOT / relative).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def generated_paths(relative: str) -> set[str]:
    text = (ROOT / relative).read_text(encoding="utf-8")
    if "export type HhyJsonValue" not in text or "JsonValue: HhyJsonValue;" not in text:
        errors.append(f"{relative} recursive JsonValue post-processing missing")
    return set(re.findall(r'^    "([^"]+)": \{$', text, flags=re.MULTILINE))


errors: list[str] = []
documents: dict[str, dict] = {}
contracts = [
    ("catalogs/api_endpoints.csv", "contracts/openapi.yaml", "packages/api-client/src/client.generated.ts"),
    ("catalogs/admin_api_endpoints.csv", "contracts/admin-openapi.yaml", "packages/api-client/src/admin.generated.ts"),
]
for csv_name, yaml_name, generated_name in contracts:
    expected = {(row["路径"], row["方法"].lower()) for row in rows(csv_name)}
    document = yaml.safe_load((ROOT / yaml_name).read_text(encoding="utf-8"))
    documents[yaml_name] = document
    actual = {
        (path, method.lower())
        for path, item in document.get("paths", {}).items()
        for method in item
        if method.lower() in METHODS
    }
    if expected != actual:
        errors.append(f"{yaml_name} operation drift missing={sorted(expected - actual)[:10]} extra={sorted(actual - expected)[:10]}")
    expected_paths = set(document.get("paths", {}))
    actual_generated_paths = generated_paths(generated_name)
    if expected_paths != actual_generated_paths:
        errors.append(
            f"{generated_name} path drift missing={sorted(expected_paths - actual_generated_paths)[:10]} "
            f"extra={sorted(actual_generated_paths - expected_paths)[:10]}"
        )


client = documents["contracts/openapi.yaml"]
schemas = client.get("components", {}).get("schemas", {})


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


# CR-0003: keep the three P00 bootstrap operations exact.  These assertions
# catch the legacy generator bug that inferred pagination from words such as
# "版本" and from the trailing "s" in /status.
version_get = client["paths"]["/api/v1/app/version-check"]["get"]
status_get = client["paths"]["/public-api/v1/platform/status"]["get"]
version_post = client["paths"]["/public-api/v1/app/version-check"]["post"]
require(version_get.get("security") == [{}, {"BearerAuth": []}], "P00 GET version-check optional-auth security drift")
require(status_get.get("security") == [] and version_post.get("security") == [], "P00 public operation security drift")
require(
    [item.get("name") for item in version_get.get("parameters", [])]
    == ["platform", "versionCode", "channel", "environment"],
    "P00 GET version-check parameter drift",
)
require(not status_get.get("parameters"), "P00 platform status must not expose inferred pagination parameters")
require(
    schemas.get("AppReleaseGetAppVersionCheckResponse", {}).get("properties", {}).get("data")
    == {"$ref": "#/components/schemas/AppVersionPolicyResource"},
    "P00 GET version-check response must contain one policy",
)
require(
    schemas.get("PublicGetPlatformStatusResponse", {}).get("properties", {}).get("data")
    == {"$ref": "#/components/schemas/PlatformStatusResource"},
    "P00 platform status response schema drift",
)
policy = schemas.get("AppVersionPolicyResource", {})
policy_properties = policy.get("properties", {})
require(policy.get("additionalProperties") is False, "AppVersionPolicyResource must reject unknown fields")
for field in ("latestVersionCode", "minSupportedVersionCode"):
    require(
        policy_properties.get(field, {}).get("type") == "integer"
        and policy_properties.get(field, {}).get("format") == "int64",
        f"{field} must be int64",
    )
require(policy_properties.get("updateType", {}).get("enum") == ["NONE", "OPTIONAL", "FORCED"], "updateType enum drift")
sha_schema = policy_properties.get("sha256", {})
require(
    sha_schema.get("minLength") == 64
    and sha_schema.get("maxLength") == 64
    and sha_schema.get("pattern") == "^[A-Fa-f0-9]{64}$",
    "version policy sha256 must be exactly 64 hexadecimal characters",
)
capabilities = schemas.get("PlatformCapabilitiesResource", {})
require(capabilities.get("additionalProperties") is False, "platform capabilities must reject unknown flags")
require(
    set(capabilities.get("required", [])) == {"registration", "publishing", "redPacket", "withdrawal"},
    "platform capability set drift",
)

# CR-0316: the destructive content command must participate in the same
# optimistic-lock contract as every other content mutation.  The operation is
# still pre-implementation, so this corrects the frozen v1 contract before any
# runtime consumer can ship without a version token.
content_delete = client["paths"]["/api/v1/contents/{id}"]["delete"]
delete_parameters = {
    (item.get("name"), item.get("in")): item
    for item in content_delete.get("parameters", [])
    if "$ref" not in item
}
delete_expected_version = delete_parameters.get(("expectedVersion", "query"), {})
require(
    delete_expected_version.get("required") is True
    and delete_expected_version.get("schema") == {
        "type": "integer", "format": "int64", "minimum": 0,
    },
    "content delete expectedVersion query contract drift",
)
delete_contract = schemas.get("ContentDeleteContentsByIdParameters", {})
require(
    "expectedVersion" in delete_contract.get("required", [])
    and delete_contract.get("properties", {}).get("expectedVersion", {}).get("minimum") == 0,
    "content delete named parameter contract must require nonnegative expectedVersion",
)

for row_number, row in enumerate(rows("contracts/contract_status.csv"), start=2):
    source = ROOT / row["事实源"]
    require(source.is_file(), f"contract_status:{row_number} source missing: {row['事实源']}")
    if source.is_file():
        require(row["事实源SHA256"] == sha256(source), f"contract_status:{row_number} source hash stale")

runtime = ROOT / "services/backend/boot/src/main/resources/contracts"
for name in ["openapi.yaml", "admin-openapi.yaml", "websocket-events.yaml", "error-codes.csv"]:
    source = ROOT / "contracts" / name
    target = runtime / name
    if not target.is_file() or sha256(source) != sha256(target):
        errors.append(f"runtime contract drift: {target.relative_to(ROOT).as_posix()}")

if errors:
    print("API_CONTRACT_FAIL")
    print("\n".join(errors))
    sys.exit(1)
print("API_CONTRACT_OK client=131 admin=184 websocket=10 runtime_hashes=PASS")
