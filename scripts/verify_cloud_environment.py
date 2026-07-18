#!/usr/bin/env python3
"""Read-only preflight for the existing project cloud environment.

This gate intentionally contains no local SDK bootstrap, image build, or cache
rebuild operation. A failure blocks development and must be reported.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / "config" / "DEVELOPMENT_RUNTIME.yaml"


def load_policy(path: Path = POLICY_PATH) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def run_ssh(ssh_executable: str, alias: str, command: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [ssh_executable, alias, command], text=True, capture_output=True, encoding="utf-8", errors="replace", timeout=30
    )


def check_cloud_environment(*, ssh_executable: str, check_android: bool, declared_disconnected: bool, policy: dict) -> dict:
    environment = policy["cloud_environment"]
    if declared_disconnected:
        return {
            "status": "BLOCKED",
            "reason": "USER_DECLARED_CLOUD_DISCONNECTED",
            "development_allowed": False,
            "ssh_alias": environment["ssh_alias"],
        }
    alias = environment["ssh_alias"]
    checks: list[dict] = []
    try:
        connection = run_ssh(ssh_executable, alias, "hostname")
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"status": "BLOCKED", "reason": "CLOUD_PREFLIGHT_FAILED", "development_allowed": False, "ssh_alias": alias, "error": str(error), "checks": checks}
    checks.append({"name": "cloud_connection", "ok": connection.returncode == 0, "output": connection.stdout.strip()})
    if connection.returncode != 0:
        return {"status": "BLOCKED", "reason": "CLOUD_PREFLIGHT_FAILED", "development_allowed": False, "ssh_alias": alias, "checks": checks, "error": connection.stderr.strip()}
    if check_android:
        android = environment["android"]
        image = android["image"]
        expected_id = android["image_id"]
        image_check = run_ssh(ssh_executable, alias, f"docker image inspect --format '{{{{.Id}}}}' {image}")
        actual_id = image_check.stdout.strip()
        checks.append({"name": "android_image", "ok": image_check.returncode == 0 and actual_id == expected_id, "output": actual_id})
        cache = android["gradle_cache"]
        cache_check = run_ssh(ssh_executable, alias, f"docker volume inspect {cache}")
        checks.append({"name": "gradle_cache", "ok": cache_check.returncode == 0, "output": cache_check.stdout.strip()})
    passed = all(row["ok"] for row in checks)
    return {
        "status": "PASS" if passed else "BLOCKED",
        "reason": None if passed else "CLOUD_PREFLIGHT_FAILED",
        "development_allowed": passed,
        "ssh_alias": alias,
        "assumption": environment["default_assumption"],
        "checks": checks,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ssh-executable", default="ssh")
    parser.add_argument("--check-android", action="store_true")
    parser.add_argument("--declared-disconnected", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    result = check_cloud_environment(ssh_executable=args.ssh_executable, check_android=args.check_android, declared_disconnected=args.declared_disconnected, policy=load_policy())
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
