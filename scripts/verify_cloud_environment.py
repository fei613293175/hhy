#!/usr/bin/env python3
"""Read-only preflight for the existing project cloud environment.

This gate intentionally contains no local SDK bootstrap, image build, or cache
rebuild operation. A failure blocks development and must be reported.
"""
from __future__ import annotations

import argparse
import json
import shlex
import subprocess
import sys
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / "config" / "DEVELOPMENT_RUNTIME.yaml"
ANDROID_PLATFORM_VOLUME = "hhy-android-sdk-platform-36"


def load_policy(path: Path = POLICY_PATH) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def run_ssh(ssh_executable: str, alias: str, command: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [ssh_executable, alias, command], text=True, capture_output=True, encoding="utf-8", errors="replace", timeout=30
    )


def parse_probe(output: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in output.splitlines():
        key, separator, value = line.partition("=")
        if separator and key:
            values[key] = value
    return values


def android_probe_command(*, image: str, expected_id: str, cache: str) -> str:
    quoted_image = shlex.quote(image)
    quoted_expected_id = shlex.quote(expected_id)
    quoted_cache = shlex.quote(cache)
    return "\n".join(
        [
            "set -eu",
            "printf 'hostname=%s\\n' \"$(hostname)\"",
            f"actual_image_id=$(docker image inspect --format '{{{{.Id}}}}' {quoted_image})",
            f"test \"$actual_image_id\" = {quoted_expected_id}",
            "printf 'android_image_id=%s\\n' \"$actual_image_id\"",
            f"docker volume inspect {quoted_cache} >/dev/null",
            "printf 'gradle_cache=present\\n'",
            f"platform_mount=$(docker volume inspect --format '{{{{.Mountpoint}}}}' {ANDROID_PLATFORM_VOLUME})",
            "test -f \"$platform_mount/android.jar\"",
            "printf 'android_platform_36=present\\n'",
            "swap_mb=$(awk '/^SwapTotal:/ {print int($2 / 1024)}' /proc/meminfo)",
            "available_mb=$(awk '/^MemAvailable:/ {print int($2 / 1024)}' /proc/meminfo)",
            "printf 'swap_mb=%s\\n' \"$swap_mb\"",
            "printf 'available_mb=%s\\n' \"$available_mb\"",
            "printf 'load_1m=%s\\n' \"$(cut -d' ' -f1 /proc/loadavg)\"",
            "if flock -n /var/lock/hhy-android-build.lock -c true; then printf 'android_build_slot=available\\n'; else printf 'android_build_slot=busy\\n'; fi",
        ]
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
    android = environment.get("android") if check_android else None
    command = "hostname"
    if android:
        command = android_probe_command(
            image=android["image"],
            expected_id=android["image_id"],
            cache=android["gradle_cache"],
        )
    try:
        connection = run_ssh(ssh_executable, alias, command)
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"status": "BLOCKED", "reason": "CLOUD_PREFLIGHT_FAILED", "development_allowed": False, "ssh_alias": alias, "error": str(error), "checks": checks}
    probe = parse_probe(connection.stdout) if android else {}
    checks.append({"name": "cloud_connection", "ok": connection.returncode == 0, "output": probe.get("hostname", connection.stdout.strip())})
    if connection.returncode != 0:
        return {"status": "BLOCKED", "reason": "CLOUD_PREFLIGHT_FAILED", "development_allowed": False, "ssh_alias": alias, "checks": checks, "error": connection.stderr.strip()}
    if android:
        swap_mb = int(probe.get("swap_mb", "0"))
        available_mb = int(probe.get("available_mb", "0"))
        checks.extend(
            [
                {"name": "android_image", "ok": probe.get("android_image_id") == android["image_id"], "output": probe.get("android_image_id", "")},
                {"name": "gradle_cache", "ok": probe.get("gradle_cache") == "present", "output": probe.get("gradle_cache", "")},
                {"name": "android_platform_36", "ok": probe.get("android_platform_36") == "present", "output": probe.get("android_platform_36", "")},
                {"name": "swap_capacity", "ok": swap_mb >= 7_168, "output": f"{swap_mb} MB"},
                {"name": "available_memory", "ok": available_mb >= 1_024, "output": f"{available_mb} MB"},
                {"name": "android_build_slot", "ok": probe.get("android_build_slot") in {"available", "busy"}, "output": probe.get("android_build_slot", "unknown")},
                {"name": "load_1m", "ok": "load_1m" in probe, "output": probe.get("load_1m", "")},
            ]
        )
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
