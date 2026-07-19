#!/usr/bin/env python3
"""Plan or execute FAST/MODULE/INTEGRATION/RELEASE quality checks."""
from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
from typing import Any, Iterable
import fnmatch
import json
import os
import shutil
import subprocess
import sys
import time

import yaml


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MAP = ROOT / "config/test-impact-map.yaml"


class ImpactMapError(RuntimeError):
    pass


def _bundled_dependency_path(*parts: str) -> Path:
    return Path.home() / ".cache" / "codex-runtimes" / "codex-primary-runtime" / "dependencies" / Path(*parts)


def resolve_git_executable() -> str:
    override = os.environ.get("HHY_GIT_BIN") or os.environ.get("HHY_GIT")
    if override:
        return override
    found = shutil.which("git.exe" if os.name == "nt" else "git") or shutil.which("git")
    bundled = _bundled_dependency_path("native", "git", "cmd", "git.exe")
    if found:
        return found
    if bundled.is_file():
        return str(bundled)
    return "git.exe" if os.name == "nt" else "git"


def resolve_pnpm_executable() -> str:
    override = os.environ.get("HHY_PNPM_BIN")
    if override:
        return override
    found = shutil.which("pnpm.cmd" if os.name == "nt" else "pnpm") or shutil.which("pnpm")
    bundled = _bundled_dependency_path("bin", "fallback", "pnpm.cmd")
    if found:
        return found
    if bundled.is_file():
        return str(bundled)
    return "pnpm.cmd" if os.name == "nt" else "pnpm"


def normalize_path(value: str) -> str:
    normalized = value.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized.lstrip("/")


def path_matches(path: str, pattern: str) -> bool:
    path = normalize_path(path)
    pattern = normalize_path(pattern)
    if pattern.endswith("/**"):
        prefix = pattern[:-3]
        if "*" not in prefix and "?" not in prefix:
            return path == prefix or path.startswith(prefix + "/")
    return fnmatch.fnmatchcase(path, pattern)


