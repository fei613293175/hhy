#!/usr/bin/env python3
"""Build durable Android CI evidence and block candidates before every gate is green."""
from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
from typing import Any
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET

import yaml


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_POLICY = ROOT / "config/android-automation.yaml"
RELEASE_PATTERN = re.compile(r"^R(\d{2})$")


class GateError(RuntimeError):
    pass


def load_policy(path: Path = DEFAULT_POLICY) -> dict[str, Any]:
    document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    required = {
        "schema_version", "policy_id", "status", "enforcement", "build",
        "emulator", "visual", "logs", "remediation", "delivery",
    }
    missing = sorted(required - set(document))
    if missing:
        raise GateError(f"Android automation policy missing sections: {missing}")
    if document.get("status") != "ENFORCED":
        raise GateError("Android automation policy must be ENFORCED")
    endpoint = str(document["build"].get("api_base_url") or "")
    if endpoint != "https://api.orbexa.cc":
        raise GateError("Android candidate API base URL must be https://api.orbexa.cc")
    if int(document["remediation"].get("max_ai_attempts") or 0) != 3:
        raise GateError("Android remediation loop must be bounded to exactly three AI attempts")
    if not document["delivery"].get("forbid_owner_request_before_pass"):
        raise GateError("Owner test must remain blocked before automated PASS")
    if not document["delivery"].get("desktop_copy_after_actions_pass"):
        raise GateError("Desktop candidate delivery must happen only after Actions PASS")
    if not document["delivery"].get("desktop_test_guide_required"):
        raise GateError("Every Desktop candidate must include the version test guide")
    return document


def release_number(value: str) -> int | None:
    match = RELEASE_PATTERN.fullmatch(value.upper())
    return int(match.group(1)) if match else None


def is_enforced_release(policy: dict[str, Any], release: str) -> bool:
    current = release_number(release)
    start = release_number(str(policy["enforcement"]["from_release"]))
    return current is not None and start is not None and current >= start


def junit_failures(root: Path) -> list[str]:
    failures: list[str] = []
    if not root.exists():
        return [f"JUnit report root missing: {root}"]
    files = sorted(root.rglob("*.xml"))
    if not files:
        return [f"No JUnit XML reports under {root}"]
    for path in files:
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError as exc:
            failures.append(f"Malformed JUnit XML {path.name}: {exc}")
            continue
        failed = int(suite.attrib.get("failures", "0")) + int(suite.attrib.get("errors", "0"))
        if failed:
            failures.append(f"{path.name}: failures+errors={failed}")
    return failures


def visual_failures(
    policy: dict[str, Any], release: str, screenshots: Path, baseline_root: Path,
) -> tuple[list[str], list[dict[str, Any]]]:
    images = sorted(screenshots.rglob("*.png")) if screenshots.exists() else []
    required = int(policy["emulator"].get("required_screenshots") or 0)
    failures: list[str] = []
    comparisons: list[dict[str, Any]] = []
    if len(images) < required:
        failures.append(f"screenshots={len(images)} required={required}")
    if not is_enforced_release(policy, release):
        return failures, comparisons

    release_baseline = baseline_root / release.upper()
    if not release_baseline.is_dir():
        failures.append(f"approved visual baseline missing: {release_baseline}")
        return failures, comparisons
    try:
        from PIL import Image, ImageChops, ImageStat
    except ImportError as exc:
        failures.append(f"Pillow required for enforced visual comparison: {exc}")
        return failures, comparisons

    max_mae = float(policy["visual"]["max_mean_absolute_error"])
    max_ratio = float(policy["visual"]["max_changed_pixel_ratio"])
    threshold = int(policy["visual"]["changed_pixel_threshold"])
    for actual in images:
        baseline = release_baseline / actual.name
        if not baseline.is_file():
            failures.append(f"baseline missing for {actual.name}")
            continue
        with Image.open(actual).convert("RGB") as left, Image.open(baseline).convert("RGB") as right:
            if left.size != right.size:
                failures.append(f"size mismatch {actual.name}: {left.size} != {right.size}")
                continue
            diff = ImageChops.difference(left, right)
            stat = ImageStat.Stat(diff)
            mae = sum(stat.mean) / len(stat.mean)
            pixels = list(diff.convert("L").getdata())
            ratio = sum(value > threshold for value in pixels) / max(1, len(pixels))
            row = {"screenshot": actual.name, "mean_absolute_error": round(mae, 4), "changed_pixel_ratio": round(ratio, 6)}
            comparisons.append(row)
            if mae > max_mae or ratio > max_ratio:
                failures.append(f"visual drift {actual.name}: mae={mae:.2f}, ratio={ratio:.4f}")
    return failures, comparisons


