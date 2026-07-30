#!/usr/bin/env python3
"""Recover and validate the repository's non-secret Git transport metadata."""
from __future__ import annotations

from argparse import ArgumentParser, Namespace
from fnmatch import fnmatchcase
from pathlib import Path
from typing import Any, Sequence
from urllib.parse import urlsplit, urlunsplit
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

import yaml


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DESCRIPTOR = ROOT / "config/REPOSITORY_TRANSPORT.yaml"


class TransportError(RuntimeError):
    """The tracked transport contract or local Git state is unsafe."""


def git_executable() -> str:
    override = os.environ.get("HHY_GIT_BIN", "").strip()
    if override:
        candidate = Path(override).expanduser()
        if candidate.is_file():
            return str(candidate)
        raise TransportError(f"HHY_GIT_BIN does not exist: {candidate}")
    discovered = shutil.which("git")
    if discovered:
        return discovered
    # Codex Desktop supplies this runtime on Windows even when Git is not in PATH.
    bundled = Path.home() / ".cache/codex-runtimes/codex-primary-runtime/dependencies/native/git/cmd/git.exe"
    if bundled.is_file():
        return str(bundled)
    raise TransportError("Git executable not found; configure HHY_GIT_BIN")


def run_git(
    root: Path,
    *args: str,
    check: bool = True,
    timeout: int = 120,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        [git_executable(), *args],
        cwd=str(root),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise TransportError(f"git {' '.join(args)} failed ({result.returncode}): {detail}")
    return result


def url_has_credentials(value: str) -> bool:
    text = value.strip()
    if not text or any(char in text for char in "\r\n\x00"):
        return True
    parsed = urlsplit(text)
    if parsed.scheme in {"http", "https", "ssh", "git", "file"}:
        # SSH usernames in URLs are still machine-specific transport identity. The
        # tracked descriptor uses a credential-free HTTPS endpoint instead.
        if parsed.username is not None or parsed.password is not None:
            return True
        if parsed.query or parsed.fragment:
            return True
    return False


def normalize_url(value: str) -> str:
    text = value.strip().replace("\\", "/")
    parsed = urlsplit(text)
    if parsed.scheme in {"http", "https", "ssh", "git", "file"}:
        host = (parsed.hostname or "").lower()
        port = f":{parsed.port}" if parsed.port else ""
        netloc = host + port
        path = parsed.path.rstrip("/") or "/"
        return urlunsplit((parsed.scheme.lower(), netloc, path, "", ""))
    return text.rstrip("/")


def _reject_secret_fields(value: Any, path: str = "") -> None:
    forbidden = {"password", "passwd", "pat", "token", "private_key", "identity_file", "credential_helper"}
    if isinstance(value, dict):
        for key, child in value.items():
            key_text = str(key).lower()
            if key_text in forbidden:
                raise TransportError(f"secret-bearing field is forbidden in transport descriptor: {path}{key}")
            _reject_secret_fields(child, f"{path}{key}.")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _reject_secret_fields(child, f"{path}{index}.")
    elif isinstance(value, str) and "-----BEGIN " in value and "PRIVATE KEY-----" in value:
        raise TransportError("private key material is forbidden in transport descriptor")


def load_descriptor(path: Path = DEFAULT_DESCRIPTOR) -> dict[str, Any]:
    if not path.is_file():
        raise TransportError(f"transport descriptor missing: {path}")
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except yaml.YAMLError as exc:
        raise TransportError(f"invalid transport descriptor: {exc}") from exc
    if not isinstance(value, dict) or value.get("schema_version") != 1:
        raise TransportError("transport descriptor schema_version must be 1")
    _reject_secret_fields(value)
    repository = value.get("repository") or {}
    canonical_url = str(repository.get("canonical_url") or "").strip()
    remote_name = str(repository.get("remote_name") or "").strip()
    if not canonical_url or not remote_name:
        raise TransportError("repository.canonical_url and repository.remote_name are required")
    if url_has_credentials(canonical_url):
        raise TransportError("canonical repository URL must not contain credentials, query, or fragment")
    if not re.fullmatch(r"[A-Za-z0-9._-]+", remote_name):
        raise TransportError("repository.remote_name is invalid")
    patterns = (value.get("branch_policy") or {}).get("allowed_patterns") or []
    if not patterns or not all(isinstance(pattern, str) and pattern.strip() for pattern in patterns):
        raise TransportError("branch_policy.allowed_patterns must not be empty")
    upstream_remote = str((value.get("branch_policy") or {}).get("upstream_remote") or remote_name)
    if upstream_remote != remote_name:
        raise TransportError("branch_policy.upstream_remote must equal repository.remote_name")
    return value


def is_git_repository(root: Path) -> bool:
    if not root.exists():
        return False
    result = run_git(root, "rev-parse", "--is-inside-work-tree", check=False)
    return result.returncode == 0 and result.stdout.strip() == "true"


def git_value(root: Path, *args: str) -> str:
    return run_git(root, *args).stdout.strip()


def config_values(root: Path, key: str) -> list[str]:
    result = run_git(root, "config", "--get-all", key, check=False)
    if result.returncode not in {0, 1}:
        raise TransportError(f"cannot read Git config key: {key}")
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def current_branch(root: Path) -> str:
    branch = git_value(root, "branch", "--show-current")
    if not branch:
        raise TransportError("detached HEAD is not eligible for transport recovery or push")
    return branch


def require_concrete_head(root: Path) -> str:
    result = run_git(root, "rev-parse", "--verify", "HEAD", check=False)
    if result.returncode != 0:
        raise TransportError("repository has no concrete Git history")
    return result.stdout.strip()


def validate_branch(branch: str, descriptor: dict[str, Any]) -> None:
    patterns = descriptor["branch_policy"]["allowed_patterns"]
    if not any(fnmatchcase(branch, pattern) for pattern in patterns):
        raise TransportError(f"branch is outside tracked branch policy: {branch}")


def expected_transport(descriptor: dict[str, Any], branch: str) -> tuple[str, str, str]:
    repository = descriptor["repository"]
    remote = str(repository["remote_name"])
    url = str(repository["canonical_url"])
    template = str((descriptor.get("branch_policy") or {}).get("upstream_merge_template") or "refs/heads/{branch}")
    try:
        merge_ref = template.format(branch=branch)
    except (KeyError, ValueError) as exc:
        raise TransportError("invalid upstream_merge_template") from exc
    if merge_ref != f"refs/heads/{branch}":
        raise TransportError("upstream_merge_template must resolve to the same branch")
    return remote, url, merge_ref


def remote_urls(root: Path, remote: str) -> tuple[list[str], list[str]]:
    return config_values(root, f"remote.{remote}.url"), config_values(root, f"remote.{remote}.pushurl")


def validate_remote_urls(root: Path, remote: str, expected_url: str) -> list[str]:
    errors: list[str] = []
    fetch_urls, push_urls = remote_urls(root, remote)
    if len(fetch_urls) != 1 or normalize_url(fetch_urls[0]) != normalize_url(expected_url):
        errors.append(f"remote {remote} URL must exactly match tracked canonical URL")
    for label, values in (("fetch", fetch_urls), ("push", push_urls)):
        for value in values:
            if url_has_credentials(value):
                errors.append(f"remote {remote} {label} URL contains credentials, query, or fragment")
            if normalize_url(value) != normalize_url(expected_url):
                errors.append(f"remote {remote} {label} URL differs from tracked canonical URL")
    return sorted(set(errors))


def configured_upstream(root: Path, branch: str) -> tuple[str | None, str | None]:
    remote = config_values(root, f"branch.{branch}.remote")
    merge = config_values(root, f"branch.{branch}.merge")
    return (remote[-1] if remote else None, merge[-1] if merge else None)


def transport_status(root: Path, descriptor: dict[str, Any]) -> dict[str, Any]:
    result: dict[str, Any] = {
        "status": "BLOCKED",
        "root": str(root.resolve()),
        "descriptor": str(DEFAULT_DESCRIPTOR),
        "errors": [],
    }
    if not is_git_repository(root):
        result["mode"] = "RAW_SOURCE_NO_GIT_HISTORY"
        result["errors"] = ["no .git history; restore only from a verified repository.bundle into a new target"]
        return result
    try:
        head = require_concrete_head(root)
        branch = current_branch(root)
        validate_branch(branch, descriptor)
        remote, expected_url, merge_ref = expected_transport(descriptor, branch)
        errors = validate_remote_urls(root, remote, expected_url)
        configured_remote, configured_merge = configured_upstream(root, branch)
        if configured_remote != remote or configured_merge != merge_ref:
            errors.append(f"upstream must be {remote}/{branch}")
        result.update({
            "mode": "EXISTING_GIT_REPOSITORY",
            "head": head,
            "branch": branch,
            "remote": remote,
            "canonical_url": expected_url,
            "configured_upstream": {
                "remote": configured_remote,
                "merge": configured_merge,
            },
            "errors": errors,
            "status": "READY" if not errors else "BLOCKED",
        })
    except TransportError as exc:
        result["mode"] = "EXISTING_GIT_REPOSITORY"
        result["errors"] = [str(exc)]
    return result


def configure_existing_repository(
    root: Path,
    descriptor: dict[str, Any],
    *,
    branch: str | None = None,
    allow_remote_repair: bool = False,
    fetch: bool = True,
) -> dict[str, Any]:
    if not is_git_repository(root):
        raise TransportError("no .git history; refusing to initialize or fabricate history from raw source")
    require_concrete_head(root)
    actual_branch = current_branch(root)
    if branch and actual_branch != branch:
        raise TransportError(f"checked-out branch mismatch: expected {branch}, found {actual_branch}")
    branch = actual_branch
    validate_branch(branch, descriptor)
    remote, expected_url, merge_ref = expected_transport(descriptor, branch)
    fetch_urls, push_urls = remote_urls(root, remote)
    existing_errors = validate_remote_urls(root, remote, expected_url) if fetch_urls or push_urls else []
    if existing_errors and not allow_remote_repair:
        raise TransportError("; ".join(existing_errors) + "; use --allow-remote-repair after verifying repository identity")
    if not fetch_urls:
        run_git(root, "remote", "add", remote, expected_url)
    elif existing_errors:
        run_git(root, "remote", "set-url", remote, expected_url)
        run_git(root, "config", "--unset-all", f"remote.{remote}.pushurl", check=False)
    if fetch:
        run_git(root, "fetch", "--prune", remote, timeout=240)
    run_git(root, "config", f"branch.{branch}.remote", remote)
    run_git(root, "config", f"branch.{branch}.merge", merge_ref)
    result = transport_status(root, descriptor)
    if result["status"] != "READY":
        raise TransportError("transport restore did not converge: " + "; ".join(result["errors"]))
    result["operation"] = "RESTORED_EXISTING_REPOSITORY"
    return result


def verify_bundle(bundle: Path) -> None:
    if not bundle.is_file():
        raise TransportError(f"repository.bundle not found: {bundle}")
    with tempfile.TemporaryDirectory(prefix="hhy-bundle-verify-") as temp:
        verify_root = Path(temp)
        run_git(verify_root, "init", "--quiet")
        run_git(verify_root, "bundle", "verify", str(bundle.resolve()))


def restore_from_bundle(
    bundle: Path,
    target: Path,
    descriptor: dict[str, Any],
    *,
    branch: str,
    fetch: bool = True,
) -> dict[str, Any]:
    validate_branch(branch, descriptor)
    if target.exists():
        raise TransportError("bundle recovery target must not already exist")
    target.parent.mkdir(parents=True, exist_ok=True)
    verify_bundle(bundle)
    staging = Path(tempfile.mkdtemp(prefix=f".{target.name}.restore-", dir=str(target.parent)))
    clone_target = staging / "repository"
    try:
        run_git(staging, "clone", "--quiet", "--branch", branch, str(bundle.resolve()), str(clone_target), timeout=240)
        configure_existing_repository(
            clone_target,
            descriptor,
            branch=branch,
            allow_remote_repair=True,
            fetch=fetch,
        )
        clone_target.replace(target)
    finally:
        shutil.rmtree(staging, ignore_errors=True)
    result = transport_status(target, descriptor)
    result["operation"] = "RESTORED_VERIFIED_BUNDLE"
    result["bundle"] = str(bundle.resolve())
    return result


def rejected_push_argument(arguments: Sequence[str]) -> str | None:
    force_flags = {"-f", "--force", "--force-with-lease", "--force-if-includes"}
    for argument in arguments:
        value = argument.strip()
        if value in force_flags or value.startswith("--force-with-lease=") or value.startswith("+"):
            return value
    return None


def push_preflight(
    root: Path,
    descriptor: dict[str, Any],
    *,
    remote_name: str | None = None,
    push_url: str | None = None,
    branch: str | None = None,
    push_arguments: Sequence[str] = (),
    fetch: bool | None = None,
) -> dict[str, Any]:
    rejected = rejected_push_argument(push_arguments)
    if rejected:
        raise TransportError(f"force push/refspec is forbidden: {rejected}")
    if not is_git_repository(root):
        raise TransportError("push requires verified Git history; raw source cannot be pushed")
    head = require_concrete_head(root)
    actual_branch = current_branch(root)
    if branch and branch != actual_branch:
        raise TransportError(f"push branch mismatch: expected {actual_branch}, received {branch}")
    validate_branch(actual_branch, descriptor)
    expected_remote, expected_url, merge_ref = expected_transport(descriptor, actual_branch)
    if remote_name and remote_name != expected_remote:
        raise TransportError(f"wrong push remote: expected {expected_remote}, received {remote_name}")
    if push_url:
        if url_has_credentials(push_url):
            raise TransportError("actual push URL contains credentials, query, or fragment")
        if normalize_url(push_url) != normalize_url(expected_url):
            raise TransportError("actual push URL differs from tracked canonical URL")
    errors = validate_remote_urls(root, expected_remote, expected_url)
    configured_remote, configured_merge = configured_upstream(root, actual_branch)
    if configured_remote != expected_remote or configured_merge != merge_ref:
        errors.append(f"wrong upstream: expected {expected_remote}/{actual_branch}")
    if errors:
        raise TransportError("; ".join(sorted(set(errors))))
    should_fetch = (descriptor.get("push_preflight") or {}).get("fetch_before_compare", True) if fetch is None else fetch
    if should_fetch:
        run_git(root, "fetch", "--prune", expected_remote, timeout=240)
    if (descriptor.get("push_preflight") or {}).get("require_clean_worktree", True):
        porcelain = git_value(root, "status", "--porcelain=v1", "--untracked-files=all")
        if porcelain:
            raise TransportError("push preflight requires a clean worktree")
    remote_ref = f"refs/remotes/{expected_remote}/{actual_branch}"
    exists = run_git(root, "show-ref", "--verify", "--quiet", remote_ref, check=False).returncode == 0
    ahead = behind = 0
    if exists:
        counts = git_value(root, "rev-list", "--left-right", "--count", f"{remote_ref}...HEAD").split()
        if len(counts) != 2:
            raise TransportError("cannot compare local branch with upstream")
        behind, ahead = (int(counts[0]), int(counts[1]))
        if behind and ahead:
            raise TransportError(f"branch diverged from {expected_remote}/{actual_branch}: behind={behind}, ahead={ahead}")
        if behind:
            raise TransportError(f"branch is behind {expected_remote}/{actual_branch} by {behind} commit(s)")
    elif not (descriptor.get("push_preflight") or {}).get("allow_new_remote_branch", False):
        raise TransportError(f"remote branch does not exist: {expected_remote}/{actual_branch}")
    return {
        "status": "PASS",
        "root": str(root.resolve()),
        "head": head,
        "branch": actual_branch,
        "remote": expected_remote,
        "upstream": f"{expected_remote}/{actual_branch}",
        "remote_branch_exists": exists,
        "ahead": ahead,
        "behind": behind,
        "force_push_allowed": False,
    }


def print_payload(value: dict[str, Any], as_json: bool) -> None:
    if as_json:
        print(json.dumps(value, ensure_ascii=False, indent=2))
    else:
        print(yaml.safe_dump(value, allow_unicode=True, sort_keys=False, width=140).rstrip())


def command_status(args: Namespace) -> dict[str, Any]:
    return transport_status(args.root.resolve(), load_descriptor(args.config.resolve()))


def command_restore(args: Namespace) -> dict[str, Any]:
    descriptor = load_descriptor(args.config.resolve())
    root = args.root.resolve()
    if is_git_repository(root):
        return configure_existing_repository(
            root,
            descriptor,
            branch=args.branch,
            allow_remote_repair=args.allow_remote_repair,
            fetch=not args.no_fetch,
        )
    bundle = args.bundle.resolve() if args.bundle else root / str((descriptor.get("recovery") or {}).get("bundle_filename") or "repository.bundle")
    if not bundle.is_file():
        raise TransportError("raw source has no .git or repository.bundle; refusing to fabricate history")
    if not args.target:
        raise TransportError("bundle recovery requires --target pointing to a new directory")
    if not args.branch:
        raise TransportError("bundle recovery requires the exact --branch from HANDOFF/Context Pack")
    return restore_from_bundle(bundle, args.target.resolve(), descriptor, branch=args.branch, fetch=not args.no_fetch)


def command_push_preflight(args: Namespace) -> dict[str, Any]:
    return push_preflight(
        args.root.resolve(),
        load_descriptor(args.config.resolve()),
        remote_name=args.remote,
        push_url=args.push_url,
        branch=args.branch,
        push_arguments=args.push_arg,
        fetch=not args.no_fetch,
    )


def build_parser() -> ArgumentParser:
    parser = ArgumentParser(description="Recover and validate tracked Git transport metadata")
    parser.add_argument("--config", type=Path, default=DEFAULT_DESCRIPTOR)
    parser.add_argument("--json", action="store_true")
    subparsers = parser.add_subparsers(dest="command", required=True)

    status = subparsers.add_parser("status", help="report local remote/upstream drift without modifying Git")
    status.add_argument("--root", type=Path, default=ROOT)
    status.set_defaults(func=command_status)

    restore = subparsers.add_parser("restore", help="restore an existing clone or verified repository.bundle")
    restore.add_argument("--root", type=Path, default=ROOT)
    restore.add_argument("--bundle", type=Path)
    restore.add_argument("--target", type=Path)
    restore.add_argument("--branch")
    restore.add_argument("--allow-remote-repair", action="store_true")
    restore.add_argument("--no-fetch", action="store_true")
    restore.set_defaults(func=command_restore)

    preflight = subparsers.add_parser("push-preflight", help="reject unsafe remote, upstream, history, or force push state")
    preflight.add_argument("--root", type=Path, default=ROOT)
    preflight.add_argument("--remote")
    preflight.add_argument("--push-url")
    preflight.add_argument("--branch")
    preflight.add_argument("--push-arg", action="append", default=[])
    preflight.add_argument("--no-fetch", action="store_true")
    preflight.set_defaults(func=command_push_preflight)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        payload = args.func(args)
        print_payload(payload, args.json)
        return 0 if payload.get("status") in {"READY", "PASS"} else 2
    except (TransportError, OSError, subprocess.SubprocessError) as exc:
        print_payload({"status": "BLOCKED", "error": str(exc)}, args.json)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