def _git(root: Path, *arguments: str, check: bool = True) -> str:
    executable = resolve_git_executable()
    completed = subprocess.run(
        [executable, *arguments], cwd=root, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if check and completed.returncode != 0:
        raise ImpactMapError(completed.stderr.strip() or f"git {' '.join(arguments)} failed")
    return completed.stdout


def changed_files(root: Path, base_ref: str | None = None, head_ref: str = "HEAD") -> list[str]:
    if base_ref and set(base_ref) != {"0"}:
        verified = _git(root, "rev-parse", "--verify", base_ref, check=False).strip()
        if verified:
            merge_base = _git(root, "merge-base", verified, head_ref).strip()
            output = _git(root, "diff", "--name-only", "--diff-filter=ACDMRTUXB", f"{merge_base}..{head_ref}")
            return sorted({normalize_path(row) for row in output.splitlines() if row.strip()})
    if base_ref:
        output = _git(root, "ls-files")
        return sorted({normalize_path(row) for row in output.splitlines() if row.strip()})
    tracked = _git(root, "diff", "--name-only", "--diff-filter=ACDMRTUXB", "HEAD")
    untracked = _git(root, "ls-files", "--others", "--exclude-standard")
    return sorted({normalize_path(row) for row in (tracked + untracked).splitlines() if row.strip()})


def load_impact_map(path: Path) -> dict[str, Any]:
    try:
        document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except (OSError, yaml.YAMLError) as exc:
        raise ImpactMapError(f"cannot read impact map {path}: {exc}") from exc
    if not isinstance(document.get("profiles"), dict) or not document["profiles"]:
        raise ImpactMapError("impact map requires profiles")
    if not isinstance(document.get("checks"), list):
        raise ImpactMapError("impact map requires checks")
    seen: set[str] = set()
    for check in document["checks"]:
        if not isinstance(check, dict) or not check.get("id"):
            raise ImpactMapError("every check requires an id")
        check_id = str(check["id"])
        if check_id in seen:
            raise ImpactMapError(f"duplicate check id: {check_id}")
        seen.add(check_id)
        if not isinstance(check.get("profiles"), list) or not isinstance(check.get("command"), list):
            raise ImpactMapError(f"{check_id} requires profiles and command arrays")
    return document


def profile_closure(document: dict[str, Any], profile: str) -> set[str]:
    profiles = document["profiles"]
    profile = profile.upper()
    if profile not in profiles:
        raise ImpactMapError(f"unknown profile: {profile}")
    result: set[str] = set()

    def visit(name: str) -> None:
        if name in result:
            return
        if name not in profiles:
            raise ImpactMapError(f"unknown included profile: {name}")
        result.add(name)
        for included in profiles[name].get("includes", []) or []:
            visit(str(included).upper())

    visit(profile)
    return result


def select_checks(
    document: dict[str, Any], profile: str, paths: Iterable[str], *, job: str | None = None,
) -> list[dict[str, Any]]:
    requested = profile.upper()
    effective = profile_closure(document, requested)
    affected_only = bool(document["profiles"][requested].get("affected_only"))
    normalized_paths = sorted({normalize_path(path) for path in paths if path})
    selected: list[dict[str, Any]] = []
    for check in document["checks"]:
        if not (effective & {str(value).upper() for value in check["profiles"]}):
            continue
        if job and check.get("ci_job") != job:
            continue
        patterns = [str(value) for value in check.get("paths", []) or []]
        if affected_only and not any(
            path_matches(path, pattern) for path in normalized_paths for pattern in patterns
        ):
            continue
        selected.append(check)
    return selected


def _tool_values(root: Path, release: str | None) -> dict[str, str]:
    windows = os.name == "nt"
    return {
        "python": sys.executable,
        "pnpm": resolve_pnpm_executable(),
        "git": resolve_git_executable(),
        "maven_wrapper": "mvnw.cmd" if windows else "./mvnw",
        "gradle_wrapper": "gradlew.bat" if windows else "./gradlew",
        "root": str(root),
        "release": release or "",
    }


def build_plan(
    document: dict[str, Any], profile: str, paths: Iterable[str], *, root: Path = ROOT,
    release: str | None = None, job: str | None = None,
) -> dict[str, Any]:
    profile = profile.upper()
    normalized_paths = sorted({normalize_path(path) for path in paths if path})
    checks = select_checks(document, profile, normalized_paths, job=job)
    tools = _tool_values(root, release)
    planned: list[dict[str, Any]] = []
    for check in checks:
        raw_command = [str(value) for value in check["command"]]
        if any("{release}" in value for value in raw_command) and not release:
            raise ImpactMapError("RELEASE profile requires --release")
        try:
            command = [value.format_map(tools) for value in raw_command]
        except KeyError as exc:
            raise ImpactMapError(f"{check['id']} has unknown command placeholder: {exc}") from exc
        planned.append({
            "id": check["id"],
            "ci_job": check.get("ci_job") or "tooling",
            "cwd": str((root / str(check.get("cwd") or ".")).resolve()),
            "command": command,
            "env": {str(key): str(value).format_map(tools) for key, value in (check.get("env") or {}).items()},
            "timeout_seconds": int(check.get("timeout_seconds") or 900),
        })
    return {
        "schema": "hhy.test-impact-plan/v1",
        "profile": profile,
        "affected_only": bool(document["profiles"][profile].get("affected_only")),
        "changed_files": normalized_paths,
        "job_filter": job,
        "checks": planned,
        "ci_jobs": sorted({row["ci_job"] for row in planned}),
    }


def execute_plan(plan: dict[str, Any]) -> dict[str, Any]:
    results: list[dict[str, Any]] = []
    for check in plan["checks"]:
        print(f"QUALITY_CHECK_START {check['id']}", flush=True)
        started = time.monotonic()
        environment = os.environ.copy()
        environment.update(check["env"])
        git_executable = resolve_git_executable()
        pnpm_executable = resolve_pnpm_executable()
        environment.setdefault("HHY_GIT_BIN", git_executable)
        environment.setdefault("HHY_PNPM_BIN", pnpm_executable)
        portable_directories = [str(Path(git_executable).parent), str(Path(pnpm_executable).parent)]
        existing_path = environment.get("PATH", "")
        environment["PATH"] = os.pathsep.join(
            [directory for directory in portable_directories if directory]
            + ([existing_path] if existing_path else [])
        )
        try:
            completed = subprocess.run(
                check["command"], cwd=check["cwd"], env=environment, text=True,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=check["timeout_seconds"],
            )
            result = {
                "id": check["id"], "status": "PASS" if completed.returncode == 0 else "FAIL",
                "exit_code": completed.returncode,
                "duration_seconds": round(time.monotonic() - started, 3),
                "stdout_tail": completed.stdout[-6000:], "stderr_tail": completed.stderr[-6000:],
            }
        except (OSError, subprocess.TimeoutExpired) as exc:
            result = {
                "id": check["id"], "status": "ERROR", "exit_code": None,
                "duration_seconds": round(time.monotonic() - started, 3),
                "stdout_tail": "", "stderr_tail": str(exc),
            }
        results.append(result)
        print(f"QUALITY_CHECK_{result['status']} {check['id']} {result['duration_seconds']}s", flush=True)
    status = "PASS" if all(row["status"] == "PASS" for row in results) else "FAIL"
    return {**plan, "status": status, "executed": True, "results": results}


def write_github_outputs(path: Path, plan: dict[str, Any], document: dict[str, Any]) -> None:
    known_jobs = sorted({str(check.get("ci_job") or "tooling") for check in document["checks"]})
    selected = set(plan["ci_jobs"])
    with path.open("a", encoding="utf-8", newline="\n") as handle:
        for job in known_jobs:
            handle.write(f"{job}={'true' if job in selected else 'false'}\n")


def main() -> int:
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--profile", required=True, type=str.upper, choices=["FAST", "MODULE", "INTEGRATION", "RELEASE"])
    parser.add_argument("--map", dest="map_path", type=Path, default=DEFAULT_MAP)
    parser.add_argument("--base-ref")
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--changed-file", action="append", default=[])
    parser.add_argument("--job")
    parser.add_argument("--release")
    parser.add_argument("--execute", action="store_true", help="run checks; default is a side-effect-free dry run")
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    try:
        document = load_impact_map(args.map_path)
        paths = args.changed_file or changed_files(ROOT, args.base_ref, args.head_ref)
        plan = build_plan(document, args.profile, paths, release=args.release, job=args.job)
        result = execute_plan(plan) if args.execute else {
            **plan, "status": "DRY_RUN", "executed": False, "results": [],
        }
        if args.github_output:
            write_github_outputs(args.github_output, plan, document)
        if args.json_out:
            output = args.json_out if args.json_out.is_absolute() else ROOT / args.json_out
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result["status"] in {"DRY_RUN", "PASS"} else 1
    except ImpactMapError as exc:
        print(f"QUALITY_GATE_CONFIG_ERROR {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
