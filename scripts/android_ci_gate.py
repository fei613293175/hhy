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
CANDIDATE_RELEASE_PATTERN = re.compile(r"^R(?:0[6-9]|[12][0-9]|3[0-2])$")
REQUEST_ID_PATTERN = re.compile(r"^[A-Z0-9][A-Z0-9._-]{5,79}$")
EXCEPTION_ID_PATTERN = re.compile(r"^CR-\d{4}$")
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")


class GateError(RuntimeError):
    pass


def load_policy(path: Path = DEFAULT_POLICY) -> dict[str, Any]:
    document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    required = {
        "schema_version", "policy_id", "status", "enforcement", "build",
        "authentication", "emulator", "visual", "logs", "remediation", "delivery",
    }
    missing = sorted(required - set(document))
    if missing:
        raise GateError(f"Android automation policy missing sections: {missing}")
    if document.get("status") != "ENFORCED":
        raise GateError("Android automation policy must be ENFORCED")
    endpoint = str(document["build"].get("api_base_url") or "")
    if endpoint != "https://api.orbexa.cc":
        raise GateError("Android candidate API base URL must be https://api.orbexa.cc")
    max_attempts = int(document["remediation"].get("max_ai_attempts") or 0)
    if max_attempts != 3:
        raise GateError("Android remediation loop must be bounded to exactly three AI attempts")
    authorization = document["remediation"].get("candidate_authorization") or {}
    expected_authorization = {
        "mode": "AI_STANDING_DELEGATION",
        "authority": "AI_IMPLEMENTATION_AGENT",
        "standing_owner_confirmation_cr": "CR-0358",
        "prompt_owner_each_attempt": False,
        "require_previous_request_consumed": True,
        "require_distinct_root_cause": True,
        "require_fix_evidence": True,
        "require_change_request": True,
        "require_contiguous_attempts": True,
        "require_unique_request_id": True,
        "max_candidate_runs": 1,
        "owner_only_actions": [
            "NEW_SECRET",
            "THIRD_PARTY_PERMISSION",
            "FUNDS_OR_LEDGER_POLICY",
            "PRODUCTION_ACTIVATION",
        ],
    }
    if authorization != expected_authorization:
        raise GateError("Android candidate standing authorization contract drift")
    exceptions = document["remediation"].get("approved_attempt_exceptions") or []
    if not isinstance(exceptions, list):
        raise GateError("approved Android attempt exceptions must be a list")
    seen_exception_ids: set[str] = set()
    seen_request_ids: set[str] = set()
    seen_fix_commits: set[str] = set()
    next_attempt_by_release: dict[str, int] = {}
    for row in exceptions:
        if not isinstance(row, dict):
            raise GateError("every approved Android attempt exception must be an object")
        exception_id = str(row.get("exception_id") or "")
        release = str(row.get("release") or "")
        request_id = str(row.get("request_id") or "")
        required_fix_commit = str(row.get("required_fix_commit") or "")
        if not EXCEPTION_ID_PATTERN.fullmatch(exception_id):
            raise GateError("approved attempt exception_id must be a CR identifier")
        if exception_id in seen_exception_ids:
            raise GateError(f"duplicate approved attempt exception_id: {exception_id}")
        if not CANDIDATE_RELEASE_PATTERN.fullmatch(release):
            raise GateError("approved attempt exception release must be R06 through R32")
        expected_attempt = next_attempt_by_release.get(release, max_attempts + 1)
        if row.get("attempt") != expected_attempt:
            raise GateError(
                "approved attempt exceptions must be ordered and contiguous per release after the global limit"
            )
        if not REQUEST_ID_PATTERN.fullmatch(request_id):
            raise GateError("approved attempt exception request_id is invalid")
        if request_id in seen_request_ids:
            raise GateError(f"duplicate approved attempt exception request_id: {request_id}")
        if not COMMIT_PATTERN.fullmatch(required_fix_commit):
            raise GateError("approved attempt exception required_fix_commit must be a full lowercase SHA-1")
        if required_fix_commit in seen_fix_commits:
            raise GateError(
                f"duplicate approved attempt exception required_fix_commit: {required_fix_commit}"
            )
        if row.get("max_candidate_runs") != 1:
            raise GateError("approved attempt exception must permit exactly one candidate run")
        seen_exception_ids.add(exception_id)
        seen_request_ids.add(request_id)
        seen_fix_commits.add(required_fix_commit)
        next_attempt_by_release[release] = expected_attempt + 1
    authentication = document["authentication"]
    if authentication.get("mode") != "GITHUB_OIDC_ONE_TIME":
        raise GateError("Android candidate authentication must use one-time GitHub OIDC")
    bootstrap_ttl = int(authentication.get("bootstrap_ttl_seconds") or 0)
    if bootstrap_ttl != 600 or int(authentication.get("bootstrap_ttl_max_seconds") or 0) != 600:
        raise GateError("Android CI bootstrap TTL must be fixed to the ten-minute hard maximum")
    if int(authentication.get("session_ttl_seconds") or 0) != 900:
        raise GateError("Android CI session TTL must remain fifteen minutes")
    if authentication.get("consume_once") is not True or authentication.get("production_enabled") is not False:
        raise GateError("Android CI bootstrap must remain single-use and production-forbidden")
    required_claims = {"repository", "workflow_ref", "commit", "run_id"}
    if set(authentication.get("binding_claims") or []) != required_claims:
        raise GateError("Android CI bootstrap must bind repository, workflow, commit, and run")
    route_activation = authentication.get("public_route_activation") or {}
    if route_activation.get("required") is not True:
        raise GateError("Android candidate public route activation proof must be required")
    if route_activation.get("script") != "scripts/switch_android_candidate_route.sh":
        raise GateError("Android candidate public route activation must use the controlled switch script")
    if route_activation.get("confirmation_env") != "HHY_CANDIDATE_ROUTE_CONFIRM":
        raise GateError("Android candidate route activation confirmation variable drift")
    if route_activation.get("nginx_config") != "/www/server/panel/vhost/nginx/api.orbexa.cc.conf":
        raise GateError("Android candidate route activation must target the exact api.orbexa.cc Nginx config")
    if route_activation.get("public_probe_url") != "https://api.orbexa.cc/public-api/v1/platform/status":
        raise GateError("Android candidate route activation public probe URL drift")
    required_route_proofs = {
        "target_container_local_http_200",
        "nginx_upstream_exact",
        "nginx_config_test_pass",
        "public_request_id_in_target_container_log",
        "automatic_rollback_on_failure",
    }
    if set(route_activation.get("required_proofs") or []) != required_route_proofs:
        raise GateError("Android candidate public route activation proof set drift")
    if not document["delivery"].get("forbid_owner_request_before_pass"):
        raise GateError("Owner test must remain blocked before automated PASS")
    if not document["delivery"].get("desktop_copy_after_actions_pass"):
        raise GateError("Desktop candidate delivery must happen only after Actions PASS")
    if not document["delivery"].get("desktop_test_guide_required"):
        raise GateError("Every Desktop candidate must include the version test guide")
    if document["enforcement"].get("test_apk_requires_candidate_status") is not False:
        raise GateError("Fixed-toolchain TEST_APK delivery must not depend on automated candidate status")
    test_apk = document["delivery"].get("test_apk") or {}
    required_test_apk_evidence = {
        "frozen_commit", "official_api_base_url", "compile", "unit_tests", "lint",
        "assemble_apk", "stable_test_signing", "version_identity",
        "repository_desktop_server_https_sha256", "desktop_test_guide",
    }
    if test_apk.get("mode") != "OBX_TEST_FIXED_TOOLCHAIN":
        raise GateError("TEST_APK delivery must use the obx-test fixed toolchain")
    if test_apk.get("github_emulator_required") is not False:
        raise GateError("GitHub emulator must remain optional for TEST_APK delivery")
    if test_apk.get("continue_next_release_when_owner_pending") is not True:
        raise GateError("Pending owner feedback must not block next-release development")
    if set(test_apk.get("required_evidence") or []) != required_test_apk_evidence:
        raise GateError("TEST_APK fixed-toolchain evidence contract drift")
    if document["delivery"].get("candidate_fields_scope") != "AUTOMATED_CANDIDATE_ONLY":
        raise GateError("Legacy candidate-only delivery fields must not govern TEST_APK delivery")
    automated_candidate = document["delivery"].get("automated_candidate") or {}
    if automated_candidate.get("mode") != "ON_DEMAND_NON_BLOCKING_SPECIALTY":
        raise GateError("GitHub emulator candidate must remain an on-demand specialty")
    if automated_candidate.get("required_for_test_apk_delivery") is not False:
        raise GateError("Automated candidate must not block TEST_APK delivery")
    if automated_candidate.get("required_for_next_release_development") is not False:
        raise GateError("Automated candidate must not block next-release development")
    if document["visual"].get("review_authority") != "AI_IMPLEMENTATION_AGENT":
        raise GateError("Visual review authority must be AI_IMPLEMENTATION_AGENT")
    bootstrap = document["visual"].get("baseline_bootstrap") or {}
    if bootstrap.get("mode") != "SINGLE_EMULATOR_CAPTURE_THEN_LIGHTWEIGHT_PROMOTION":
        raise GateError("Visual baseline bootstrap must use one emulator capture and lightweight promotion")
    if bootstrap.get("promotion_rebuild_allowed") is not False or bootstrap.get("promotion_emulator_allowed") is not False:
        raise GateError("Visual baseline promotion must never rebuild or rerun the emulator")
    if document["enforcement"].get("release_complete_requires_owner_status") != "PASS":
        raise GateError("Formal release closure must retain project-owner device acceptance")
    if document["enforcement"].get("next_release_development_requires_owner_status") is not False:
        raise GateError("Owner device feedback must not block next-release development")
    if document["enforcement"].get("production_activation_requires_owner_status") != "PASS":
        raise GateError("Production activation must retain project-owner acceptance")
    if not document["emulator"].get("visual_manifest_root"):
        raise GateError("Every enforced release must resolve a visual manifest")
    return document


