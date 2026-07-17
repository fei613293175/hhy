#!/usr/bin/env python3
"""Fail when checked-in safe generated assets drift from authoritative inputs."""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence


ROOT = Path(__file__).resolve().parents[1]


class GeneratedAssetError(RuntimeError):
    """Raised when a generator cannot be executed reliably."""


@dataclass(frozen=True)
class Comparison:
    expected: Path
    actual: Path
    label: str


def compare_file(expected: Path, actual: Path, label: str) -> str | None:
    if not actual.is_file():
        return f"{label}: checked-in asset missing: {actual}"
    if not expected.is_file():
        return f"{label}: isolated generator did not produce: {expected}"
    expected_text = expected.read_text(encoding="utf-8").replace("\r\n", "\n")
    actual_text = actual.read_text(encoding="utf-8").replace("\r\n", "\n")
    if expected_text != actual_text:
        return f"{label}: generated asset drift: {actual}"
    return None


def _copy(relative: str, destination_root: Path) -> None:
    source = ROOT / relative
    destination = destination_root / relative
    if not source.is_file():
        raise GeneratedAssetError(f"required source missing: {relative}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)


def _run(
    command: Sequence[str],
    cwd: Path,
    label: str,
    *,
    env: Mapping[str, str] | None = None,
    input_text: str | None = None,
) -> None:
    try:
        completed = subprocess.run(
            list(command), cwd=cwd, text=True, encoding="utf-8", errors="replace",
            capture_output=True, check=False, env=dict(env) if env is not None else None,
            input=input_text,
        )
    except OSError as exc:
        raise GeneratedAssetError(f"{label}: cannot execute {command[0]}: {exc}") from exc
    if completed.returncode != 0:
        details = (completed.stderr or completed.stdout).strip()
        raise GeneratedAssetError(f"{label}: exit={completed.returncode}: {details}")


def _pnpm() -> str:
    executable = shutil.which("pnpm") or shutil.which("pnpm.cmd")
    if not executable:
        raise GeneratedAssetError(
            "OpenAPI type drift cannot be checked: pnpm is unavailable; install the pinned workspace toolchain"
        )
    return executable


def _pnpm_environment(pnpm: str) -> dict[str, str]:
    environment = os.environ.copy()
    if shutil.which("node", path=environment.get("PATH")):
        return environment
    pnpm_path = Path(pnpm).resolve()
    candidates = [
        pnpm_path.parents[2] / "node" / "bin",
        Path(sys.executable).resolve().parents[1] / "node" / "bin",
    ]
    for directory in candidates:
        executable = directory / ("node.exe" if os.name == "nt" else "node")
        if executable.is_file():
            environment["PATH"] = str(directory) + os.pathsep + environment.get("PATH", "")
            return environment
    raise GeneratedAssetError(
        "OpenAPI type drift cannot be checked: node is unavailable; install the pinned workspace toolchain"
    )


def _postprocess_openapi(path: Path) -> None:
    recursive = '''        JsonValue: components["schemas"]["JsonScalar"] | components["schemas"]["JsonValue"][] | {
            [key: string]: components["schemas"]["JsonValue"];
        };'''
    replacement = '        JsonValue: HhyJsonValue;'
    alias = 'export type HhyJsonValue = string | number | boolean | null | HhyJsonValue[] | { [key: string]: HhyJsonValue };\n\n'
    source = path.read_text(encoding="utf-8")
    if source.count(recursive) != 1:
        raise GeneratedAssetError(f"{path.name}: expected one recursive JsonValue member")
    source = source.replace(recursive, replacement)
    if alias not in source:
        marker = "export interface paths {"
        if marker not in source:
            raise GeneratedAssetError(f"{path.name}: paths interface marker missing")
        source = source.replace(marker, alias + marker)
    path.write_text(source, encoding="utf-8", newline="\n")


def _check_openapi_types(temp_root: Path) -> list[Comparison]:
    outputs = (
        ("contracts/openapi.yaml", "packages/api-client/src/client.generated.ts"),
        ("contracts/admin-openapi.yaml", "packages/api-client/src/admin.generated.ts"),
    )
    comparisons: list[Comparison] = []
    pnpm = _pnpm()
    environment = _pnpm_environment(pnpm)
    for contract, output in outputs:
        generated = temp_root / output
        generated.parent.mkdir(parents=True, exist_ok=True)
        _run(
            [pnpm, "exec", "openapi-typescript", "-o", str(generated)], ROOT,
            f"openapi-typescript {contract}", env=environment,
            input_text=(ROOT / contract).read_text(encoding="utf-8"),
        )
        _postprocess_openapi(generated)
        comparisons.append(Comparison(generated, ROOT / output, output))
    return comparisons


def _check_frontend_scaffolds(temp_root: Path) -> list[Comparison]:
    inputs = (
        "scripts/generate_scaffolds.py",
        "scripts/generate_frontend_types.py",
        "catalogs/admin_pages.csv",
        "catalogs/h5_screens.csv",
        "catalogs/ui_page_specifications.csv",
        "catalogs/admin_page_operation_specs.csv",
        "database/enum_registry.yaml",
    )
    for relative in inputs:
        _copy(relative, temp_root)
    _run([sys.executable, "scripts/generate_scaffolds.py"], temp_root, "frontend scaffolds")
    outputs = (
        "apps/admin-web/src/generated/admin-pages.json",
        "apps/h5/src/generated/h5-pages.json",
        "packages/domain-types/src/index.ts",
    )
    return [Comparison(temp_root / item, ROOT / item, item) for item in outputs]


def _check_android_assets(temp_root: Path) -> list[Comparison]:
    inputs = [
        "scripts/generate_android_scaffold.py",
        "catalogs/android_screens.csv",
        "catalogs/ui_page_specifications.csv",
        "design/tokens/hhy_design_tokens_v1.2.2.json",
        "apps/android/settings.gradle.kts",
        "apps/android/build.gradle.kts",
        "apps/android/gradlew",
        "apps/android/gradle/wrapper/gradle-wrapper.jar",
        "apps/android/gradle/wrapper/gradle-wrapper.properties",
        "apps/android/app/build.gradle.kts",
        "apps/android/core/designsystem/build.gradle.kts",
        "apps/android/core/network/build.gradle.kts",
        "apps/android/feature/shell/build.gradle.kts",
    ]
    for relative in inputs:
        _copy(relative, temp_root)
    _run([sys.executable, "scripts/generate_android_scaffold.py"], temp_root, "Android catalog assets")
    outputs = (
        "apps/android/app/src/main/assets/android-screens.v1.2.2.json",
        "apps/android/core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json",
    )
    return [Comparison(temp_root / item, ROOT / item, item) for item in outputs]


def _run_read_only_contract_checks() -> None:
    commands = (
        ("contract registry", [sys.executable, "scripts/generate_contracts.py", "--check"]),
        ("runtime assets", [sys.executable, "scripts/sync_runtime_assets.py", "--check"]),
        ("API semantics", [sys.executable, "scripts/check_api_contract.py"]),
    )
    for label, command in commands:
        _run(command, ROOT, label)


def check_generated_assets(*, skip_openapi: bool = False) -> list[str]:
    _run_read_only_contract_checks()
    with tempfile.TemporaryDirectory(prefix="hhy-generated-check-") as directory:
        temp_root = Path(directory)
        comparisons: list[Comparison] = []
        if not skip_openapi:
            comparisons.extend(_check_openapi_types(temp_root / "openapi"))
        comparisons.extend(_check_frontend_scaffolds(temp_root / "frontend"))
        comparisons.extend(_check_android_assets(temp_root / "android"))
        return [
            error for item in comparisons
            if (error := compare_file(item.expected, item.actual, item.label)) is not None
        ]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--skip-openapi", action="store_true",
        help="local diagnostic only; CI/release gates must not use this option",
    )
    args = parser.parse_args(argv)
    try:
        errors = check_generated_assets(skip_openapi=args.skip_openapi)
    except GeneratedAssetError as exc:
        print(f"GENERATED_ASSETS_FAIL {exc}", file=sys.stderr)
        return 2
    if errors:
        print("GENERATED_ASSETS_DRIFT", file=sys.stderr)
        print("\n".join(errors), file=sys.stderr)
        return 1
    payload = {
        "status": "PASS",
        "openapi_types": "SKIPPED" if args.skip_openapi else "VERIFIED",
        "frontend_scaffolds": "VERIFIED",
        "android_assets": "VERIFIED",
        "runtime_contracts": "VERIFIED",
    }
    print("GENERATED_ASSETS_OK " + json.dumps(payload, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