def analyze(args: Any) -> int:
    policy = load_policy(Path(args.policy))
    max_attempts = int(policy["remediation"]["max_ai_attempts"])
    attempt = int(args.attempt)
    if not 1 <= attempt <= max_attempts:
        raise GateError(f"remediation attempt must be between 1 and {max_attempts}")
    test_exit_code_path = Path(args.test_exit_code_file)
    try:
        test_exit_code = int(test_exit_code_path.read_text(encoding="utf-8").strip())
    except (OSError, ValueError):
        test_exit_code = 255
    failures: list[dict[str, Any]] = []
    if test_exit_code != 0:
        failures.append({"type": "INSTRUMENTATION", "detail": f"exit_code={test_exit_code}"})

    for detail in junit_failures(Path(args.junit_root)):
        failures.append({"type": "JUNIT", "detail": detail})

    logcat_path = Path(args.logcat)
    logcat = logcat_path.read_text(encoding="utf-8", errors="replace") if logcat_path.is_file() else ""
    if not logcat:
        failures.append({"type": "LOGCAT", "detail": "logcat missing or empty"})
    for pattern in policy["logs"].get("fatal_patterns", []):
        if re.search(str(pattern), logcat, re.IGNORECASE):
            failures.append({"type": "CRASH_OR_ANR", "detail": str(pattern)})

    visual, comparisons = visual_failures(
        policy, args.release, Path(args.screenshots), Path(args.baseline_root),
    )
    failures.extend({"type": "VISUAL", "detail": detail} for detail in visual)
    status = "PASS" if not failures else "FAIL"
    report = {
        "schema": "hhy.android-ci-runtime/v1",
        "policy_id": policy["policy_id"],
        "release": args.release,
        "commit": args.commit,
        "attempt": attempt,
        "max_ai_attempts": max_attempts,
        "status": status,
        "candidate_eligible": status == "PASS",
        "owner_test_allowed": status == "PASS",
        "failures": failures,
        "visual_comparisons": comparisons,
        "remediation": {
            "status": "NOT_REQUIRED" if status == "PASS" else "REMEDIATION_REQUIRED",
            "next_action": "PACKAGE_CANDIDATE" if status == "PASS" else "AI_ANALYZE_FIX_REBUILD_RETEST",
            "escalation_allowed": status == "FAIL" and attempt >= max_attempts,
        },
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if status == "PASS" else 1


def finalize(args: Any) -> int:
    policy = load_policy(Path(args.policy))
    errors: list[str] = []
    if args.build_result != "success":
        errors.append(f"build_result={args.build_result}")
    report_path = Path(args.emulator_report)
    if report_path.is_file():
        report = json.loads(report_path.read_text(encoding="utf-8"))
        if report.get("status") != "PASS" or not report.get("owner_test_allowed"):
            errors.append("emulator/runtime report is not PASS")
        if report.get("policy_id") != policy["policy_id"]:
            errors.append("emulator/runtime policy_id does not match")
        if report.get("release") != args.release:
            errors.append("emulator/runtime release does not match")
        if report.get("commit") != args.commit:
            errors.append("emulator/runtime commit does not match")
    else:
        report = {}
        errors.append("emulator/runtime report missing")
    apk = Path(args.apk)
    if not apk.is_file():
        errors.append(f"APK missing: {apk}")
        sha256 = None
        size = None
    else:
        sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
        size = apk.stat().st_size
    status = "PASS" if not errors else "FAIL"
    payload = {
        "schema": "hhy.android-ci-candidate/v1",
        "policy_id": policy["policy_id"],
        "release": args.release,
        "commit": args.commit,
        "github_run_id": args.run_id,
        "status": status,
        "owner_test_allowed": status == "PASS",
        "release_completion_allowed": False,
        "apk": {"file": apk.name, "sha256": sha256, "size_bytes": size},
        "runtime_report": report,
        "errors": errors,
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if status == "PASS" else 1


def main() -> int:
    parser = ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    policy = sub.add_parser("policy-check")
    policy.add_argument("--policy", default=str(DEFAULT_POLICY))

    runtime = sub.add_parser("analyze")
    runtime.add_argument("--policy", default=str(DEFAULT_POLICY))
    runtime.add_argument("--release", required=True)
    runtime.add_argument("--commit", required=True)
    runtime.add_argument("--attempt", type=int, default=1)
    runtime.add_argument("--test-exit-code-file", required=True)
    runtime.add_argument("--junit-root", required=True)
    runtime.add_argument("--logcat", required=True)
    runtime.add_argument("--screenshots", required=True)
    runtime.add_argument("--baseline-root", default=str(ROOT / "tests/android/visual-baselines"))
    runtime.add_argument("--output", required=True)

    candidate = sub.add_parser("finalize")
    candidate.add_argument("--policy", default=str(DEFAULT_POLICY))
    candidate.add_argument("--release", required=True)
    candidate.add_argument("--commit", required=True)
    candidate.add_argument("--run-id", required=True)
    candidate.add_argument("--build-result", required=True)
    candidate.add_argument("--emulator-report", required=True)
    candidate.add_argument("--apk", required=True)
    candidate.add_argument("--output", required=True)

    args = parser.parse_args()
    try:
        if args.command == "policy-check":
            document = load_policy(Path(args.policy))
            print(json.dumps({"status": "PASS", "policy_id": document["policy_id"]}, ensure_ascii=False))
            return 0
        if args.command == "analyze":
            return analyze(args)
        return finalize(args)
    except (GateError, OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"ANDROID_CI_GATE_ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
