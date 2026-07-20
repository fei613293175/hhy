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
DEFAULT_VISUAL_MANIFEST_ROOT = ROOT / "tests/android/visual-manifests"
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
    if document["visual"].get("review_authority") != "AI_IMPLEMENTATION_AGENT":
        raise GateError("Visual review authority must be AI_IMPLEMENTATION_AGENT")
    if document["enforcement"].get("release_complete_requires_owner_status") != "PASS":
        raise GateError("Formal release closure must retain project-owner device acceptance")
    if document["enforcement"].get("next_release_development_requires_owner_status") is not False:
        raise GateError("Owner device feedback must not block next-release development")
    if document["enforcement"].get("production_activation_requires_owner_status") != "PASS":
        raise GateError("Production activation must retain project-owner acceptance")
    if not document["emulator"].get("visual_manifest_root"):
        raise GateError("Every enforced release must resolve a visual manifest")
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
    manifest_root: Path,
) -> tuple[list[str], list[dict[str, Any]], list[dict[str, Any]]]:
    images = sorted(screenshots.rglob("*.png")) if screenshots.exists() else []
    failures: list[str] = []
    comparisons: list[dict[str, Any]] = []
    evidence: list[dict[str, Any]] = []
    manifest_path = manifest_root / f"{release.upper()}.yaml"
    manifest: dict[str, Any] = {}
    if is_enforced_release(policy, release):
        if not manifest_path.is_file():
            failures.append(f"visual manifest missing: {manifest_path}")
            return failures, comparisons, evidence
        manifest = yaml.safe_load(manifest_path.read_text(encoding="utf-8")) or {}
        if manifest.get("schema") != "hhy.android-visual-manifest/v1":
            failures.append(f"invalid visual manifest schema: {manifest_path}")
        if str(manifest.get("release") or "").upper() != release.upper():
            failures.append(f"visual manifest release mismatch: {manifest_path}")
        screens = manifest.get("screens") or []
        expected_names = [str(row.get("file") or "") for row in screens]
        if not expected_names or any(not name.endswith(".png") for name in expected_names):
            failures.append(f"visual manifest has invalid screenshot names: {manifest_path}")
        if len(expected_names) != len(set(expected_names)):
            failures.append(f"visual manifest has duplicate screenshot names: {manifest_path}")
        actual_names = [path.name for path in images]
        for name in sorted(set(expected_names) - set(actual_names)):
            failures.append(f"required screenshot missing: {name}")
        for name in sorted(set(actual_names) - set(expected_names)):
            failures.append(f"unexpected screenshot: {name}")

    digest_to_names: dict[str, list[str]] = {}
    for image in images:
        digest = hashlib.sha256(image.read_bytes()).hexdigest()
        digest_to_names.setdefault(digest, []).append(image.name)
        evidence.append({"screenshot": image.name, "sha256": digest, "size_bytes": image.stat().st_size})
    for digest, names in digest_to_names.items():
        if len(names) > 1:
            failures.append(f"duplicate screenshot sha256={digest}: {','.join(sorted(names))}")
    if not is_enforced_release(policy, release):
        return failures, comparisons, evidence

    try:
        from PIL import Image, ImageChops, ImageStat
    except ImportError as exc:
        failures.append(f"Pillow required for enforced visual comparison: {exc}")
        return failures, comparisons, evidence

    minimum_cross_ratio = float(policy["visual"].get("minimum_cross_screen_changed_pixel_ratio") or 0)
    threshold = int(policy["visual"]["changed_pixel_threshold"])
    for previous, current in zip(images, images[1:]):
        with Image.open(previous).convert("RGB") as left, Image.open(current).convert("RGB") as right:
            if left.size != right.size:
                failures.append(f"cross-screen size mismatch {previous.name}/{current.name}: {left.size} != {right.size}")
                continue
            pixels = list(ImageChops.difference(left, right).convert("L").getdata())
            ratio = sum(value > threshold for value in pixels) / max(1, len(pixels))
            comparisons.append({
                "comparison": "cross_screen",
                "screenshots": [previous.name, current.name],
                "changed_pixel_ratio": round(ratio, 6),
            })
            if ratio < minimum_cross_ratio:
                failures.append(
                    f"cross-screen pixel difference too small {previous.name}/{current.name}: ratio={ratio:.4f}"
                )

    release_baseline = baseline_root / release.upper()
    if not release_baseline.is_dir():
        failures.append(f"approved visual baseline missing: {release_baseline}")
        return failures, comparisons, evidence

    max_mae = float(policy["visual"]["max_mean_absolute_error"])
    max_ratio = float(policy["visual"]["max_changed_pixel_ratio"])
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
            row = {"comparison": "baseline", "screenshot": actual.name, "mean_absolute_error": round(mae, 4), "changed_pixel_ratio": round(ratio, 6)}
            comparisons.append(row)
            if mae > max_mae or ratio > max_ratio:
                failures.append(f"visual drift {actual.name}: mae={mae:.2f}, ratio={ratio:.4f}")
    return failures, comparisons, evidence


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

    visual, comparisons, screenshot_evidence = visual_failures(
        policy, args.release, Path(args.screenshots), Path(args.baseline_root),
        Path(args.visual_manifest_root),
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
        "screenshot_evidence": screenshot_evidence,
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
        "production_activation_allowed": False,
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
    runtime.add_argument("--visual-manifest-root", default=str(DEFAULT_VISUAL_MANIFEST_ROOT))
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
