from __future__ import annotations

import json
import os
import re
import shlex
import shutil
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

from .hard import run_hard_protection, run_release_hard_protection
from .legacy import scan_active_control_plane
from .secrets import scan_secrets
from .state import assert_valid_state, read_state
from .tasks import load_task_specs, validate_specs
from .util import git, read_yaml, run, sha256_file, utc_now, write_json

PROFILES = {"task", "module", "freeze", "candidate", "release"}


def product_readiness(repo: Path, release: str) -> dict[str, Any]:
    errors: list[str] = []
    required = [repo / "releases" / release / "DEFINITION_OF_READY.yaml", repo / "releases" / release / "STORIES.yaml"]
    for path in required:
        if not path.is_file():
            errors.append(f"missing {path.relative_to(repo).as_posix()}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if re.search(r"\bTBD\b|待定|TODO", text, flags=re.IGNORECASE):
            errors.append(f"unresolved placeholder in {path.relative_to(repo).as_posix()}")
    return {"schema": "hhy.product-readiness/v5.0", "status": "PASS" if not errors else "FAIL", "release": release, "errors": errors}


def _script(repo: Path, relative: str, *args: str, timeout: int = 1800) -> dict[str, Any]:
    path = repo / relative
    if not path.is_file():
        return {"status": "FAIL", "exit_code": 127, "command": [relative, *args], "stderr_tail": "required script missing", "stdout_tail": ""}
    return run([os.environ.get("PYTHON", "python3"), relative, *args], repo, timeout=timeout)




_TEST_SUFFIXES = (
    "_test.py", ".test.py", ".spec.ts", ".test.ts", ".spec.tsx", ".test.tsx",
    ".spec.js", ".test.js", ".spec.jsx", ".test.jsx", "Test.kt", "Tests.kt",
    "Test.java", "Tests.java",
)
_SKIP_ADDITION = re.compile(
    r"^\+\s*(?:"
    r"@(?:Disabled|Ignore)\b|"
    r"@pytest\.mark\.(?:skip|skipif)\b|"
    r"pytestmark\s*=.*\bskip\b|"
    r"(?:describe|context|it|test)\.skip\s*\(|"
    r"(?:xit|xdescribe|xcontext)\s*\("
    r")",
    re.IGNORECASE,
)


def _is_test_path(value: str) -> bool:
    path = value.replace("\\", "/")
    name = Path(path).name
    return (
        path.startswith("tests/")
        or "/tests/" in f"/{path}"
        or "/test/" in f"/{path}"
        or name.endswith(_TEST_SUFFIXES)
    )


def test_authority_check(repo: Path, commit: str) -> dict[str, Any]:
    """Reject direct deletion or disabling of tests in the candidate commit.

    The Worker may add and repair tests, but it may not make a candidate pass by
    deleting a test file or introducing a conventional skip/disable marker. This
    check is intentionally narrow and deterministic; deeper semantic review is
    performed once by the independent read-only reviewer for high-risk tasks.
    """
    expected = git(repo, "rev-parse", commit)
    parent_line = git(repo, "rev-list", "--parents", "-n", "1", expected, check=False).split()
    parent = parent_line[1] if len(parent_line) > 1 else ""
    if not parent:
        return {"schema": "hhy.test-authority/v5.0", "status": "PASS", "commit": expected, "root_commit": True, "violations": []}
    changed = git(repo, "-c", "core.quotepath=false", "diff", "--name-status", "--find-renames", f"{parent}..{expected}")
    violations: list[dict[str, Any]] = []
    test_paths: list[str] = []
    for line in changed.splitlines():
        columns = line.split("\t")
        if not columns:
            continue
        status = columns[0]
        paths = columns[1:]
        target = paths[-1] if paths else ""
        if _is_test_path(target):
            test_paths.append(target)
            if status.startswith("D"):
                violations.append({"type": "TEST_FILE_DELETED", "path": target})
    for path in sorted(set(test_paths)):
        diff = git(repo, "diff", "--unified=0", f"{parent}..{expected}", "--", path, check=False)
        for line_number, line in enumerate(diff.splitlines(), 1):
            if line.startswith("+++"):
                continue
            if _SKIP_ADDITION.search(line):
                violations.append({
                    "type": "TEST_SKIP_OR_DISABLE_ADDED",
                    "path": path,
                    "diff_line": line_number,
                    "text": line[:300],
                })
    return {
        "schema": "hhy.test-authority/v5.0",
        "status": "PASS" if not violations else "FAIL",
        "commit": expected,
        "parent": parent,
        "test_paths_checked": sorted(set(test_paths)),
        "violations": violations,
    }


def _base_checks(repo: Path, task_id: str | None, profile: str, release: str | None) -> list[dict[str, Any]]:
    checks: list[dict[str, Any]] = []
    specs = load_task_specs(repo)
    plan = read_yaml(repo / "governance" / "PROGRAM_PLAN.yaml") or {}
    errors = validate_specs(specs, plan)
    checks.append({"id": "static_specs", "status": "PASS" if not errors else "FAIL", "detail": errors})
    try:
        assert_valid_state(read_state(repo), specs)
        checks.append({"id": "state_integrity", "status": "PASS"})
    except Exception as exc:
        checks.append({"id": "state_integrity", "status": "FAIL", "detail": str(exc)})
    legacy = scan_active_control_plane(repo)
    checks.append({"id": "active_legacy_routes", "status": legacy["status"], "detail": legacy.get("findings")})
    secret = scan_secrets(repo, full=profile in {"freeze", "candidate", "release"}, include_untracked=profile in {"freeze", "candidate", "release"})
    checks.append({"id": "secret_scan", "status": secret["status"], "detail": {k: secret.get(k) for k in ("mode", "files_considered", "files_scanned", "findings")}})
    if task_id:
        hard = run_hard_protection(repo, task_id, profile)
        checks.append({"id": "hard_protection", "status": hard["status"], "detail": hard})
    # Module/freeze/candidate/release are release objects, even when invoked from
    # a recovery task. Always evaluate the deduplicated release requirement set so
    # an empty orchestration task cannot bypass product hard protections.
    if release and profile in {"module", "freeze", "candidate", "release"}:
        release_hard = run_release_hard_protection(repo, release, profile)
        checks.append({"id": "release_hard_protection", "status": release_hard["status"], "detail": release_hard})
    return checks


def run_gate(repo: Path, *, profile: str, task_id: str | None, commit: str, release: str | None = None) -> dict[str, Any]:
    if profile not in PROFILES:
        raise ValueError(f"unknown gate profile {profile}")
    actual = git(repo, "rev-parse", "HEAD")
    expected = git(repo, "rev-parse", commit)
    specs = load_task_specs(repo)
    spec = specs.get(task_id) if task_id else None
    release = release or (spec.get("release") if spec else None)
    if not release and profile in {"module", "freeze", "candidate", "release"}:
        release = (read_state(repo).get("project") or {}).get("active_release")
    checks = [{"id": "commit_binding", "status": "PASS" if actual == expected else "FAIL", "detail": {"HEAD": actual, "expected": expected}}]
    authority = test_authority_check(repo, expected)
    checks.append({"id": "test_authority", "status": authority["status"], "detail": authority})
    checks.extend(_base_checks(repo, task_id, profile, release))
    risks = set((spec or {}).get("risks") or [])
    release_has_ui = bool(release) and any(
        row.get("release") == release and "ui" in set(row.get("risks") or [])
        for row in specs.values()
    )
    # Task-specific deterministic commands are generated from the normalized task
    # specification. They are executed exactly once here, outside the Worker.
    if profile == "task" and spec:
        for index, command in enumerate(spec.get("acceptance_commands") or [], 1):
            try:
                argv = shlex.split(command)
            except ValueError as exc:
                checks.append({"id": f"task_acceptance_{index}", "status": "FAIL", "detail": str(exc)})
                continue
            if not argv or "hhy_governance.py" in command and " gate " in f" {command} ":
                checks.append({"id": f"task_acceptance_{index}", "status": "FAIL", "detail": "recursive governance gate command is forbidden"})
                continue
            result = run(argv, repo, timeout=1800)
            checks.append({"id": f"task_acceptance_{index}", "status": result["status"], "detail": result})
    if profile in {"module", "freeze", "candidate", "release"}:
        for check_id, script in [
            ("documentation", "scripts/check_v123_documentation.py"),
            ("api_contract", "scripts/check_api_contract.py"),
            ("database_schema", "scripts/check_db_schema.py"),
            ("ui_tokens", "scripts/check_ui_tokens.py"),
        ]:
            result = _script(repo, script)
            checks.append({"id": check_id, "status": result["status"], "detail": result})
    if profile in {"freeze", "candidate", "release"} and release and release_has_ui:
        result = _script(repo, "scripts/check_ui_visual_acceptance.py", "--release", release)
        checks.append({"id": "visual_contract", "status": result["status"], "detail": result})
    if profile in {"candidate", "release"}:
        # Run the repository's own package-manager test matrix. No model-generated
        # success claim may replace this command.
        if (repo / "package.json").is_file():
            if (repo / "pnpm-lock.yaml").is_file():
                result = run(["pnpm", "test"], repo, timeout=1800) if shutil.which("pnpm") else {"status": "FAIL", "exit_code": 127, "stderr_tail": "pnpm missing", "stdout_tail": "", "command": ["pnpm", "test"]}
            else:
                result = run(["npm", "test"], repo, timeout=1800) if shutil.which("npm") else {"status": "FAIL", "exit_code": 127, "stderr_tail": "npm missing", "stdout_tail": "", "command": ["npm", "test"]}
            checks.append({"id": "web_tests", "status": result["status"], "detail": result})
        if release:
            rel = release.lower()
            release_commands = [
                (f"{rel}_test_matrix", [os.environ.get("PYTHON", "python3"), f"scripts/run_{rel}_test_matrix.py"]),
                (f"{rel}_specialized_matrix", [os.environ.get("PYTHON", "python3"), f"scripts/run_{rel}_specialized_matrix.py"]),
                (f"{rel}_observability", [os.environ.get("PYTHON", "python3"), f"scripts/check_{rel}_observability.py"]),
                (f"{rel}_database_invariants", ["bash", f"scripts/run_{rel}_database_invariants.sh"]),
            ]
            found = 0
            for check_id, argv in release_commands:
                if (repo / argv[-1]).is_file():
                    found += 1
                    result = run(argv, repo, timeout=1800)
                    checks.append({"id": check_id, "status": result["status"], "detail": result})
            checks.append({"id": "release_specific_matrix_present", "status": "PASS" if found else "FAIL", "detail": {"release": release, "found": found}})
    status = "PASS" if all(row["status"] == "PASS" for row in checks) else "FAIL"
    report = {
        "schema": "hhy.gate-report/v5.0", "status": status, "profile": profile,
        "task_id": task_id, "release": release, "commit": expected, "generated_at": utc_now(), "checks": checks,
    }
    output = repo / "governance" / "evidence" / "gates" / f"{profile}-{task_id or 'release'}-{expected[:12]}.json"
    write_json(output, report)
    report["evidence_path"] = output.relative_to(repo).as_posix()
    return report


def _validate_json_schema(repo: Path, schema_name: str, data: dict[str, Any]) -> list[str]:
    path = repo / "governance" / "schemas" / schema_name
    if not path.is_file():
        return [f"schema file missing: {schema_name}"]
    try:
        schema = json.loads(path.read_text(encoding="utf-8"))
        validator = Draft202012Validator(schema)
        return [error.message for error in sorted(validator.iter_errors(data), key=lambda e: list(e.path))]
    except Exception as exc:
        return [f"schema validation failed: {exc}"]


def _internal_path(repo: Path, value: str, label: str, errors: list[str]) -> Path | None:
    candidate = (repo / value).resolve()
    try:
        candidate.relative_to(repo.resolve())
    except ValueError:
        errors.append(f"{label} must stay inside repository")
        return None
    return candidate


def _validate_gate_report(path: Path | None, frozen_commit: str, release: str, errors: list[str]) -> None:
    if path is None or not path.is_file():
        errors.append("gate report file missing")
        return
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"invalid gate report: {exc}")
        return
    if report.get("schema") != "hhy.gate-report/v5.0":
        errors.append("gate report schema mismatch")
    if report.get("status") != "PASS":
        errors.append("candidate gate report is not PASS")
    if report.get("profile") != "candidate":
        errors.append("gate report profile must be candidate")
    if report.get("commit") != frozen_commit:
        errors.append("gate report commit mismatch")
    if report.get("release") != release:
        errors.append("gate report release mismatch")
    checks = report.get("checks")
    if not isinstance(checks, list) or not checks:
        errors.append("gate report checks are required")
    elif any(not isinstance(row, dict) or row.get("status") != "PASS" for row in checks):
        errors.append("all candidate gate checks must PASS")


