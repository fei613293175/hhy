#!/usr/bin/env python3
"""Select the lightweight or full continuity CI tier from changed paths."""

from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import fnmatch
import json
import subprocess


ROOT = Path(__file__).resolve().parents[1]

FULL_LIFECYCLE_PATTERNS = (
    ".continuity/CONTINUITY_POLICY.yaml",
    ".continuity/schemas/**",
    ".githooks/**",
    ".github/workflows/continuity-gate.yml",
    "config/DEVELOPMENT_RUNTIME.yaml",
    "config/REPOSITORY_TRANSPORT.yaml",
    "scripts/continuity.py",
    "scripts/continuity_gate.py",
    "scripts/continuity_lib.py",
    "scripts/continuity_ci_scope.py",
    "scripts/create_handoff_bundle.py",
    "scripts/prepare_commit_message.py",
    "scripts/restore_git_transport.py",
    "scripts/run_continuity_self_test.py",
    "scripts/select_execution_profile.py",
    "scripts/test_continuity_protocol.py",
    "scripts/verify_cloud_environment.py",
    "tests/test_cloud_environment.py",
    "tests/test_context_pack_parallel_policy.py",
    "tests/test_continuity*.py",
    "tests/test_git_transport_recovery.py",
    "tests/test_model_routing.py",
    "tests/test_release_close_gate.py",
)


def normalize(path: str) -> str:
    normalized = path.strip().replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def matches(path: str, pattern: str) -> bool:
    path = normalize(path)
    if pattern.endswith("/**"):
        prefix = pattern[:-3]
        return path == prefix or path.startswith(prefix + "/")
    return fnmatch.fnmatch(path, pattern)


def full_lifecycle_reasons(changed_files: list[str], *, initial_history: bool = False) -> list[str]:
    if initial_history:
        return ["INITIAL_HISTORY"]
    return sorted({
        normalize(path)
        for path in changed_files
        if any(matches(path, pattern) for pattern in FULL_LIFECYCLE_PATTERNS)
    })


def git_changed_files(base_ref: str | None, head_ref: str) -> tuple[list[str], bool]:
    if not base_ref or set(base_ref) == {"0"}:
        return [], True
    verify = subprocess.run(
        ["git", "rev-parse", "--verify", base_ref],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if verify.returncode != 0:
        return [], True
    diff = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACDMRTUXB", base_ref, head_ref],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=True,
    )
    return sorted({normalize(line) for line in diff.stdout.splitlines() if line.strip()}), False


def write_github_output(path: Path, payload: dict[str, object]) -> None:
    lines = [
        f"full_lifecycle={'true' if payload['full_lifecycle'] else 'false'}",
        f"changed_count={payload['changed_count']}",
        f"reason={payload['reason']}",
    ]
    with path.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument("--base-ref")
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--changed-file", action="append", default=[])
    parser.add_argument("--initial-history", action="store_true")
    parser.add_argument("--github-output")
    parser.add_argument("--json-output")
    args = parser.parse_args()

    if args.changed_file:
        changed = sorted({normalize(path) for path in args.changed_file})
        initial = args.initial_history
    else:
        changed, inferred_initial = git_changed_files(args.base_ref, args.head_ref)
        initial = args.initial_history or inferred_initial
    reasons = full_lifecycle_reasons(changed, initial_history=initial)
    payload = {
        "schema": "hhy.continuity-ci-scope/v1",
        "full_lifecycle": bool(reasons),
        "reason": ",".join(reasons) if reasons else "FAST_PATH_ONLY",
        "changed_count": len(changed),
        "changed_files": changed,
    }
    if args.github_output:
        write_github_output(Path(args.github_output), payload)
    if args.json_output:
        output = Path(args.json_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