def resolve_attempt_policy(
    policy: dict[str, Any],
    *,
    release: str,
    attempt: int,
    request_id: str = "",
    exception_id: str = "",
    required_fix_commit: str = "",
) -> dict[str, Any]:
    max_attempts = int(policy["remediation"]["max_ai_attempts"])
    supplied_exception_fields = any((exception_id, required_fix_commit))
    if 1 <= attempt <= max_attempts:
        if supplied_exception_fields:
            raise GateError("ordinary attempts must not claim an attempt exception")
        return {
            "max_ai_attempts": max_attempts,
            "effective_attempt_limit": max_attempts,
            "attempt_exception_id": None,
            "required_fix_commit": None,
            "max_candidate_runs": None,
        }
    matches = [
        row for row in policy["remediation"].get("approved_attempt_exceptions", [])
        if row.get("release") == release
        and row.get("attempt") == attempt
        and row.get("request_id") == request_id
        and row.get("exception_id") == exception_id
        and row.get("required_fix_commit") == required_fix_commit
        and row.get("max_candidate_runs") == 1
    ]
    if len(matches) != 1:
        raise GateError("remediation attempt exceeds the global limit without one exact approved exception")
    return {
        "max_ai_attempts": max_attempts,
        "effective_attempt_limit": attempt,
        "attempt_exception_id": exception_id,
        "required_fix_commit": required_fix_commit,
        "max_candidate_runs": 1,
    }


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
    attempt = int(args.attempt)
    attempt_policy = resolve_attempt_policy(
        policy,
        release=args.release,
        attempt=attempt,
        request_id=str(getattr(args, "request_id", "") or ""),
        exception_id=str(getattr(args, "attempt_exception_id", "") or ""),
        required_fix_commit=str(getattr(args, "required_fix_commit", "") or ""),
    )
    max_attempts = int(attempt_policy["max_ai_attempts"])
    effective_attempt_limit = int(attempt_policy["effective_attempt_limit"])
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
    baseline_only = bool(failures) and all(
        failure["type"] == "VISUAL"
        and (
            failure["detail"].startswith("approved visual baseline missing:")
            or failure["detail"].startswith("baseline missing for ")
        )
        for failure in failures
    )
    status = "BASELINE_REVIEW_REQUIRED" if baseline_only and screenshot_evidence else (
        "PASS" if not failures else "FAIL"
    )
    report = {
        "schema": "hhy.android-ci-runtime/v1",
        "policy_id": policy["policy_id"],
        "release": args.release,
        "commit": args.commit,
        "github_run_id": str(getattr(args, "run_id", "")),
        "attempt": attempt,
        "max_ai_attempts": max_attempts,
        "effective_attempt_limit": effective_attempt_limit,
        "candidate_request_id": str(getattr(args, "request_id", "") or "") or None,
        "attempt_exception_id": attempt_policy["attempt_exception_id"],
        "required_fix_commit": attempt_policy["required_fix_commit"],
        "max_candidate_runs": attempt_policy["max_candidate_runs"],
        "status": status,
        "candidate_eligible": status == "PASS",
        "owner_test_allowed": status == "PASS",
        "failures": failures,
        "visual_comparisons": comparisons,
        "screenshot_evidence": screenshot_evidence,
        "remediation": {
            "status": "NOT_REQUIRED" if status == "PASS" else (
                "AI_BASELINE_REVIEW_REQUIRED" if status == "BASELINE_REVIEW_REQUIRED" else "REMEDIATION_REQUIRED"
            ),
            "next_action": "PACKAGE_CANDIDATE" if status == "PASS" else (
                "AI_REVIEW_AND_LIGHTWEIGHT_PROMOTE_BASELINE"
                if status == "BASELINE_REVIEW_REQUIRED"
                else "AI_ANALYZE_FIX_REBUILD_RETEST"
            ),
            "escalation_allowed": status == "FAIL" and attempt >= effective_attempt_limit,
        },
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if status in {"PASS", "BASELINE_REVIEW_REQUIRED"} else 1


def finalize(args: Any) -> int:
    policy = load_policy(Path(args.policy))
    errors: list[str] = []
    if args.build_result != "success":
        errors.append(f"build_result={args.build_result}")
    report_path = Path(args.emulator_report)
    if report_path.is_file():
        report = json.loads(report_path.read_text(encoding="utf-8"))
        runtime_status = report.get("status")
        if runtime_status not in {"PASS", "BASELINE_REVIEW_REQUIRED"}:
            errors.append("emulator/runtime report is neither PASS nor baseline review evidence")
        if runtime_status == "PASS" and not report.get("owner_test_allowed"):
            errors.append("emulator/runtime PASS does not allow owner testing")
        if runtime_status == "BASELINE_REVIEW_REQUIRED" and report.get("owner_test_allowed"):
            errors.append("baseline review evidence must not allow owner testing")
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
    if errors:
        status = "FAIL"
    elif report.get("status") == "BASELINE_REVIEW_REQUIRED":
        status = "BASELINE_REVIEW_REQUIRED"
    else:
        status = "PASS"
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
    return 0 if status in {"PASS", "BASELINE_REVIEW_REQUIRED"} else 1


def promote(args: Any) -> int:
    policy = load_policy(Path(args.policy))
    approval = yaml.safe_load(Path(args.approval).read_text(encoding="utf-8")) or {}
    runtime = json.loads(Path(args.emulator_report).read_text(encoding="utf-8"))
    candidate = json.loads(Path(args.candidate_report).read_text(encoding="utf-8"))
    release = str(approval.get("release") or "").upper()
    source = approval.get("source") or {}
    errors: list[str] = []
    if approval.get("schema") != "hhy.android-visual-baseline-approval/v1":
        errors.append("invalid baseline approval schema")
    if approval.get("status") != "APPROVED" or approval.get("authority") != "AI_IMPLEMENTATION_AGENT":
        errors.append("baseline approval is not an AI APPROVED decision")
    if source.get("github_run_id") != str(args.source_run_id):
        errors.append("approval source run does not match")
    if runtime.get("github_run_id") != str(args.source_run_id):
        errors.append("runtime source run does not match")
    if candidate.get("github_run_id") != str(args.source_run_id):
        errors.append("candidate source run does not match")
    source_commit = str(source.get("commit") or "")
    for name, document in (("runtime", runtime), ("candidate", candidate)):
        if document.get("policy_id") != policy["policy_id"]:
            errors.append(f"{name} policy does not match")
        if str(document.get("release") or "").upper() != release:
            errors.append(f"{name} release does not match")
        if document.get("commit") != source_commit:
            errors.append(f"{name} source commit does not match")
        if document.get("status") != "BASELINE_REVIEW_REQUIRED":
            errors.append(f"{name} is not baseline review evidence")
        if document.get("owner_test_allowed"):
            errors.append(f"{name} improperly allows owner testing before promotion")
    runtime_failures = runtime.get("failures") or []
    if not runtime_failures or any(
        failure.get("type") != "VISUAL"
        or not (
            str(failure.get("detail") or "").startswith("approved visual baseline missing:")
            or str(failure.get("detail") or "").startswith("baseline missing for ")
        )
        for failure in runtime_failures
    ):
        errors.append("runtime has failures beyond the missing approved baseline")

    screenshots = Path(args.screenshots)
    baseline_root = Path(args.baseline_root)
    approval_screens = approval.get("screens") or []
    approved_hashes = {str(row.get("file")): str(row.get("sha256")) for row in approval_screens}
    evidence_hashes = {
        str(row.get("screenshot")): str(row.get("sha256"))
        for row in (runtime.get("screenshot_evidence") or [])
    }
    actual_hashes = {
        path.name: hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(screenshots.glob("*.png"))
    }
    baseline_dir = baseline_root / release
    baseline_hashes = {
        path.name: hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(baseline_dir.glob("*.png"))
    } if baseline_dir.is_dir() else {}
    if not approved_hashes or not (
        approved_hashes == evidence_hashes == actual_hashes == baseline_hashes
    ):
        errors.append("approved, runtime, artifact, and repository screenshot hashes do not match exactly")
    visual, _, _ = visual_failures(
        policy, release, screenshots, baseline_root, Path(args.visual_manifest_root),
    )
    errors.extend(f"promoted visual validation: {detail}" for detail in visual)

    apk = Path(args.apk)
    if apk.is_file():
        apk_sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
        apk_size = apk.stat().st_size
    else:
        apk_sha256 = None
        apk_size = None
        errors.append(f"source APK missing: {apk}")
    candidate_apk = candidate.get("apk") or {}
    if apk_sha256 != candidate_apk.get("sha256") or apk_size != candidate_apk.get("size_bytes"):
        errors.append("source APK does not match captured candidate evidence")

    status = "PASS" if not errors else "FAIL"
    payload = {
        "schema": "hhy.android-ci-candidate-promotion/v1",
        "policy_id": policy["policy_id"],
        "release": release,
        "commit": source_commit,
        "github_run_id": str(args.run_id),
        "source_github_run_id": str(args.source_run_id),
        "status": status,
        "owner_test_allowed": status == "PASS",
        "release_completion_allowed": False,
        "production_activation_allowed": False,
        "apk": {"file": apk.name, "sha256": apk_sha256, "size_bytes": apk_size},
        "baseline_approval": approval,
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

    attempt_check = sub.add_parser("attempt-check")
    attempt_check.add_argument("--policy", default=str(DEFAULT_POLICY))
    attempt_check.add_argument("--release", required=True)
    attempt_check.add_argument("--commit", required=True)
    attempt_check.add_argument("--attempt", type=int, required=True)
    attempt_check.add_argument("--request-id", default="")
    attempt_check.add_argument("--attempt-exception-id", default="")
    attempt_check.add_argument("--required-fix-commit", default="")
    attempt_check.add_argument("--effective-attempt-limit", type=int, required=True)

    runtime = sub.add_parser("analyze")
    runtime.add_argument("--policy", default=str(DEFAULT_POLICY))
    runtime.add_argument("--release", required=True)
    runtime.add_argument("--commit", required=True)
    runtime.add_argument("--run-id", default="")
    runtime.add_argument("--attempt", type=int, default=1)
    runtime.add_argument("--request-id", default="")
    runtime.add_argument("--attempt-exception-id", default="")
    runtime.add_argument("--required-fix-commit", default="")
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

    promotion = sub.add_parser("promote")
    promotion.add_argument("--policy", default=str(DEFAULT_POLICY))
    promotion.add_argument("--approval", required=True)
    promotion.add_argument("--source-run-id", required=True)
    promotion.add_argument("--run-id", required=True)
    promotion.add_argument("--emulator-report", required=True)
    promotion.add_argument("--candidate-report", required=True)
    promotion.add_argument("--screenshots", required=True)
    promotion.add_argument("--baseline-root", default=str(ROOT / "tests/android/visual-baselines"))
    promotion.add_argument("--visual-manifest-root", default=str(DEFAULT_VISUAL_MANIFEST_ROOT))
    promotion.add_argument("--apk", required=True)
    promotion.add_argument("--output", required=True)

    args = parser.parse_args()
    try:
        if args.command == "policy-check":
            document = load_policy(Path(args.policy))
            print(json.dumps({"status": "PASS", "policy_id": document["policy_id"]}, ensure_ascii=False))
            return 0
        if args.command == "attempt-check":
            document = load_policy(Path(args.policy))
            resolution = resolve_attempt_policy(
                document,
                release=args.release,
                attempt=args.attempt,
                request_id=args.request_id,
                exception_id=args.attempt_exception_id,
                required_fix_commit=args.required_fix_commit,
            )
            if args.effective_attempt_limit != resolution["effective_attempt_limit"]:
                raise GateError("effective attempt limit does not match the approved policy")
            if resolution["required_fix_commit"] and args.commit == resolution["required_fix_commit"]:
                raise GateError("candidate commit must contain the required fix plus the approved request")
            print(json.dumps({"status": "PASS", **resolution}, ensure_ascii=False))
            return 0
        if args.command == "analyze":
            return analyze(args)
        if args.command == "finalize":
            return finalize(args)
        return promote(args)
    except (GateError, OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"ANDROID_CI_GATE_ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