def validate_candidate_evidence(repo: Path, release: str, evidence_path: Path, frozen_commit: str) -> dict[str, Any]:
    errors: list[str] = []
    if not evidence_path.is_file():
        return {"status": "FAIL", "errors": ["candidate evidence file missing"]}
    try:
        data = json.loads(evidence_path.read_text(encoding="utf-8"))
    except Exception as exc:
        return {"status": "FAIL", "errors": [f"invalid candidate evidence: {exc}"]}
    errors.extend(_validate_json_schema(repo, "candidate-evidence.schema.json", data))
    if data.get("release") != release:
        errors.append("release mismatch")
    if data.get("commit") != frozen_commit:
        errors.append("candidate commit does not equal frozen commit")

    gate_path = _internal_path(repo, str(data.get("gate_report") or ""), "gate_report", errors)
    _validate_gate_report(gate_path, frozen_commit, release, errors)

    apk = data.get("apk") if isinstance(data.get("apk"), dict) else {}
    if apk.get("source_commit") != frozen_commit:
        errors.append("APK source commit mismatch")
    apk_path = _internal_path(repo, str(apk.get("path") or ""), "apk.path", errors)
    if apk_path is None or not apk_path.is_file():
        errors.append("APK file does not exist")
    elif sha256_file(apk_path) != apk.get("sha256"):
        errors.append("APK sha256 mismatch")
    signing = str(apk.get("signing_sha256") or "").replace(":", "")
    if not re.fullmatch(r"[A-Fa-f0-9]{64}", signing):
        errors.append("APK signing_sha256 must be a 32-byte SHA-256 fingerprint")

    screenshots = data.get("screenshots") if isinstance(data.get("screenshots"), dict) else {}
    if screenshots.get("source_commit") != frozen_commit:
        errors.append("screenshot source commit mismatch")
    paths = screenshots.get("paths") if isinstance(screenshots.get("paths"), list) else []
    if not paths:
        errors.append("runtime screenshot paths are required")
    for value in paths:
        path = _internal_path(repo, str(value), "screenshot path", errors)
        if path is None or not path.is_file():
            errors.append(f"runtime screenshot file missing: {value}")
    return {"status": "PASS" if not errors else "FAIL", "errors": sorted(set(errors)), "evidence": data}

