#!/usr/bin/env python3
"""合伙云 Pro 持续开发无状态接续基础库。

只使用标准库和 PyYAML。所有写入采用临时文件替换，事件日志采用哈希链，
从而让任意 AI 在不依赖对话的情况下恢复任务、变更、检查点和工作区意图。
"""
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable, Iterator, Sequence
import csv
import fnmatch
import hashlib
import io
import json
import os
import re
import shutil
import socket
import subprocess
import tarfile
import tempfile
import time
import uuid
import zipfile

import yaml

UTC = timezone.utc
PROTOCOL_VERSION = "1.0"
PACKAGE_VERSION = "1.2.3"

CONTINUITY_DIR = ".continuity"
POLICY_FILE = f"{CONTINUITY_DIR}/CONTINUITY_POLICY.yaml"
STATE_FILE = f"{CONTINUITY_DIR}/STATE.yaml"
ACTIVE_FILE = f"{CONTINUITY_DIR}/ACTIVE_SESSION.yaml"
EVENT_LOG_FILE = f"{CONTINUITY_DIR}/EVENT_LOG.jsonl"
SESSION_INDEX_FILE = f"{CONTINUITY_DIR}/SESSION_INDEX.yaml"
TASK_CLAIMS_FILE = f"{CONTINUITY_DIR}/TASK_CLAIMS.yaml"
CR_INDEX_FILE = f"{CONTINUITY_DIR}/CHANGE_REQUEST_INDEX.yaml"
TASK_TRANSITIONS_FILE = f"{CONTINUITY_DIR}/TASK_TRANSITIONS.yaml"
LOCK_FILE = f"{CONTINUITY_DIR}/runtime/continuity.lock"

# 这些记录由接续工具自己维护，不进入“项目内容指纹”，否则写检查点本身会改变指纹。
MANAGED_RECORD_PREFIXES = (
    ".continuity/",
    "artifacts/context/",
    "artifacts/handoffs/",
    "artifacts/validation/",
    "artifacts/reports/",
    "artifacts/exports/",
    "docs/03-continuity/sessions/",
)
MANAGED_RECORD_FILES = {
    "CURRENT_STATUS.yaml",
    "NEXT_TASK.yaml",
    "catalogs/session_index.csv",
    "catalogs/handoff_index.csv",
    "catalogs/change_request_index.csv",
    "catalogs/task_transition_ledger.csv",
}

# 打包未提交文件时必须排除秘密、缓存和产物。
PORTABLE_EXCLUDE_PATTERNS = (
    ".git/**",
    "**/.git/**",
    "artifacts/handoffs/**",
    "artifacts/exports/**",
    "**/node_modules/**",
    "**/dist/**",
    "**/target/**",
    "**/.gradle/**",
    "**/build/**",
    "**/__pycache__/**",
    "**/*.pyc",
    "**/*.pyo",
    "**/.env",
    "**/.env.*",
    "**/*.pem",
    "**/*.key",
    "**/*.p12",
    "**/*.pfx",
    "**/*.jks",
    "**/*.keystore",
    "**/*.apk",
    "**/*secret*",
    "**/*private_key*",
)

# Directory names that must be removed from os.walk's traversal list. The
# equivalent glob rules above still protect individual files; this set avoids
# discovering dependency and build files at all.
FINGERPRINT_PRUNE_DIRECTORY_NAMES = frozenset(
    {".git", "node_modules", "dist", "target", ".gradle", "build", "__pycache__"}
)

SUSPICIOUS_SECRET_REGEXES = {
    "private_key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "aws_like_access_key": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "generic_secret_assignment": re.compile(
        r"(?i)\b(?:secret|password|passwd|private[_-]?key|access[_-]?key)[\s\"']*[:=][\s\"']+[A-Za-z0-9+/=_-]{12,}"
    ),
}


class ContinuityError(RuntimeError):
    """接续门禁错误。"""


def now_utc() -> datetime:
    return datetime.now(tz=UTC)


def iso_utc(value: datetime | None = None) -> str:
    value = value or now_utc()
    return value.astimezone(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse_iso(value: str | None) -> datetime | None:
    if not value:
        return None
    text = value.strip().replace("Z", "+00:00")
    parsed = datetime.fromisoformat(text)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_text(text: str) -> str:
    return sha256_bytes(text.encode("utf-8"))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_write_text(path: Path, text: str, mode: int | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    temp = Path(temp_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        if mode is not None:
            os.chmod(temp, mode)
        os.replace(temp, path)
    finally:
        if temp.exists():
            temp.unlink(missing_ok=True)


def atomic_write_yaml(path: Path, value: Any) -> None:
    atomic_write_text(path, yaml.safe_dump(value, allow_unicode=True, sort_keys=False, width=140))


def atomic_write_json(path: Path, value: Any) -> None:
    atomic_write_text(path, json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n")


def load_yaml(path: Path, default: Any = None) -> Any:
    if not path.exists():
        return default
    content = path.read_text(encoding="utf-8")
    if not content.strip():
        return default
    return yaml.safe_load(content)


def load_json(path: Path, default: Any = None) -> Any:
    if not path.exists():
        return default
    content = path.read_text(encoding="utf-8")
    if not content.strip():
        return default
    return json.loads(content)


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: Sequence[dict[str, Any]], fields: Sequence[str] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rows = list(rows)
    if fields is None:
        fields = list(rows[0].keys()) if rows else []
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=list(fields), extrasaction="ignore", lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    atomic_write_text(path, "\ufeff" + output.getvalue())


def root_from_script(script_file: str | Path) -> Path:
    return Path(script_file).resolve().parents[1]


@contextmanager
def continuity_lock(root: Path, timeout_seconds: int = 20) -> Iterator[None]:
    """使用 O_EXCL 建立跨进程锁；崩溃后按策略清理过期锁。"""
    lock_path = root / LOCK_FILE
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    token = {
        "pid": os.getpid(),
        "host": socket.gethostname(),
        "created_at": iso_utc(),
        "command": " ".join(os.sys.argv),
    }
    while True:
        try:
            fd = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                handle.write(json.dumps(token, ensure_ascii=False))
                handle.flush()
                os.fsync(handle.fileno())
            break
        except FileExistsError:
            try:
                age = time.time() - lock_path.stat().st_mtime
            except FileNotFoundError:
                continue
            if age > 600:
                lock_path.unlink(missing_ok=True)
                continue
            if time.monotonic() - started > timeout_seconds:
                detail = lock_path.read_text(encoding="utf-8", errors="replace") if lock_path.exists() else ""
                raise ContinuityError(f"接续状态被其他进程锁定：{detail}")
            time.sleep(0.2)
    try:
        yield
    finally:
        lock_path.unlink(missing_ok=True)


def run_command(
    args: Sequence[str],
    *,
    cwd: Path,
    check: bool = False,
    text: bool = True,
    timeout: int = 60,
    input_text: str | None = None,
) -> subprocess.CompletedProcess[str]:
    command = list(args)
    if command and command[0] == "git":
        git_override = os.environ.get("HHY_GIT_BIN")
        bundled_git = Path.home() / ".cache/codex-runtimes/codex-primary-runtime/dependencies/native/git/cmd/git.exe"
        git_executable = git_override or shutil.which("git") or (str(bundled_git) if bundled_git.is_file() else None)
        if not git_executable:
            raise ContinuityError("Git不可执行：请安装Git或设置HHY_GIT_BIN；不得把缺少Git误判为无历史源码并重建仓库")
        command[0] = git_executable
    try:
        result = subprocess.run(
            command,
            cwd=str(cwd),
            capture_output=True,
            text=text,
            input=input_text,
            timeout=timeout,
            encoding="utf-8" if text else None,
            errors="replace" if text else None,
        )
    except FileNotFoundError as exc:
        raise ContinuityError(f"命令不可执行：{command[0]}") from exc
    if check and result.returncode != 0:
        raise ContinuityError(
            f"命令失败 ({result.returncode})：{' '.join(args)}\n{result.stdout}\n{result.stderr}"
        )
    return result


def is_git_repo(root: Path) -> bool:
    result = run_command(["git", "rev-parse", "--is-inside-work-tree"], cwd=root)
    return result.returncode == 0 and result.stdout.strip() == "true"


def git(root: Path, *args: str, check: bool = False, timeout: int = 60) -> str:
    result = run_command(["git", *args], cwd=root, check=check, timeout=timeout)
    return result.stdout.strip()


def git_info(root: Path) -> dict[str, Any]:
    if not is_git_repo(root):
        return {
            "initialized": False,
            "branch": "NOT_INITIALIZED",
            "head": "NOT_INITIALIZED",
            "upstream": None,
            "ahead": None,
            "behind": None,
            "dirty": None,
            "status_porcelain": [],
            "recent_commits": [],
        }
    branch = git(root, "branch", "--show-current") or git(root, "symbolic-ref", "--short", "HEAD") or "DETACHED"
    head_result = run_command(["git", "rev-parse", "--verify", "HEAD"], cwd=root)
    head = head_result.stdout.strip() if head_result.returncode == 0 else "UNBORN"
    upstream_result = run_command(["git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"], cwd=root)
    upstream = upstream_result.stdout.strip() if upstream_result.returncode == 0 else None
    ahead = behind = None
    if upstream:
        counts = git(root, "rev-list", "--left-right", "--count", f"{upstream}...HEAD")
        if counts:
            behind, ahead = [int(part) for part in counts.split()]
    status_result = run_command(["git", "status", "--porcelain=v1", "--untracked-files=all"], cwd=root)
    status = status_result.stdout.splitlines()
    recent = [] if head == "UNBORN" else git(root, "log", "-n", "8", "--pretty=format:%H%x09%aI%x09%an%x09%s").splitlines()
    return {
        "initialized": True,
        "branch": branch,
        "head": head,
        "upstream": upstream,
        "ahead": ahead,
        "behind": behind,
        "dirty": bool(status),
        "status_porcelain": status,
        "recent_commits": recent,
    }


def git_has_concrete_head(info: dict[str, Any]) -> bool:
    """Return true only when Git is initialized and HEAD resolves to a commit."""
    return bool(info.get("initialized")) and info.get("head") not in {
        None,
        "",
        "NOT_INITIALIZED",
        "UNBORN",
    }


def parse_porcelain_line(line: str) -> tuple[str, str]:
    status = line[:2]
    path = line[3:] if len(line) >= 4 else ""
    if " -> " in path:
        path = path.split(" -> ", 1)[1]
    if path.startswith('"') and path.endswith('"'):
        # core.quotePath=false is installed by bootstrap, but retain a safe fallback.
        path = path[1:-1]
    return status, path


def git_changed_files(root: Path, *, staged: bool = False) -> list[str]:
    if not is_git_repo(root):
        return []
    if staged:
        output = git(root, "diff", "--cached", "--name-only", "--diff-filter=ACDMRTUXB")
        return sorted({line.strip() for line in output.splitlines() if line.strip()})
    status_result = run_command(["git", "status", "--porcelain=v1", "--untracked-files=all"], cwd=root)
    rows = status_result.stdout.splitlines()
    return sorted({parse_porcelain_line(row)[1] for row in rows if parse_porcelain_line(row)[1]})


def changed_since(root: Path, base_commit: str | None) -> list[str]:
    if not is_git_repo(root):
        return []
    if not base_commit or base_commit in {"NOT_INITIALIZED", "UNBORN"}:
        info = git_info(root)
        if info.get("head") not in {None, "NOT_INITIALIZED", "UNBORN"}:
            tracked = {line.strip() for line in git(root, "ls-files").splitlines() if line.strip()}
            return sorted(tracked | set(git_changed_files(root)))
        return git_changed_files(root)
    result = run_command(
        ["git", "diff", "--name-only", "--diff-filter=ACDMRTUXB", f"{base_commit}..HEAD"],
        cwd=root,
    )
    committed = {line.strip() for line in result.stdout.splitlines() if line.strip()}
    return sorted(committed | set(git_changed_files(root)))


def normalize_repo_path(value: str) -> str:
    normalized = value.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized.lstrip("/")


def is_managed_record(path: str) -> bool:
    normalized = normalize_repo_path(path)
    if normalized in MANAGED_RECORD_FILES:
        return True
    return any(normalized.startswith(normalize_repo_path(prefix)) for prefix in MANAGED_RECORD_PREFIXES)


def path_matches(path: str, pattern: str) -> bool:
    normalized = normalize_repo_path(path)
    pattern = normalize_repo_path(pattern)
    if pattern.endswith("/**"):
        return normalized == pattern[:-3] or normalized.startswith(pattern[:-2])
    if pattern.endswith("/"):
        return normalized.startswith(pattern)
    return fnmatch.fnmatch(normalized, pattern) or normalized.startswith(pattern.rstrip("*").rstrip("/"))


def filter_project_files(paths: Iterable[str]) -> list[str]:
    return sorted({p.replace("\\", "/") for p in paths if p and not is_managed_record(p)})


def canonical_fingerprint_bytes(content: bytes) -> bytes:
    """Make project fingerprints stable across Git CRLF checkout policies.

    Git stores normalized LF blobs for text files even when a Windows working
    tree uses CRLF.  Fingerprints intentionally ignore that transport-only
    difference so a checkpoint made before ``git add`` can be verified against
    the committed tree on every supported platform.
    """
    return content.replace(b"\r\n", b"\n")


def file_content_token(root: Path, relative: str) -> dict[str, Any]:
    path = root / relative
    if not path.exists() and not path.is_symlink():
        return {"path": relative, "state": "DELETED"}
    if path.is_symlink():
        return {"path": relative, "state": "SYMLINK", "target": os.readlink(path)}
    if path.is_dir():
        return {"path": relative, "state": "DIRECTORY"}
    content = canonical_fingerprint_bytes(path.read_bytes())
    return {
        "path": relative,
        "state": "FILE",
        "size": len(content),
        "sha256": hashlib.sha256(content).hexdigest(),
    }


def git_blob_content_tokens(root: Path, relative_paths: Sequence[str]) -> list[dict[str, Any]]:
    """Use portable index blobs, overlaying only tracked working-tree edits."""
    index: dict[str, str] = {}
    for record in git(root, "ls-files", "-s", "-z").split("\0"):
        if not record or "\t" not in record:
            continue
        metadata, relative = record.split("\t", 1)
        fields = metadata.split()
        if len(fields) >= 3 and fields[2] == "0":
            index[relative] = fields[1]
    modified = {
        row for row in git(root, "diff", "--name-only", "HEAD", "--").splitlines() if row
    }
    tokens: list[dict[str, Any]] = []
    for relative in relative_paths:
        path = root / relative
        if relative in modified:
            if not path.exists() and not path.is_symlink():
                tokens.append({"path": relative, "state": "DELETED"})
                continue
            object_id = git(root, "hash-object", f"--path={relative}", "--", relative)
        else:
            object_id = index.get(relative)
            if not object_id:
                raise ContinuityError(f"Git索引缺少已跟踪文件：{relative}")
        tokens.append({"path": relative, "state": "GIT_BLOB", "oid": object_id})
    return tokens


def portable_source_record(root: Path, path: Path) -> dict[str, Any]:
    """Describe a Context Pack source independently of Git checkout EOLs."""
    content = canonical_fingerprint_bytes(path.read_bytes())
    return {
        "path": path.relative_to(root).as_posix(),
        "sha256": hashlib.sha256(content).hexdigest(),
        "bytes": len(content),
    }


def project_fingerprint(root: Path, session: dict[str, Any] | None = None) -> dict[str, Any]:
    base = (session or {}).get("git", {}).get("base_commit")
    files = filter_project_files(changed_since(root, base))
    tokens = [file_content_token(root, relative) for relative in files]
    payload = {
        "base_commit": base or "NOT_INITIALIZED",
        "files": tokens,
    }
    return {
        "sha256": sha256_text(canonical_json(payload)),
        "files": files,
        "file_count": len(files),
        "payload": payload,
    }


def tree_fingerprint(root: Path) -> dict[str, Any]:
    tokens: list[dict[str, Any]] = []
    if is_git_repo(root):
        # A closed repository fingerprint represents the versioned source tree,
        # not runner caches, logs, or other untracked machine-local files.
        # Working-tree contents are still read so tracked edits and deletions
        # remain visible before the final metadata commit.
        tracked_files = [item for item in git(root, "ls-files", "-z").split("\0") if item]
        fingerprint_paths: list[str] = []
        for relative in sorted(tracked_files):
            if relative in {"MANIFEST_SHA256.txt", STATE_FILE} or is_managed_record(relative):
                continue
            if any(path_matches(relative, pattern) for pattern in PORTABLE_EXCLUDE_PATTERNS):
                continue
            fingerprint_paths.append(relative)
        tokens.extend(git_blob_content_tokens(root, fingerprint_paths))
        payload = {"files": tokens}
        return {"sha256": sha256_text(canonical_json(payload)), "file_count": len(tokens)}

    # Path.rglob descends into excluded dependency/build trees before filtering.
    # Prune them top-down so a cold resume remains fast in an installed checkout.
    for current, directories, filenames in os.walk(root, topdown=True):
        current_path = Path(current)
        retained_directories: list[str] = []
        for name in sorted(directories):
            candidate = (current_path / name).relative_to(root).as_posix()
            if name in FINGERPRINT_PRUNE_DIRECTORY_NAMES:
                continue
            if is_managed_record(candidate + "/"):
                continue
            if any(path_matches(candidate, pattern) for pattern in PORTABLE_EXCLUDE_PATTERNS):
                continue
            retained_directories.append(name)
        directories[:] = retained_directories

        for name in sorted(filenames):
            path = current_path / name
            relative = path.relative_to(root).as_posix()
            if relative in {"MANIFEST_SHA256.txt", STATE_FILE} or is_managed_record(relative):
                continue
            if any(path_matches(relative, pattern) for pattern in PORTABLE_EXCLUDE_PATTERNS):
                continue
            tokens.append(file_content_token(root, relative))
    tokens.sort(key=lambda token: token["path"])
    payload = {"files": tokens}
    return {"sha256": sha256_text(canonical_json(payload)), "file_count": len(tokens)}


def classify_paths(paths: Iterable[str], policy: dict[str, Any]) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    classes = policy.get("path_classes", {})
    for relative in sorted(set(paths)):
        matched = False
        for category, patterns in classes.items():
            if any(path_matches(relative, pattern) for pattern in patterns):
                result.setdefault(category, []).append(relative)
                matched = True
        if not matched:
            result.setdefault("other", []).append(relative)
    return result


def scope_allowed(path: str, allowed_paths: Sequence[str]) -> bool:
    if is_managed_record(path):
        return True
    return any(path_matches(path, pattern) for pattern in allowed_paths)


SECRET_SCAN_EXCLUDE_PATTERNS = (
    ".git/**",
    "**/.git/**",
    "**/node_modules/**",
    "**/dist/**",
    "**/target/**",
    "**/.gradle/**",
    "**/build/**",
    "**/__pycache__/**",
    "**/*.pyc",
    "**/*.pyo",
)

GENERIC_SECRET_SCAN_EXCLUDE_PATTERNS = (
    "docs/**",
    "templates/**",
    "catalogs/**",
    "contracts/**",
    "artifacts/**",
    "tests/fixtures/**",
    "**/*.example",
    "**/.env.example",
)

SENSITIVE_FILENAME_PATTERNS = (
    "**/*.pem",
    "**/*.key",
    "**/*.p12",
    "**/*.pfx",
    "**/*.jks",
    "**/*.keystore",
    "**/.env",
    "**/.env.*",
    "**/*private_key*",
)


def secret_scan(root: Path, paths: Iterable[str], max_bytes: int = 2_000_000) -> list[dict[str, str]]:
    """扫描真实秘密，同时允许文档/模板中的脱敏示例。

    便携交接的排除规则不能直接复用为安全扫描规则；否则名为 secret/key 的
    文件反而会被跳过。PEM、云访问键和危险文件名始终检测；通用赋值正则在
    文档、模板和示例文件中跳过，以避免把说明文字误判成生产秘密。
    """
    findings: list[dict[str, str]] = []
    for relative in sorted(set(paths)):
        if any(path_matches(relative, pattern) for pattern in SECRET_SCAN_EXCLUDE_PATTERNS):
            continue
        path = root / relative
        if not path.is_file() or path.stat().st_size > max_bytes:
            continue
        if any(path_matches(relative, pattern) for pattern in SENSITIVE_FILENAME_PATTERNS):
            # 明确允许的示例文件不作为真实秘密，但其他敏感扩展名必须阻断。
            if not relative.endswith((".example", ".example.env")) and not relative.endswith(".env.example"):
                findings.append({"path": relative, "rule": "sensitive_filename"})
        try:
            content = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for code, pattern in SUSPICIOUS_SECRET_REGEXES.items():
            if code == "generic_secret_assignment" and any(
                path_matches(relative, excluded) for excluded in GENERIC_SECRET_SCAN_EXCLUDE_PATTERNS
            ):
                continue
            if pattern.search(content):
                findings.append({"path": relative, "rule": code})
    # 同一路径/规则只报告一次。
    unique: dict[tuple[str, str], dict[str, str]] = {}
    for finding in findings:
        unique[(finding["path"], finding["rule"])] = finding
    return [unique[key] for key in sorted(unique)]


def load_policy(root: Path) -> dict[str, Any]:
    policy = load_yaml(root / POLICY_FILE, {})
    if not policy:
        raise ContinuityError(f"缺少接续策略：{POLICY_FILE}")
    return policy


def load_state(root: Path) -> dict[str, Any]:
    state = load_yaml(root / STATE_FILE, {})
    if not state:
        raise ContinuityError(f"缺少接续状态：{STATE_FILE}")
    return state


def save_state(root: Path, state: dict[str, Any]) -> None:
    state["updated_at"] = iso_utc()
    atomic_write_yaml(root / STATE_FILE, state)


def load_active_pointer(root: Path) -> dict[str, Any]:
    return load_yaml(root / ACTIVE_FILE, {"active_session_id": None, "status": "NONE"})


def save_active_pointer(root: Path, pointer: dict[str, Any]) -> None:
    pointer["updated_at"] = iso_utc()
    atomic_write_yaml(root / ACTIVE_FILE, pointer)


def session_record_path(root: Path, session_id: str) -> Path:
    return root / CONTINUITY_DIR / "sessions" / f"{session_id}.yaml"


def checkpoint_dir(root: Path, session_id: str) -> Path:
    return root / CONTINUITY_DIR / "checkpoints" / session_id


def load_session(root: Path, session_id: str) -> dict[str, Any]:
    record = load_yaml(session_record_path(root, session_id), {})
    if not record:
        raise ContinuityError(f"会话记录不存在：{session_id}")
    return record


def save_session(root: Path, session: dict[str, Any]) -> None:
    session["updated_at"] = iso_utc()
    atomic_write_yaml(session_record_path(root, session["session_id"]), session)


def current_session(root: Path, *, allow_handoff: bool = True) -> dict[str, Any] | None:
    pointer = load_active_pointer(root)
    session_id = pointer.get("active_session_id")
    if not session_id:
        return None
    session = load_session(root, session_id)
    allowed = {"ACTIVE", "HANDED_OFF", "CLOSING"} if allow_handoff else {"ACTIVE", "CLOSING"}
    if session.get("status") not in allowed:
        return None
    return session


def lease_is_expired(session: dict[str, Any], at: datetime | None = None) -> bool:
    expires = parse_iso(session.get("lease", {}).get("expires_at"))
    return bool(expires and (at or now_utc()) > expires)


def renew_lease(session: dict[str, Any], policy: dict[str, Any], at: datetime | None = None) -> None:
    at = at or now_utc()
    duration = int(policy.get("lease", {}).get("duration_minutes", 240))
    session.setdefault("lease", {})
    session["lease"].update(
        {
            "duration_minutes": duration,
            "renewed_at": iso_utc(at),
            "expires_at": iso_utc(at + timedelta(minutes=duration)),
        }
    )


def append_event(root: Path, event_type: str, payload: dict[str, Any]) -> dict[str, Any]:
    state = load_state(root)
    event_meta = state.setdefault("event_log", {"sequence": 0, "head_hash": "0" * 64})
    sequence = int(event_meta.get("sequence", 0)) + 1
    previous = event_meta.get("head_hash", "0" * 64)
    body = {
        "sequence": sequence,
        "timestamp": iso_utc(),
        "event_type": event_type,
        "payload": payload,
        "previous_hash": previous,
    }
    event_hash = sha256_text(previous + canonical_json(body))
    event = {**body, "event_hash": event_hash}
    log_path = root / EVENT_LOG_FILE
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write(canonical_json(event) + "\n")
        handle.flush()
        os.fsync(handle.fileno())
    event_meta["sequence"] = sequence
    event_meta["head_hash"] = event_hash
    event_meta["last_event_type"] = event_type
    event_meta["last_event_at"] = event["timestamp"]
    save_state(root, state)
    try:
        sync_continuity_catalogs(root)
    except Exception:
        # CSV是派生投影，不得阻断事件事实写入；doctor会报告投影问题。
        pass
    return event


def validate_event_chain(root: Path) -> dict[str, Any]:
    path = root / EVENT_LOG_FILE
    previous = "0" * 64
    sequence = 0
    errors: list[str] = []
    if not path.exists():
        return {"valid": False, "events": 0, "head_hash": previous, "errors": ["事件日志不存在"]}
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError as exc:
                errors.append(f"第{line_no}行不是合法JSON：{exc}")
                continue
            sequence += 1
            expected_body = {
                "sequence": event.get("sequence"),
                "timestamp": event.get("timestamp"),
                "event_type": event.get("event_type"),
                "payload": event.get("payload"),
                "previous_hash": event.get("previous_hash"),
            }
            expected_hash = sha256_text(previous + canonical_json(expected_body))
            if event.get("sequence") != sequence:
                errors.append(f"事件序号不连续：期望{sequence}，实际{event.get('sequence')}")
            if event.get("previous_hash") != previous:
                errors.append(f"事件{sequence} previous_hash错误")
            if event.get("event_hash") != expected_hash:
                errors.append(f"事件{sequence} event_hash错误")
            previous = event.get("event_hash", previous)
    state = load_state(root)
    meta = state.get("event_log", {})
    if meta.get("sequence") != sequence:
        errors.append("STATE中的事件序号与日志不一致")
    if meta.get("head_hash") != previous:
        errors.append("STATE中的事件头哈希与日志不一致")
    return {"valid": not errors, "events": sequence, "head_hash": previous, "errors": errors}


def load_index(root: Path, relative: str, key: str) -> dict[str, Any]:
    value = load_yaml(root / relative, {}) or {}
    value.setdefault("version", PROTOCOL_VERSION)
    value.setdefault(key, [])
    return value


def append_session_index(root: Path, session: dict[str, Any]) -> None:
    index = load_index(root, SESSION_INDEX_FILE, "sessions")
    rows = index["sessions"]
    rows.append(
        {
            "session_id": session["session_id"],
            "task_id": session["task_id"],
            "story_id": session.get("story_id"),
            "actor_id": session["actor"]["id"],
            "status": session["status"],
            "started_at": session["started_at"],
            "record": str(session_record_path(root, session["session_id"]).relative_to(root)).replace("\\", "/"),
            "session_log": session["session_log"],
        }
    )
    atomic_write_yaml(root / SESSION_INDEX_FILE, index)


def update_session_index(root: Path, session: dict[str, Any]) -> None:
    index = load_index(root, SESSION_INDEX_FILE, "sessions")
    found = False
    for row in index["sessions"]:
        if row.get("session_id") == session["session_id"]:
            row.update(
                {
                    "status": session["status"],
                    "updated_at": session.get("updated_at") or iso_utc(),
                    "closed_at": session.get("closed_at"),
                    "latest_checkpoint": session.get("latest_checkpoint"),
                    "handoff_bundle": session.get("handoff_bundle"),
                }
            )
            found = True
            break
    if not found:
        append_session_index(root, session)
        return
    atomic_write_yaml(root / SESSION_INDEX_FILE, index)


def claim_task(root: Path, session: dict[str, Any]) -> None:
    claims = load_index(root, TASK_CLAIMS_FILE, "claims")
    for row in claims["claims"]:
        if row.get("status") == "ACTIVE" and (
            row.get("task_id") == session["task_id"]
            or (session.get("story_id") and row.get("story_id") == session.get("story_id"))
        ):
            raise ContinuityError(
                f"任务或故事已被会话 {row.get('session_id')} 领取；不得并发修改同一事实范围"
            )
    claims["claims"].append(
        {
            "claim_id": f"CLM-{uuid.uuid4().hex[:12].upper()}",
            "session_id": session["session_id"],
            "task_id": session["task_id"],
            "story_id": session.get("story_id"),
            "actor_id": session["actor"]["id"],
            "status": "ACTIVE",
            "claimed_at": session["started_at"],
            "allowed_paths": session["scope"]["allowed_paths"],
        }
    )
    atomic_write_yaml(root / TASK_CLAIMS_FILE, claims)


def close_task_claim(root: Path, session: dict[str, Any], status: str) -> None:
    claims = load_index(root, TASK_CLAIMS_FILE, "claims")
    for row in claims["claims"]:
        if row.get("session_id") == session["session_id"] and row.get("status") == "ACTIVE":
            row["status"] = status
            row["closed_at"] = iso_utc()
    atomic_write_yaml(root / TASK_CLAIMS_FILE, claims)


def read_next_task(root: Path) -> dict[str, Any]:
    task = load_yaml(root / "NEXT_TASK.yaml", {})
    if not task:
        raise ContinuityError("NEXT_TASK.yaml为空")
    return task


def read_current_status(root: Path) -> dict[str, Any]:
    status = load_yaml(root / "CURRENT_STATUS.yaml", {})
    if not status:
        raise ContinuityError("CURRENT_STATUS.yaml为空")
    return status


def release_task(root: Path, release: str, task_id: str) -> dict[str, Any]:
    document = load_yaml(root / "releases" / release / "TASKS.yaml", {})
    for row in document.get("tasks", []):
        if row.get("id") == task_id:
            return row
    raise ContinuityError(f"{release} 中不存在任务 {task_id}")


def release_story(root: Path, release: str, story_id: str | None) -> dict[str, Any] | None:
    document = load_yaml(root / "releases" / release / "STORIES.yaml", {})
    stories = document.get("stories", [])
    if story_id:
        for row in stories:
            if row.get("story_id") == story_id:
                return row
        raise ContinuityError(f"{release} 中不存在故事 {story_id}")
    ready = [row for row in stories if row.get("status") == "READY_FOR_IMPLEMENTATION"]
    if len(ready) == 1:
        return ready[0]
    return None


def derive_scope(policy: dict[str, Any], story: dict[str, Any] | None, explicit: Sequence[str]) -> list[str]:
    paths: list[str] = []
    scope_rules = policy.get("scope_rules", {})
    if story:
        platform = story.get("platform", "CROSS_PLATFORM")
        for item in scope_rules.get(platform, scope_rules.get("CROSS_PLATFORM", [])):
            if item not in paths:
                paths.append(item)
    if not paths:
        paths.extend(scope_rules.get("CROSS_PLATFORM", ["**"]))
    for item in explicit:
        normalized = item.replace("\\", "/").strip()
        if normalized and normalized not in paths:
            paths.append(normalized)
    # Every successful task close writes the user-visible changelog.  This is
    # mandatory closure metadata rather than an optional platform artefact, so
    # every session must be able to produce it regardless of story platform.
    if "CHANGELOG.md" not in paths:
        paths.append("CHANGELOG.md")
    return paths


def validate_scope(paths: Sequence[str], allowed_paths: Sequence[str]) -> list[str]:
    return sorted([path for path in paths if not scope_allowed(path, allowed_paths)])


def update_current_status_for_session(root: Path, session: dict[str, Any], checkpoint: dict[str, Any] | None = None) -> None:
    status = read_current_status(root)
    status.update(
        {
            "baseline_version": PACKAGE_VERSION,
            "phase": session["release"],
            "active_release": session["release"],
            "active_task": session["task_id"],
            "status": "IN_PROGRESS" if session["status"] == "ACTIVE" else session["status"],
            "next_task": session["task_id"],
            "updated_at": iso_utc(),
            "continuity": {
                "protocol_version": PROTOCOL_VERSION,
                "mode": "ENFORCED",
                "active_session_id": session["session_id"],
                "actor_id": session["actor"]["id"],
                "story_id": session.get("story_id"),
                "lease_expires_at": session.get("lease", {}).get("expires_at"),
                "latest_checkpoint": session.get("latest_checkpoint"),
                "project_fingerprint": (checkpoint or {}).get("project_fingerprint", {}).get("sha256"),
                "context_pack": session.get("context_pack"),
                "handoff_bundle": session.get("handoff_bundle"),
            },
        }
    )
    in_progress = list(status.get("in_progress_tasks", []))
    if session["task_id"] not in in_progress:
        in_progress.append(session["task_id"])
    status["in_progress_tasks"] = in_progress
    atomic_write_yaml(root / "CURRENT_STATUS.yaml", status)


def update_current_status_closed(
    root: Path,
    session: dict[str, Any],
    *,
    result: str,
    next_task_id: str | None,
    code_commit: str | None = None,
) -> None:
    status = read_current_status(root)
    completed = list(status.get("completed_tasks", []))
    in_progress = [row for row in status.get("in_progress_tasks", []) if row != session["task_id"]]
    blocked = list(status.get("blocked_tasks", []))
    if result == "COMPLETED" and session["task_id"] not in completed:
        completed.append(session["task_id"])
    if result == "BLOCKED" and session["task_id"] not in blocked:
        blocked.append(session["task_id"])
    next_document = read_next_task(root) if next_task_id else {}
    next_release = next_document.get("release") or session.get("release")
    status.update(
        {
            "baseline_version": PACKAGE_VERSION,
            "phase": next_release,
            "active_release": next_release,
            "status": "READY" if result == "COMPLETED" else result,
            "active_task": next_task_id,
            "next_task": next_task_id,
            "completed_tasks": completed,
            "in_progress_tasks": in_progress,
            "blocked_tasks": blocked,
            "updated_at": iso_utc(),
            "continuity": {
                "protocol_version": PROTOCOL_VERSION,
                "mode": "ENFORCED",
                "active_session_id": None,
                "last_session_id": session["session_id"],
                "last_session_result": result,
                "last_checkpoint": session.get("latest_checkpoint"),
                "last_handoff_bundle": session.get("handoff_bundle"),
                "context_pack": session.get("context_pack"),
            },
        }
    )
    if result == "COMPLETED" and code_commit:
        status["last_green_commit"] = code_commit
    atomic_write_yaml(root / "CURRENT_STATUS.yaml", status)


def make_session_id() -> str:
    return f"SES-{now_utc().strftime('%Y%m%dT%H%M%SZ')}-{uuid.uuid4().hex[:8].upper()}"


def make_checkpoint_id(session_id: str, sequence: int) -> str:
    return f"CP-{session_id}-{sequence:04d}"


def markdown_session_log(session: dict[str, Any]) -> str:
    crs = ", ".join(session.get("change_requests", [])) or "无"
    return f"""---
session_id: {session['session_id']}
protocol_version: {PROTOCOL_VERSION}
status: {session['status']}
actor_id: {session['actor']['id']}
task_id: {session['task_id']}
story_id: {session.get('story_id') or 'N/A'}
release: {session['release']}
started_at: {session['started_at']}
base_commit: {session['git']['base_commit']}
---
# 开发会话记录 · {session['session_id']}

## 任务与目标

- Release：`{session['release']}`
- Task：`{session['task_id']}`
- Story：`{session.get('story_id') or 'N/A'}`
- Actor：`{session['actor']['id']}`
- 目标：{session['goal']}
- 关联 CR：{crs}
- 允许路径：`{';'.join(session['scope']['allowed_paths'])}`

## 开始状态

- 开始时间：{session['started_at']}
- 分支：`{session['git']['branch']}`
- 起始 Commit：`{session['git']['base_commit']}`
- 工作区：{session['git']['initial_worktree_state']}
- 租约到期：{session['lease']['expires_at']}

## 检查点

尚未创建检查点。编码、切换上下文、执行重要测试或交接前必须运行 `continuity.py checkpoint`。

## 决策与变更

暂无。冻结事实变化必须关联已批准 CR。

## 测试与证据

暂无。每个检查点必须记录本阶段测试或明确未执行原因。

## 阻塞与风险

暂无。

## 未完成与下一步

{session.get('next_step') or '按 NEXT_TASK.yaml 和 Story 开始。'}

## Commit / PR / Handoff

- Commit：待检查点自动记录
- PR：待填写
- Handoff：无
"""


def append_checkpoint_to_log(root: Path, session: dict[str, Any], checkpoint: dict[str, Any]) -> None:
    log_path = root / session["session_log"]
    text = log_path.read_text(encoding="utf-8") if log_path.exists() else markdown_session_log(session)
    block = [
        "",
        f"### {checkpoint['checkpoint_id']} · {checkpoint['created_at']}",
        "",
        f"- 摘要：{checkpoint['summary']}",
        f"- 下一步：{checkpoint['next_step']}",
        f"- 项目指纹：`{checkpoint['project_fingerprint']['sha256']}`",
        f"- 变更文件：{checkpoint['project_fingerprint']['file_count']} 个",
        f"- 分类：`{', '.join(sorted(checkpoint['change_classification'])) or 'none'}`",
        f"- 阻塞：{'; '.join(checkpoint.get('blockers', [])) or '无'}",
        f"- 决策：{'; '.join(checkpoint.get('decisions', [])) or '无'}",
        "- 测试：",
    ]
    tests = checkpoint.get("tests", [])
    if tests:
        for item in tests:
            block.append(
                f"  - `{item.get('result','NOT_RUN')}` {item.get('name','未命名')} — {item.get('evidence') or item.get('note') or '无证据路径'}"
            )
    else:
        block.append("  - `NOT_RUN` 本检查点未执行测试；原因已在机器检查点记录。")
    block.extend(
        [
            "- 关键文件：",
            *[f"  - `{path}`" for path in checkpoint["project_fingerprint"]["files"][:120]],
            "",
        ]
    )
    atomic_write_text(log_path, text.rstrip() + "\n" + "\n".join(block))


def parse_test_spec(value: str) -> dict[str, str]:
    # 推荐格式：name|PASS|artifacts/report.md|note
    parts = value.split("|", 3)
    parts += [""] * (4 - len(parts))
    name, result, evidence, note = [part.strip() for part in parts]
    result = (result or "NOT_RUN").upper()
    if result not in {"PASS", "FAIL", "NOT_RUN", "SKIPPED"}:
        raise ContinuityError(f"测试结果必须是 PASS/FAIL/NOT_RUN/SKIPPED：{value}")
    return {"name": name or "unnamed", "result": result, "evidence": evidence, "note": note}


def active_change_requests(root: Path, session: dict[str, Any]) -> list[dict[str, Any]]:
    index = load_index(root, CR_INDEX_FILE, "change_requests")
    linked = set(session.get("change_requests", []))
    return [row for row in index["change_requests"] if row.get("cr_id") in linked]


def change_requirements(
    classification: dict[str, list[str]],
    *,
    commit_message: str | None = None,
) -> list[str]:
    required = ["SESSION_RECORD", "SESSION_LOG", "CHECKPOINT", "CURRENT_STATUS", "EVENT_LOG"]
    if classification.get("source_of_truth"):
        required.append("APPROVED_CHANGE_REQUEST")
    if classification.get("database"):
        required.extend(["DATABASE_TEST_EVIDENCE", "SCHEMA_TRACEABILITY"])
    if classification.get("contracts"):
        required.extend(["CONTRACT_TEST_EVIDENCE", "GENERATED_CLIENTS_OR_GENERATION_RECORD"])
    if classification.get("user_visible"):
        required.append("CHANGELOG")
    message = (commit_message or "").lower()
    if re.search(r"\bfix(?:es|ed)?\b|\bbug\b|修复|缺陷", message):
        required.extend(["PROBLEM_REGISTRY", "REGRESSION_TEST"])
    return list(dict.fromkeys(required))


def check_required_change_request(root: Path, session: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    records = active_change_requests(root, session)
    if not records:
        return ["冻结事实发生变化，但会话未关联 CR"]
    valid = [row for row in records if row.get("status") in {"APPROVED", "IMPLEMENTING", "IMPLEMENTED", "CLOSED"}]
    if not valid:
        errors.append("关联 CR 尚未批准或进入 IMPLEMENTING")
    for row in valid:
        if row.get("requester_actor_id") == row.get("approver_actor_id"):
            errors.append(f"{row.get('cr_id')} 申请人与审批人相同")
    return errors


def allowed_path_errors(session: dict[str, Any], project_files: Sequence[str]) -> list[str]:
    outside = validate_scope(project_files, session.get("scope", {}).get("allowed_paths", []))
    exceptions = set(session.get("scope", {}).get("approved_exceptions", []))
    outside = [path for path in outside if path not in exceptions]
    return outside


def latest_checkpoint(root: Path, session: dict[str, Any]) -> dict[str, Any] | None:
    relative = session.get("latest_checkpoint")
    if not relative:
        return None
    return load_yaml(root / relative, {})


def load_checkpoint_by_id(root: Path, session_id: str, checkpoint_id: str) -> dict[str, Any] | None:
    """Load a historical checkpoint by its immutable ID.

    CI validates every commit, so it must resolve the checkpoint referenced by that
    commit rather than comparing all commits with the session's latest checkpoint.
    """
    directory = checkpoint_dir(root, session_id)
    if not directory.exists():
        return None
    for path in sorted(directory.glob("*.yaml")):
        record = load_yaml(path, {}) or {}
        if record.get("checkpoint_id") == checkpoint_id:
            return record
    return None


def _trailer_atom(value: Any) -> str:
    text = str(value or "").strip()
    text = re.sub(r"[\r\n|;,]+", "_", text)
    text = re.sub(r"\s+", "+", text)
    return text[:120] or "unnamed"


def checkpoint_tests_trailer(checkpoint: dict[str, Any] | None) -> str:
    """Return a deterministic machine-verifiable Tests trailer."""
    rows = list((checkpoint or {}).get("tests", []))
    if not rows:
        return "NONE"
    order = ["PASS", "FAIL", "NOT_RUN", "SKIPPED"]
    grouped: dict[str, list[str]] = {key: [] for key in order}
    for row in rows:
        result = str(row.get("result") or "NOT_RUN").upper()
        grouped.setdefault(result, []).append(_trailer_atom(row.get("name")))
    parts = []
    for result in order + sorted(set(grouped) - set(order)):
        names = sorted(set(grouped.get(result, [])))
        if names:
            parts.append(f"{result}:{','.join(names)}")
    return ";".join(parts) or "NONE"


def checkpoint_cr_trailer(checkpoint: dict[str, Any] | None) -> str:
    crs = sorted({str(value).strip() for value in (checkpoint or {}).get("change_requests", []) if str(value).strip()})
    return ",".join(crs) if crs else "NONE"


def expected_commit_trailers(session: dict[str, Any], checkpoint: dict[str, Any]) -> dict[str, str]:
    trailers = {
        "Task-ID": str(session.get("task_id") or ""),
        "Session-ID": str(session.get("session_id") or ""),
        "Checkpoint-ID": str(checkpoint.get("checkpoint_id") or ""),
        "Tests": checkpoint_tests_trailer(checkpoint),
        "CR": checkpoint_cr_trailer(checkpoint),
    }
    if session.get("story_id"):
        trailers["Story-ID"] = str(session["story_id"])
    return trailers


def context_source_paths(
    root: Path,
    session: dict[str, Any] | None,
    release: str | None = None,
) -> list[Path]:
    paths = [
        root / "AGENTS.md",
        root / "START_HERE.md",
        root / "CURRENT_STATUS.yaml",
        root / "NEXT_TASK.yaml",
        root / "DEVELOPMENT_RISK_REGISTER.md",
        root / "docs/00-baseline/SOURCE_OF_TRUTH.md",
        root / "docs/03-continuity/PROBLEM_REGISTRY.yaml",
        root / "docs/03-continuity/REUSABLE_PATTERNS.md",
        root / "docs/03-continuity/PITFALLS.md",
        root / "releases/PROGRAM_EXECUTION_PLAN.yaml",
        root / "config/REPOSITORY_TRANSPORT.yaml",
        root / "config/DEVELOPMENT_RUNTIME.yaml",
        root / POLICY_FILE,
        root / EVENT_LOG_FILE,
        root / SESSION_INDEX_FILE,
        root / TASK_CLAIMS_FILE,
        root / TASK_TRANSITIONS_FILE,
        root / CR_INDEX_FILE,
        # STATE_FILE is updated with the context hash after generation; including it
        # would make every freshly generated Context Pack immediately stale.
        root / ACTIVE_FILE,
    ]
    release = (session or {}).get("release") or release
    if release:
        paths.extend(
            [
                root / "releases" / release / "RELEASE_MANIFEST.yaml",
                root / "releases" / release / "DEFINITION_OF_READY.yaml",
                root / "releases" / release / "STORIES.yaml",
                root / "releases" / release / "TASKS.yaml",
                root / "releases" / release / "ACCEPTANCE_MATRIX.csv",
                root / "releases" / release / "PARALLEL_EXECUTION_PLAN.yaml",
            ]
        )
    if session:
        # The machine session record receives the generated Context Pack pointer
        # after generation, so it must not fingerprint itself.
        paths.append(root / session["session_log"])
        if session.get("latest_checkpoint"):
            paths.append(root / session["latest_checkpoint"])
        for cr_id in session.get("change_requests", []):
            candidates = list((root / "docs/03-continuity/change-requests").glob(f"{cr_id}*.md"))
            paths.extend(candidates)
    return [path for path in paths if path.exists()]


def open_change_requests(root: Path) -> list[dict[str, Any]]:
    index = load_index(root, CR_INDEX_FILE, "change_requests")
    return [row for row in index["change_requests"] if row.get("status") not in {"CLOSED", "REJECTED", "SUPERSEDED"}]


def build_context_pack(root: Path, session: dict[str, Any] | None = None) -> dict[str, Any]:
    session = session or current_session(root)
    status = read_current_status(root)
    next_task = read_next_task(root)
    git_state = git_info(root)
    checkpoint = latest_checkpoint(root, session) if session else None
    release = (session or {}).get("release") or status.get("active_release") or next_task.get("release")
    source_paths = context_source_paths(root, session, release)
    source_manifest = [portable_source_record(root, path) for path in source_paths]
    fingerprint = project_fingerprint(root, session) if session else {
        "sha256": tree_fingerprint(root)["sha256"],
        "files": [],
        "file_count": 0,
    }
    release_docs: dict[str, Any] = {}
    if release:
        for name in ["RELEASE_MANIFEST.yaml", "DEFINITION_OF_READY.yaml", "STORIES.yaml", "TASKS.yaml", "PARALLEL_EXECUTION_PLAN.yaml"]:
            path = root / "releases" / release / name
            if path.exists():
                release_docs[name] = load_yaml(path, {})
    policy = load_policy(root)
    repository_transport = load_yaml(root / "config/REPOSITORY_TRANSPORT.yaml", {})
    development_runtime = load_yaml(root / "config/DEVELOPMENT_RUNTIME.yaml", {})
    bootstrap_tasks = set(policy.get("bootstrap", {}).get("allow_without_git_task_ids", []))
    handoff_instruction = (
        f"python3 scripts/continuity.py takeover --actor <NEW_ACTOR> --session {session['session_id']}"
        if session and session.get("status") == "HANDED_OFF"
        else (
            f"python3 scripts/continuity.py checkpoint --summary '<完成内容>' --next-step '<下一步>' --parallel-assessment <ASSESSMENT> --parallel-reason '<未委托原因>'"
            if session
            else (
                f"python3 scripts/continuity.py bootstrap --actor <ACTOR_ID> --init-git --initial-commit --task {next_task.get('id')} --branch task/{next_task.get('id')}"
                if not git_has_concrete_head(git_state) and next_task.get("id") in bootstrap_tasks
                else f"python3 scripts/continuity.py start --actor <ACTOR_ID> --task {next_task.get('id')}"
            )
        )
    )
    state = load_state(root)
    event_chain = validate_event_chain(root)
    session_rows = load_index(root, SESSION_INDEX_FILE, "sessions").get("sessions", [])
    claim_rows = load_index(root, TASK_CLAIMS_FILE, "claims").get("claims", [])
    transition_rows = load_index(root, TASK_TRANSITIONS_FILE, "transitions").get("transitions", [])
    embedded_session = dict(session) if session else None
    if embedded_session is not None:
        embedded_session["context_pack"] = "THIS_CONTEXT_PACK"
    payload = {
        "context_version": "1.0",
        "generated_at": iso_utc(),
        "conversation_dependency": "PROHIBITED",
        "source_of_truth": "REPOSITORY_ONLY",
        "project": "hhy-pro-platform",
        "package_version": PACKAGE_VERSION,
        "current_status": status,
        "next_task": next_task,
        "active_session": embedded_session,
        "latest_checkpoint": checkpoint,
        "git": git_state,
        "project_fingerprint": fingerprint,
        "release_context": release_docs,
        "parallel_development_policy": policy.get("parallel_development", {}),
        "execution_routing_policy": development_runtime.get("model_routing", {}),
        "development_runtime": development_runtime,
        "repository_transport": repository_transport,
        "continuity_state": {
            "mode": state.get("mode"),
            "protocol_version": state.get("protocol_version"),
            "active_session_id": state.get("active_session_id"),
            "last_session_id": state.get("last_session_id"),
            "last_session_result": state.get("last_session_result"),
            "last_closure_checkpoint_id": state.get("last_closure_checkpoint_id"),
            "event_count": event_chain.get("events"),
            "event_head_hash": event_chain.get("head_hash"),
            "event_chain_valid": event_chain.get("valid"),
        },
        "recent_sessions": session_rows[-10:],
        "task_claims": claim_rows[-20:],
        "recent_task_transitions": transition_rows[-20:],
        "open_change_requests": open_change_requests(root),
        "source_manifest": source_manifest,
        "exact_resume_command": handoff_instruction,
        "hard_rules": [
            "不得依赖旧对话补充仓库已有需求",
            "未领取任务和会话不得编辑项目文件",
            "项目内容变化后必须先创建检查点再提交",
            "冻结事实变化必须关联已批准CR",
            "交接必须生成Handoff Bundle或完成干净提交",
            "存在安全且路径互斥的工作包时主控自动委托1至3个执行代理，无需逐次用户确认",
            "未委托或运行环境不支持代理时必须记录原因，禁止伪造并行证据",
            "复杂/高风险使用Sol，中等使用Terra，轻量使用Luna；不可用时记录目标、实际模型和回退原因，不得伪称",
            "除非项目所有者明确报告未连接，默认Codex已连接obx-test且既有项目环境可用；预检失败必须阻断并报告",
            "禁止以无服务器状态继续开发或重建本地Android SDK，必须复用登记的远端镜像和Gradle缓存",
            "Git remote/upstream仅按tracked transport descriptor受控恢复；推送前必须预检且禁止force push",
        ],
    }
    context_hash = sha256_text(canonical_json(payload))
    payload["context_hash"] = context_hash
    output_dir = root / "artifacts/context"
    output_dir.mkdir(parents=True, exist_ok=True)
    yaml_path = output_dir / "CURRENT_CONTEXT_PACK.yaml"
    md_path = output_dir / "CURRENT_CONTEXT_PACK.md"
    manifest_path = output_dir / "CURRENT_CONTEXT_PACK_MANIFEST.json"
    atomic_write_yaml(yaml_path, payload)

    def dump_yaml(value: Any) -> str:
        return yaml.safe_dump(value, allow_unicode=True, sort_keys=False, width=140).rstrip()

    changed_lines = "\n".join(f"- `{path}`" for path in fingerprint.get("files", [])[:200]) or "- 无"
    source_lines = "\n".join(
        f"- `{row['path']}` — `{row['sha256']}`" for row in source_manifest
    )
    markdown = f"""# CURRENT CONTEXT PACK · 无对话接续上下文

- 生成时间：{payload['generated_at']}
- Context Hash：`{context_hash}`
- 对话依赖：`PROHIBITED`
- 事实源：`REPOSITORY_ONLY`
- 精确恢复命令：

```bash
{handoff_instruction}
```

## 当前状态

```yaml
{dump_yaml(status)}
```

## 默认并行规则

```yaml
{dump_yaml(payload['parallel_development_policy'])}
```

## 模型路由与云端既有环境

```yaml
execution_routing_policy: {dump_yaml(payload['execution_routing_policy'])}
development_runtime: {dump_yaml(payload['development_runtime'])}
```

## 仓库传输恢复

```yaml
{dump_yaml(payload['repository_transport'])}
```

## 下一任务

```yaml
{dump_yaml(next_task)}
```

## 活跃会话

```yaml
{dump_yaml(payload['active_session'] or {'status': 'NONE'})}
```

## 最新检查点

```yaml
{dump_yaml(checkpoint or {'status': 'NO_CHECKPOINT'})}
```

## 接续状态与事件头

```yaml
{dump_yaml(payload['continuity_state'])}
```

## 最近会话与任务迁移

```yaml
recent_sessions: {dump_yaml(payload['recent_sessions'])}
task_claims: {dump_yaml(payload['task_claims'])}
recent_task_transitions: {dump_yaml(payload['recent_task_transitions'])}
```

## Git 状态

```yaml
{dump_yaml(git_state)}
```

## 会话累计项目变更

- 指纹：`{fingerprint.get('sha256')}`
- 文件数：{fingerprint.get('file_count')}

{changed_lines}

## 当前 Release

```yaml
{dump_yaml(release_docs)}
```

## 开放 CR

```yaml
{dump_yaml(payload['open_change_requests'])}
```

## 上下文来源及哈希

{source_lines}

## 接手硬规则

1. 先运行精确恢复命令，不得直接编辑。
2. 不得要求用户重新输入仓库已有需求。
3. 所有变更必须在活跃会话、任务和故事范围内。
4. 每次上下文切换、关键测试、提交和交接前必须创建检查点。
5. 冻结事实变化必须关联已批准 CR。
6. 存在安全且路径互斥的工作包时自动委托 1 至 3 个执行代理，无需逐次用户确认。
7. 未委托或运行时不支持代理时必须记录原因，禁止伪造并行证据。
8. 复杂/高风险使用 Sol，中等使用 Terra，轻量使用 Luna；不可用时记录实际回退，禁止虚构模型使用记录。
9. 除非项目所有者明确报告未连接，默认 Codex 已连接 `obx-test` 且既有项目环境可用；预检失败必须阻断并报告。
10. 禁止以无服务器状态开发或重建本地 Android SDK；只能复用登记的远端镜像和 Gradle 缓存。
11. Git remote/upstream 仅按 tracked transport descriptor 受控恢复，推送前必须通过 preflight，禁止 force push。
"""
    atomic_write_text(md_path, markdown)
    manifest = {
        "version": "1.0",
        "generated_at": payload["generated_at"],
        "context_hash": context_hash,
        "fingerprint_mode": "SESSION_DELTA" if session else "REPOSITORY_TREE",
        "project_fingerprint": {
            "sha256": fingerprint.get("sha256"),
            "file_count": fingerprint.get("file_count"),
        },
        "yaml": {"path": yaml_path.relative_to(root).as_posix(), "sha256": sha256_file(yaml_path)},
        "markdown": {"path": md_path.relative_to(root).as_posix(), "sha256": sha256_file(md_path)},
        "sources": source_manifest,
    }
    atomic_write_json(manifest_path, manifest)
    if session:
        session["context_pack"] = {
            "yaml": yaml_path.relative_to(root).as_posix(),
            "markdown": md_path.relative_to(root).as_posix(),
            "manifest": manifest_path.relative_to(root).as_posix(),
            "context_hash": context_hash,
            "generated_at": payload["generated_at"],
        }
        save_session(root, session)
    state = load_state(root)
    state["context_pack"] = {
        "path": md_path.relative_to(root).as_posix(),
        "hash": context_hash,
        "generated_at": payload["generated_at"],
    }
    save_state(root, state)
    return payload


def context_is_fresh(root: Path, session: dict[str, Any] | None = None) -> tuple[bool, str]:
    manifest = load_json(root / "artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json", {})
    if not manifest:
        return False, "Context Pack Manifest不存在"
    # Context Pack自身也必须可验证，不能只校验它所引用的来源文件。
    for key in ("yaml", "markdown"):
        record = manifest.get(key, {})
        path_value = record.get("path")
        expected = record.get("sha256")
        if not path_value or not expected:
            return False, f"Context Pack Manifest缺少{key}记录"
        path = root / path_value
        if not path.exists():
            return False, f"Context Pack文件缺失：{path_value}"
        if sha256_file(path) != expected:
            return False, f"Context Pack文件被修改：{path_value}"
    yaml_path = root / manifest["yaml"]["path"]
    payload = load_yaml(yaml_path, {}) or {}
    embedded_hash = payload.pop("context_hash", None)
    calculated_hash = sha256_text(canonical_json(payload))
    if not embedded_hash or embedded_hash != calculated_hash or manifest.get("context_hash") != calculated_hash:
        return False, "Context Pack内容哈希不一致"
    for source in manifest.get("sources", []):
        path = root / source["path"]
        if not path.exists():
            return False, f"Context Pack来源缺失：{source['path']}"
        if portable_source_record(root, path)["sha256"] != source["sha256"]:
            return False, f"Context Pack已过期：{source['path']}发生变化"
    if session:
        checkpoint = latest_checkpoint(root, session)
        if checkpoint:
            current = project_fingerprint(root, session)["sha256"]
            if current != checkpoint.get("project_fingerprint", {}).get("sha256"):
                return False, "项目内容在最新检查点后发生变化"
    else:
        expected_tree = manifest.get("project_fingerprint", {}).get("sha256")
        current_tree = tree_fingerprint(root)["sha256"]
        if not expected_tree or current_tree != expected_tree:
            return False, "仓库树在Context Pack生成后发生变化"
    return True, "PASS"


def normalize_parallel_execution(policy: dict[str, Any], value: dict[str, Any] | None) -> dict[str, Any]:
    execution = dict(value or {})
    assessment = str(execution.get("assessment") or "").upper()
    allowed_assessments = {"DELEGATED", "NO_SAFE_PARALLEL", "CAPABILITY_UNAVAILABLE", "USER_SERIAL_OVERRIDE"}
    if assessment not in allowed_assessments:
        raise ContinuityError("检查点必须记录有效的parallel_execution.assessment")
    workers = [dict(worker) for worker in (execution.get("workers") or [])]
    reason = str(execution.get("reason") or "").strip()
    max_workers = int(policy.get("parallel_development", {}).get("max_delegated_workers", 3))
    if assessment == "DELEGATED":
        if not 1 <= len(workers) <= max_workers:
            raise ContinuityError(f"DELEGATED必须记录1至{max_workers}个执行代理")
    else:
        if workers:
            raise ContinuityError(f"{assessment}不得记录执行代理")
        if policy.get("parallel_development", {}).get("non_delegation_requires_checkpoint_reason") and not reason:
            raise ContinuityError(f"{assessment}必须记录未委托原因")
    seen_ids: set[str] = set()
    claimed_roots: list[tuple[str, str]] = []
    for worker in workers:
        worker_id = str(worker.get("worker_id") or "").strip()
        responsibility = str(worker.get("responsibility") or "").strip()
        paths = [normalize_repo_path(str(path).strip()) for path in worker.get("allowed_paths", []) if str(path).strip()]
        if not worker_id or worker_id in seen_ids or not responsibility or not paths:
            raise ContinuityError("执行代理必须具有唯一worker_id、responsibility和allowed_paths")
        seen_ids.add(worker_id)
        for path in paths:
            root_path = re.split(r"[?*\[]", path, maxsplit=1)[0].rstrip("/")
            if not root_path:
                raise ContinuityError(f"执行代理路径不得覆盖整个仓库：{path}")
            for existing_worker, existing_root in claimed_roots:
                if root_path == existing_root or root_path.startswith(existing_root + "/") or existing_root.startswith(root_path + "/"):
                    raise ContinuityError(f"执行代理路径租约重叠：{worker_id}:{path} <-> {existing_worker}:{existing_root}")
            claimed_roots.append((worker_id, root_path))
        worker.update({"worker_id": worker_id, "responsibility": responsibility, "allowed_paths": paths})
    return {
        "assessment": assessment,
        "delegated_workers": len(workers),
        "workers": workers,
        "reason": reason,
    }


def write_checkpoint(
    root: Path,
    session: dict[str, Any],
    policy: dict[str, Any],
    *,
    summary: str,
    next_step: str,
    blockers: Sequence[str],
    decisions: Sequence[str],
    tests: Sequence[dict[str, str]],
    parallel_execution: dict[str, Any] | None = None,
    note: str = "",
) -> dict[str, Any]:
    if session.get("status") not in {"ACTIVE", "CLOSING"}:
        raise ContinuityError(f"只有 ACTIVE/CLOSING 会话可以创建检查点，当前为 {session.get('status')}")
    fingerprint = project_fingerprint(root, session)
    classification = classify_paths(fingerprint["files"], policy)
    outside = allowed_path_errors(session, fingerprint["files"])
    if outside:
        raise ContinuityError("变更超出会话允许路径：\n" + "\n".join(outside))
    findings = secret_scan(root, fingerprint["files"])
    if findings:
        raise ContinuityError("检测到疑似秘密，禁止写入检查点：" + canonical_json(findings))
    required = change_requirements(classification)
    bootstrap_import = (
        str(session.get("git", {}).get("base_commit") or "") in {"NOT_INITIALIZED", "UNBORN"}
        and str(session.get("task_id") or "") in set(policy.get("bootstrap", {}).get("allow_without_git_task_ids", []))
    )
    if "APPROVED_CHANGE_REQUEST" in required and not bootstrap_import:
        cr_errors = check_required_change_request(root, session)
        if cr_errors:
            raise ContinuityError("；".join(cr_errors))
    sequence = int(session.get("checkpoint_sequence", 0)) + 1
    checkpoint_id = make_checkpoint_id(session["session_id"], sequence)
    created_at = iso_utc()
    git_state = git_info(root)
    parallel_execution = normalize_parallel_execution(policy, parallel_execution or session.get("parallel_execution"))
    checkpoint = {
        "protocol_version": PROTOCOL_VERSION,
        "checkpoint_id": checkpoint_id,
        "session_id": session["session_id"],
        "sequence": sequence,
        "created_at": created_at,
        "summary": summary.strip(),
        "next_step": next_step.strip(),
        "blockers": list(blockers),
        "decisions": list(decisions),
        "note": note,
        "tests": list(tests),
        "git": git_state,
        "project_fingerprint": fingerprint,
        "change_classification": classification,
        "required_records": required,
        "change_requests": session.get("change_requests", []),
        "scope": session.get("scope", {}),
        "parallel_execution": parallel_execution,
    }
    if not checkpoint["summary"] or not checkpoint["next_step"]:
        raise ContinuityError("检查点必须填写 summary 和 next_step")
    if fingerprint["file_count"] and not checkpoint["tests"]:
        raise ContinuityError("项目内容发生变化时，检查点必须记录测试结果或明确的NOT_RUN原因")
    for test in checkpoint["tests"]:
        result = str(test.get("result") or "").upper()
        if result not in {"PASS", "FAIL", "NOT_RUN", "SKIPPED"}:
            raise ContinuityError(f"非法测试结果：{test}")
        if result in {"PASS", "FAIL"} and not str(test.get("evidence") or "").strip():
            raise ContinuityError(f"{result}测试必须提供证据路径或命令摘要：{test.get('name')}")
        if result in {"NOT_RUN", "SKIPPED"} and not str(test.get("note") or "").strip():
            raise ContinuityError(f"{result}测试必须说明原因：{test.get('name')}")
    cp_path = checkpoint_dir(root, session["session_id"]) / f"{sequence:04d}.yaml"
    atomic_write_yaml(cp_path, checkpoint)
    session["checkpoint_sequence"] = sequence
    session["latest_checkpoint"] = cp_path.relative_to(root).as_posix()
    session["next_step"] = checkpoint["next_step"]
    session["parallel_execution"] = parallel_execution
    renew_lease(session, policy)
    save_session(root, session)
    # The active pointer is a first-class recovery record. Refresh it on every
    # checkpoint so each project commit can prove which checkpoint/fingerprint
    # was active, rather than requiring an unchanged pointer to be staged.
    save_active_pointer(
        root,
        {
            "protocol_version": PROTOCOL_VERSION,
            "active_session_id": session["session_id"],
            "status": session["status"],
            "session_record": session_record_path(root, session["session_id"]).relative_to(root).as_posix(),
            "actor_id": session["actor"]["id"],
            "task_id": session["task_id"],
            "story_id": session.get("story_id"),
            "latest_checkpoint": session["latest_checkpoint"],
            "checkpoint_id": checkpoint_id,
            "project_fingerprint": fingerprint["sha256"],
            "lease_expires_at": session["lease"]["expires_at"],
        },
    )
    append_checkpoint_to_log(root, session, checkpoint)
    update_current_status_for_session(root, session, checkpoint)
    event = append_event(
        root,
        "CHECKPOINT_CREATED",
        {
            "session_id": session["session_id"],
            "checkpoint_id": checkpoint_id,
            "project_fingerprint": fingerprint["sha256"],
            "file_count": fingerprint["file_count"],
            "summary": checkpoint["summary"],
            "next_step": checkpoint["next_step"],
        },
    )
    checkpoint["event_hash"] = event["event_hash"]
    atomic_write_yaml(cp_path, checkpoint)
    # SESSION_INDEX是Context Pack的哈希来源，必须先更新再生成Context，
    # 否则新鲜检查点会在同一命令结束时立即被判定过期。
    update_session_index(root, session)
    build_context_pack(root, session)
    save_session(root, session)
    return checkpoint


def git_worktree_patch(root: Path) -> str:
    """Return only tracked, uncommitted changes relative to current HEAD.

    A repository.bundle already contains committed history through HEAD; applying a
    patch from the session base would duplicate committed changes.
    """
    if not is_git_repo(root):
        return "# Git尚未初始化；完整工作目录本身是当前WIP快照。\n"
    info = git_info(root)
    if info.get("head") in {None, "", "NOT_INITIALIZED", "UNBORN"}:
        result = run_command(["git", "diff", "--binary", "--no-ext-diff", "--cached"], cwd=root, timeout=120)
    else:
        result = run_command(["git", "diff", "--binary", "--no-ext-diff", "HEAD"], cwd=root, timeout=120)
    return result.stdout


def git_session_cumulative_patch(root: Path, session: dict[str, Any]) -> str:
    """Return the complete session delta from its immutable base for audit."""
    if not is_git_repo(root):
        return "# Git尚未初始化；完整工作目录本身是当前WIP快照。\n"
    base = session.get("git", {}).get("base_commit")
    args = ["git", "diff", "--binary", "--no-ext-diff"]
    if base and base not in {"NOT_INITIALIZED", "UNBORN"}:
        args.append(base)
    result = run_command(args, cwd=root, timeout=120)
    return result.stdout

def safe_untracked_files(root: Path) -> tuple[list[str], list[str]]:
    if not is_git_repo(root):
        return [], []
    output = git(root, "ls-files", "--others", "--exclude-standard")
    safe: list[str] = []
    excluded: list[str] = []
    for relative in sorted({line.strip() for line in output.splitlines() if line.strip()}):
        if any(path_matches(relative, pattern) for pattern in PORTABLE_EXCLUDE_PATTERNS):
            excluded.append(relative)
            continue
        path = root / relative
        if path.is_file() and path.stat().st_size <= 20_000_000:
            safe.append(relative)
        else:
            excluded.append(relative)
    return safe, excluded


def bundle_manifest(directory: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for path in sorted(directory.rglob("*")):
        if not path.is_file() or path.name == "MANIFEST_SHA256.txt":
            continue
        rows.append(
            {
                "path": path.relative_to(directory).as_posix(),
                "sha256": sha256_file(path),
                "bytes": path.stat().st_size,
            }
        )
    atomic_write_text(
        directory / "MANIFEST_SHA256.txt",
        "".join(f"{row['sha256']}  {row['path']}\n" for row in rows),
    )
    return rows


def create_handoff_bundle(
    root: Path,
    session: dict[str, Any],
    policy: dict[str, Any],
    *,
    reason: str,
    next_step: str,
    portable_zip: str | None = None,
    include_git_bundle: bool = True,
) -> dict[str, Any]:
    """Freeze a mid-task WIP into a conversation-free, verifiable transfer.

    The repository is first transitioned to HANDED_OFF and its context regenerated.
    Only then are the Git patch, untracked snapshot and machine state captured. This
    guarantees that another clone receives the final handoff status rather than the
    pre-handoff ACTIVE state.
    """
    checkpoint = latest_checkpoint(root, session)
    if not checkpoint:
        raise ContinuityError("交接前必须至少创建一个检查点")
    current_fingerprint = project_fingerprint(root, session)
    if current_fingerprint["sha256"] != checkpoint.get("project_fingerprint", {}).get("sha256"):
        raise ContinuityError("检查点之后项目内容已变化；请先重新创建检查点")
    if not reason.strip() or not next_step.strip():
        raise ContinuityError("交接必须填写reason和精确next_step")

    stamp = now_utc().strftime("%Y%m%dT%H%M%SZ")
    bundle_id = f"HOF-{stamp}-{session['session_id']}"
    directory = root / "artifacts/handoffs" / bundle_id
    if directory.exists():
        raise ContinuityError(f"交接目录已存在：{directory.relative_to(root)}")
    created_at = iso_utc()

    # Commit the final machine state before capturing any transfer material.
    session["status"] = "HANDED_OFF"
    session["handoff_bundle"] = directory.relative_to(root).as_posix()
    session["handoff_bundle_id"] = bundle_id
    session["handoff_reason"] = reason.strip()
    session["next_step"] = next_step.strip()
    session["lease"]["expires_at"] = created_at
    save_session(root, session)
    save_active_pointer(
        root,
        {
            "protocol_version": PROTOCOL_VERSION,
            "active_session_id": session["session_id"],
            "status": "HANDED_OFF",
            "session_record": session_record_path(root, session["session_id"]).relative_to(root).as_posix(),
            "handoff_bundle": session["handoff_bundle"],
            "handoff_bundle_id": bundle_id,
        },
    )
    close_task_claim(root, session, "HANDED_OFF")
    update_session_index(root, session)
    transition = record_task_transition(
        root, task_id=session["task_id"], release=session["release"],
        from_status="IN_PROGRESS", to_status="HANDED_OFF", session_id=session["session_id"],
        actor_id=session["actor"]["id"], reason=reason, story_id=session.get("story_id"),
    )
    append_event(
        root,
        "SESSION_HANDED_OFF",
        {
            "session_id": session["session_id"],
            "bundle_id": bundle_id,
            "project_fingerprint": current_fingerprint["sha256"],
            "next_step": next_step,
            "transition_id": transition["transition_id"],
        },
    )
    update_current_status_for_session(root, session, checkpoint)
    context = build_context_pack(root, session)

    # Capture the repository state after the handoff transition but before the
    # bundle itself is created, keeping git-status free of self-referential files.
    git_state = git_info(root)
    directory.mkdir(parents=True, exist_ok=False)

    copies = [
        root / "AGENTS.md",
        root / "START_HERE.md",
        root / "CURRENT_STATUS.yaml",
        root / "NEXT_TASK.yaml",
        root / "CHANGELOG.md",
        root / POLICY_FILE,
        root / STATE_FILE,
        root / ACTIVE_FILE,
        root / EVENT_LOG_FILE,
        root / SESSION_INDEX_FILE,
        root / TASK_CLAIMS_FILE,
        root / TASK_TRANSITIONS_FILE,
        root / CR_INDEX_FILE,
        session_record_path(root, session["session_id"]),
        root / session["session_log"],
        root / session["latest_checkpoint"],
        root / "artifacts/context/CURRENT_CONTEXT_PACK.yaml",
        root / "artifacts/context/CURRENT_CONTEXT_PACK.md",
        root / "artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json",
        root / "catalogs/session_index.csv",
        root / "catalogs/handoff_index.csv",
        root / "catalogs/change_request_index.csv",
        root / "catalogs/task_transition_ledger.csv",
        root / "releases" / session["release"] / "RELEASE_MANIFEST.yaml",
        root / "releases" / session["release"] / "DEFINITION_OF_READY.yaml",
        root / "releases" / session["release"] / "STORIES.yaml",
        root / "releases" / session["release"] / "TASKS.yaml",
        root / "releases" / session["release"] / "ACCEPTANCE_MATRIX.csv",
    ]
    for cr_id in session.get("change_requests", []):
        copies.extend((root / "docs/03-continuity/change-requests").glob(f"{cr_id}*"))
        machine = root / CONTINUITY_DIR / "change_requests" / f"{cr_id}.yaml"
        if machine.exists():
            copies.append(machine)

    snapshot_dir = directory / "snapshot"
    seen: set[str] = set()
    for source in copies:
        if not source.exists() or not source.is_file():
            continue
        relative = source.relative_to(root).as_posix()
        if relative in seen:
            continue
        seen.add(relative)
        target = snapshot_dir / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)

    # The artifact directory itself is excluded by safe_untracked_files, preventing
    # recursive self-inclusion.
    working_tree_patch = git_worktree_patch(root)
    cumulative_patch = git_session_cumulative_patch(root, session)
    safe_untracked, excluded_untracked = safe_untracked_files(root)
    atomic_write_text(directory / "git-status.txt", "\n".join(git_state.get("status_porcelain", [])) + "\n")
    atomic_write_text(directory / "git-log.txt", "\n".join(git_state.get("recent_commits", [])) + "\n")
    atomic_write_text(directory / "working-tree.patch", working_tree_patch)
    atomic_write_text(directory / "session-cumulative.patch", cumulative_patch)
    atomic_write_text(directory / "untracked-files.txt", "\n".join(safe_untracked) + "\n")
    atomic_write_text(directory / "excluded-untracked-files.txt", "\n".join(excluded_untracked) + "\n")
    if safe_untracked:
        with tarfile.open(directory / "untracked-snapshot.tar.gz", "w:gz") as archive:
            for relative in safe_untracked:
                archive.add(root / relative, arcname=relative, recursive=False)

    git_bundle_status = "NOT_AVAILABLE"
    if include_git_bundle and is_git_repo(root):
        refs = git(root, "show-ref")
        if refs:
            result = run_command(
                ["git", "bundle", "create", str(directory / "repository.bundle"), "--all"],
                cwd=root, timeout=180,
            )
            git_bundle_status = "CREATED" if result.returncode == 0 else f"FAILED:{result.stderr.strip()}"

    handoff = {
        "protocol_version": PROTOCOL_VERSION,
        "package_version": PACKAGE_VERSION,
        "bundle_id": bundle_id,
        "created_at": created_at,
        "session_id": session["session_id"],
        "actor_id": session["actor"]["id"],
        "release": session["release"],
        "task_id": session["task_id"],
        "story_id": session.get("story_id"),
        "reason": reason.strip(),
        "exact_next_step": next_step.strip(),
        "latest_checkpoint": session["latest_checkpoint"],
        "project_fingerprint": current_fingerprint,
        "context_hash": context["context_hash"],
        "event_head_hash": validate_event_chain(root).get("head_hash"),
        "git": git_state,
        "git_bundle": git_bundle_status,
        "working_tree_patch_base": "HEAD",
        "session_cumulative_patch_base": session.get("git", {}).get("base_commit"),
        "safe_untracked_files": safe_untracked,
        "excluded_untracked_files": excluded_untracked,
        "takeover_command": f"python3 scripts/continuity.py takeover --actor <NEW_ACTOR> --session {session['session_id']}",
        "conversation_context_required": False,
    }
    atomic_write_yaml(directory / "HANDOFF.yaml", handoff)
    readme = f"""# 无状态接续交接包 · {bundle_id}

## 精确接管命令

```bash
{handoff['takeover_command']}
```

## 当前任务

- Release：`{session['release']}`
- Task：`{session['task_id']}`
- Story：`{session.get('story_id') or 'N/A'}`
- 原 Actor：`{session['actor']['id']}`
- 原因：{reason}
- 下一步：{next_step}
- 项目指纹：`{current_fingerprint['sha256']}`
- Event Head：`{handoff['event_head_hash']}`

## 从 Git Bundle 恢复

1. 验证 `MANIFEST_SHA256.txt`。
2. `git clone repository.bundle <workspace>` 并检出 `HANDOFF.yaml` 中的 `git.head`。
3. 在新工作区执行 `git apply --binary --ignore-space-change working-tree.patch`；该补丁只包含 HEAD 后未提交的跟踪文件。
4. 解压 `untracked-snapshot.tar.gz` 到工作区根目录。
5. 对照 `snapshot/` 与 Context Pack 校验状态。
6. 运行 takeover 命令；禁止直接编辑。

`session-cumulative.patch` 仅用于审计从Session Base到交接点的完整差异，不得在已检出HEAD的工作区重复应用。
不需要任何旧对话。
"""
    atomic_write_text(directory / "README.md", readme)
    rows = bundle_manifest(directory)
    handoff["manifest_files"] = len(rows)
    handoff["manifest_sha256"] = sha256_file(directory / "MANIFEST_SHA256.txt")
    atomic_write_yaml(directory / "HANDOFF.yaml", handoff)
    rows = bundle_manifest(directory)
    sync_continuity_catalogs(root)

    external_zip_path: Path | None = None
    if portable_zip:
        external_zip_path = Path(portable_zip).expanduser().resolve()
        external_zip_path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(external_zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in sorted(directory.rglob("*")):
                if path.is_file():
                    archive.write(path, (Path(directory.name) / path.relative_to(directory)).as_posix())
        atomic_write_text(
            Path(str(external_zip_path) + ".sha256"),
            f"{sha256_file(external_zip_path)}  {external_zip_path.name}\n",
        )
        handoff["portable_zip"] = str(external_zip_path)
    return {
        "directory": str(directory),
        "portable_zip": str(external_zip_path) if external_zip_path else None,
        "handoff": handoff,
    }

def create_clean_export_bundle(
    root: Path,
    *,
    portable_zip: str | None = None,
    include_git_bundle: bool = True,
) -> dict[str, Any]:
    """Export a clean, committed, conversation-free project handoff.

    Mid-task dirty work uses ``handoff``. This function is intentionally stricter:
    no active session, clean worktree, fresh Context Pack and valid event chain.
    """
    pointer = load_active_pointer(root)
    if pointer.get("active_session_id"):
        raise ContinuityError("干净项目导出前必须关闭或交接活跃会话")
    if not is_git_repo(root):
        raise ContinuityError("干净项目导出需要Git仓库和可验证历史")
    info = git_info(root)
    if info.get("dirty"):
        raise ContinuityError("干净项目导出要求工作区完全干净")
    chain = validate_event_chain(root)
    if not chain.get("valid"):
        raise ContinuityError("事件日志哈希链无效：" + ";".join(chain.get("errors", [])))
    fresh, reason = context_is_fresh(root, None)
    if not fresh:
        raise ContinuityError("Context Pack不是最新状态：" + reason)

    stamp = now_utc().strftime("%Y%m%dT%H%M%SZ")
    export_id = f"EXP-{stamp}-{str(info.get('head') or 'NOHEAD')[:12]}"
    directory = root / "artifacts/exports" / export_id
    directory.mkdir(parents=True, exist_ok=False)
    snapshot = directory / "snapshot"
    copies = [
        root / "CURRENT_STATUS.yaml",
        root / "NEXT_TASK.yaml",
        root / STATE_FILE,
        root / ACTIVE_FILE,
        root / EVENT_LOG_FILE,
        root / "artifacts/context/CURRENT_CONTEXT_PACK.yaml",
        root / "artifacts/context/CURRENT_CONTEXT_PACK.md",
        root / "artifacts/context/CURRENT_CONTEXT_PACK_MANIFEST.json",
        root / "docs/03-continuity/LAST_HANDOFF.yaml",
        root / "DEVELOPMENT_RISK_REGISTER.md",
    ]
    for source in copies:
        if not source.exists():
            continue
        target = snapshot / source.relative_to(root)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)

    atomic_write_text(directory / "HEAD.txt", str(info.get("head")) + "\n")
    atomic_write_text(directory / "branch.txt", str(info.get("branch")) + "\n")
    atomic_write_text(directory / "git-status.txt", "\n".join(info.get("status_porcelain", [])) + "\n")
    atomic_write_text(directory / "git-log.txt", "\n".join(info.get("recent_commits", [])) + "\n")

    git_bundle_status = "NOT_REQUESTED"
    if include_git_bundle:
        result = run_command(["git", "bundle", "create", str(directory / "repository.bundle"), "--all"], cwd=root, timeout=240)
        if result.returncode != 0:
            raise ContinuityError("Git Bundle创建失败：" + result.stderr.strip())
        git_bundle_status = "CREATED"

    archive_path = directory / "source-at-head.tar.gz"
    archive = run_command(["git", "archive", "--format=tar.gz", "-o", str(archive_path), "HEAD"], cwd=root, timeout=240)
    if archive.returncode != 0:
        raise ContinuityError("Git source archive创建失败：" + archive.stderr.strip())

    payload = {
        "protocol_version": PROTOCOL_VERSION,
        "package_version": PACKAGE_VERSION,
        "export_id": export_id,
        "created_at": iso_utc(),
        "type": "CLEAN_COMMITTED_PROJECT_HANDOFF",
        "conversation_context_required": False,
        "git": info,
        "event_chain": chain,
        "context_pack": "snapshot/artifacts/context/CURRENT_CONTEXT_PACK.md",
        "git_bundle": git_bundle_status,
        "source_archive": archive_path.name,
        "resume_command": "python3 scripts/continuity.py resume",
        "verification_order": [
            "验证MANIFEST_SHA256.txt",
            "从repository.bundle克隆或解压source-at-head.tar.gz",
            "读取CURRENT_CONTEXT_PACK.md",
            "运行continuity.py resume",
        ],
    }
    atomic_write_yaml(directory / "EXPORT.yaml", payload)
    atomic_write_text(directory / "README.md", f"""# 干净项目无状态交接包 · {export_id}

- HEAD：`{info.get('head')}`
- 分支：`{info.get('branch')}`
- 对话依赖：`false`

## 接管

1. 验证 `MANIFEST_SHA256.txt`。
2. 优先从 `repository.bundle` 克隆完整历史；也可解压 `source-at-head.tar.gz`。
3. 阅读 `snapshot/artifacts/context/CURRENT_CONTEXT_PACK.md`。
4. 运行 `python3 scripts/continuity.py resume`。

无需旧对话。
""")
    rows = bundle_manifest(directory)
    payload["manifest_files"] = len(rows)
    payload["manifest_sha256"] = sha256_file(directory / "MANIFEST_SHA256.txt")
    atomic_write_yaml(directory / "EXPORT.yaml", payload)
    bundle_manifest(directory)

    external: Path | None = None
    if portable_zip:
        external = Path(portable_zip).expanduser().resolve()
        external.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(external, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zip_out:
            for path in sorted(directory.rglob("*")):
                if path.is_file():
                    zip_out.write(path, (Path(directory.name) / path.relative_to(directory)).as_posix())
        atomic_write_text(Path(str(external) + ".sha256"), f"{sha256_file(external)}  {external.name}\n")
    return {
        "directory": str(directory),
        "portable_zip": str(external) if external else None,
        "export": payload,
    }


def _cr_placeholder(value: Any) -> bool:
    text = str(value or "").strip()
    return not text or text.upper() in {"PENDING", "TBD", "TODO", "N/A"} or "待填写" in text


def change_request_completeness_errors(record: dict[str, Any]) -> list[str]:
    """Return fields that make an approval unsafe.

    A CR is not an approval token; it is a complete, reviewable change contract.
    The requester must describe old/new rules, exact affected files, tests,
    releases and compatibility before a different actor may approve it.
    """
    errors: list[str] = []
    for key, label in [
        ("title", "标题"),
        ("user_request", "用户需求"),
        ("reason", "修改原因"),
        ("original_rule", "原规则"),
        ("new_rule", "新规则"),
        ("impact_summary", "影响摘要"),
    ]:
        if _cr_placeholder(record.get(key)):
            errors.append(label)
    impact = record.get("impact") or {}
    if _cr_placeholder(impact.get("migration_and_compatibility")):
        errors.append("迁移与兼容策略")
    for key, label in [("files", "影响文件"), ("tests", "验证测试"), ("releases", "影响版本")]:
        values = [str(item).strip() for item in impact.get(key, []) if str(item).strip()]
        if not values:
            errors.append(label)
    return errors


def render_change_request_markdown(record: dict[str, Any]) -> str:
    impact = record.get("impact") or {}
    approval = record.get("approval") or {}
    def bullets(key: str) -> str:
        values = impact.get(key, []) or []
        return "\n".join(f"- `{item}`" for item in values) or "- 无直接影响（已在影响摘要说明）"
    return f"""---
cr_id: {record['cr_id']}
status: {record.get('status')}
requester_actor_id: {record.get('requester_actor_id')}
approver_actor_id: {record.get('approver_actor_id') or 'N/A'}
task_id: {record.get('task_id')}
session_id: {record.get('session_id') or 'N/A'}
created_at: {record.get('created_at')}
updated_at: {record.get('updated_at') or record.get('created_at')}
---
# {record['cr_id']} — {record.get('title')}

## 用户需求摘要

{record.get('user_request') or '待填写'}

## 原规则

{record.get('original_rule') or '待填写'}

## 新规则

{record.get('new_rule') or '待填写'}

## 修改原因

{record.get('reason') or '待填写'}

## 影响摘要

{record.get('impact_summary') or '待填写'}

## 影响文件

{bullets('files')}

## 页面

{bullets('pages')}

## API

{bullets('apis')}

## 数据库与迁移

{bullets('database')}

## 配置

{bullets('configuration')}

## 资金/账本与历史数据

{bullets('ledger')}

## 测试

{bullets('tests')}

## 版本

{bullets('releases')}

## 迁移与兼容策略

{impact.get('migration_and_compatibility') or '待填写'}

## 用户确认

{record.get('user_confirmation') or 'PENDING'}

## 审批

- 审批人：`{record.get('approver_actor_id') or '待填写'}`
- 决定：`{approval.get('decision') or '待填写'}`
- 时间：`{approval.get('decided_at') or '待填写'}`
- 说明：{approval.get('note') or '待填写'}
"""


def _persist_change_request(root: Path, index: dict[str, Any], record: dict[str, Any]) -> None:
    # The caller may have appended the same mutable object to the index. Replacing
    # the row in-place via ``clear()/update()`` would then clear ``record`` itself
    # and persist an empty mapping. Snapshot first and replace the list entry.
    snapshot = json.loads(json.dumps(record, ensure_ascii=False))
    machine = snapshot.get("machine_record")
    if machine:
        atomic_write_yaml(root / machine, snapshot)
    replaced = False
    for position, row in enumerate(index["change_requests"]):
        if row.get("cr_id") == snapshot.get("cr_id"):
            index["change_requests"][position] = snapshot
            replaced = True
            break
    if not replaced:
        index["change_requests"].append(snapshot)
    atomic_write_yaml(root / CR_INDEX_FILE, index)
    document = snapshot.get("document")
    if document:
        atomic_write_text(root / document, render_change_request_markdown(snapshot))


def create_change_request(
    root: Path,
    *,
    title: str,
    requester_actor_id: str,
    task_id: str,
    session_id: str | None,
    user_request: str,
    reason: str,
) -> dict[str, Any]:
    index = load_index(root, CR_INDEX_FILE, "change_requests")
    numbers = []
    for row in index["change_requests"]:
        match = re.match(r"CR-(\d+)", str(row.get("cr_id", "")))
        if match:
            numbers.append(int(match.group(1)))
    for path in (root / "docs/03-continuity/change-requests").glob("CR-*.md"):
        match = re.match(r"CR-(\d+)", path.name)
        if match:
            numbers.append(int(match.group(1)))
    number = max(numbers, default=0) + 1
    cr_id = f"CR-{number:04d}"
    record = {
        "protocol_version": PROTOCOL_VERSION,
        "cr_id": cr_id,
        "title": title,
        "status": "PROPOSED",
        "created_at": iso_utc(),
        "updated_at": iso_utc(),
        "requester_actor_id": requester_actor_id,
        "approver_actor_id": None,
        "task_id": task_id,
        "session_id": session_id,
        "user_request": user_request,
        "reason": reason,
        "original_rule": "待填写",
        "new_rule": "待填写",
        "impact_summary": "待填写",
        "impact": {
            "files": [],
            "pages": [],
            "apis": [],
            "database": [],
            "configuration": [],
            "ledger": [],
            "tests": [],
            "releases": [],
            "migration_and_compatibility": "待填写",
        },
        "user_confirmation": "PENDING",
        "approval": {"decision": None, "decided_at": None, "note": None},
    }
    machine_path = root / CONTINUITY_DIR / "change_requests" / f"{cr_id}.yaml"
    doc_path = root / "docs/03-continuity/change-requests" / f"{cr_id}-{sanitize_filename(title)}.md"
    record["machine_record"] = machine_path.relative_to(root).as_posix()
    record["document"] = doc_path.relative_to(root).as_posix()
    index["change_requests"].append(record)
    _persist_change_request(root, index, record)
    if session_id:
        session = load_session(root, session_id)
        linked = list(session.get("change_requests", []))
        if cr_id not in linked:
            linked.append(cr_id)
            session["change_requests"] = linked
            save_session(root, session)
    append_event(root, "CHANGE_REQUEST_CREATED", {"cr_id": cr_id, "task_id": task_id, "session_id": session_id})
    return record


def amend_change_request(
    root: Path,
    *,
    cr_id: str,
    actor_id: str,
    original_rule: str,
    new_rule: str,
    impact_summary: str,
    migration_and_compatibility: str,
    files: Sequence[str],
    pages: Sequence[str],
    apis: Sequence[str],
    database: Sequence[str],
    configuration: Sequence[str],
    ledger: Sequence[str],
    tests: Sequence[str],
    releases: Sequence[str],
) -> dict[str, Any]:
    index = load_index(root, CR_INDEX_FILE, "change_requests")
    record = next((row for row in index["change_requests"] if row.get("cr_id") == cr_id), None)
    if not record:
        raise ContinuityError(f"CR不存在：{cr_id}")
    if record.get("status") != "PROPOSED":
        raise ContinuityError("只有PROPOSED状态的CR可以补充影响分析")
    if record.get("requester_actor_id") != actor_id:
        raise ContinuityError("只有CR申请人可以补充变更合同")
    impact = dict(record.get("impact") or {})
    impact.update({
        "files": list(dict.fromkeys(item.strip() for item in files if item.strip())),
        "pages": list(dict.fromkeys(item.strip() for item in pages if item.strip())),
        "apis": list(dict.fromkeys(item.strip() for item in apis if item.strip())),
        "database": list(dict.fromkeys(item.strip() for item in database if item.strip())),
        "configuration": list(dict.fromkeys(item.strip() for item in configuration if item.strip())),
        "ledger": list(dict.fromkeys(item.strip() for item in ledger if item.strip())),
        "tests": list(dict.fromkeys(item.strip() for item in tests if item.strip())),
        "releases": list(dict.fromkeys(item.strip() for item in releases if item.strip())),
        "migration_and_compatibility": migration_and_compatibility.strip(),
    })
    record.update({
        "original_rule": original_rule.strip(),
        "new_rule": new_rule.strip(),
        "impact_summary": impact_summary.strip(),
        "impact": impact,
        "updated_at": iso_utc(),
    })
    missing = change_request_completeness_errors(record)
    if missing:
        raise ContinuityError("CR信息仍不完整：" + "、".join(missing))
    _persist_change_request(root, index, record)
    append_event(root, "CHANGE_REQUEST_AMENDED", {"cr_id": cr_id, "actor_id": actor_id, "files": impact["files"]})
    return record


def approve_change_request(
    root: Path,
    *,
    cr_id: str,
    approver_actor_id: str,
    decision: str,
    note: str,
    user_confirmation: str,
) -> dict[str, Any]:
    index = load_index(root, CR_INDEX_FILE, "change_requests")
    record = next((row for row in index["change_requests"] if row.get("cr_id") == cr_id), None)
    if not record:
        raise ContinuityError(f"CR不存在：{cr_id}")
    if record.get("requester_actor_id") == approver_actor_id:
        raise ContinuityError("CR申请人不得审批自己的变更")
    decision = decision.upper()
    if decision not in {"APPROVED", "REJECTED"}:
        raise ContinuityError("decision必须为 APPROVED 或 REJECTED")
    if record.get("status") != "PROPOSED":
        raise ContinuityError(f"只有PROPOSED状态CR可以审批，当前为 {record.get('status')}")
    if decision == "APPROVED":
        missing = change_request_completeness_errors(record)
        if missing:
            raise ContinuityError("CR未达到可审批状态：" + "、".join(missing))
        if _cr_placeholder(user_confirmation):
            raise ContinuityError("批准CR必须提供明确的用户确认依据")
    if _cr_placeholder(note):
        raise ContinuityError("审批说明不能为空或占位")
    record["status"] = decision
    record["approver_actor_id"] = approver_actor_id
    record["user_confirmation"] = user_confirmation
    record["updated_at"] = iso_utc()
    record["approval"] = {"decision": decision, "decided_at": iso_utc(), "note": note}
    _persist_change_request(root, index, record)
    append_event(root, "CHANGE_REQUEST_DECIDED", {"cr_id": cr_id, "decision": decision, "approver_actor_id": approver_actor_id})
    return record


def apply_change_request_scope(
    root: Path,
    *,
    session: dict[str, Any],
    cr_id: str,
    actor_id: str,
) -> dict[str, Any]:
    """Apply an approved CR's exact impact files to the current session scope."""
    if session.get("status") != "ACTIVE":
        raise ContinuityError("只有ACTIVE会话可以应用CR范围")
    if session.get("actor", {}).get("id") != actor_id:
        raise ContinuityError("只有当前ACTIVE会话Actor可以应用CR范围")
    if cr_id not in session.get("change_requests", []):
        raise ContinuityError(f"CR未关联当前会话：{cr_id}")

    index = load_index(root, CR_INDEX_FILE, "change_requests")
    record = next((row for row in index["change_requests"] if row.get("cr_id") == cr_id), None)
    if not record:
        raise ContinuityError(f"CR不存在：{cr_id}")
    if record.get("status") not in {"APPROVED", "IMPLEMENTING", "IMPLEMENTED", "CLOSED"}:
        raise ContinuityError(f"CR尚未批准：{cr_id} / {record.get('status')}")
    if record.get("approval", {}).get("decision") != "APPROVED":
        raise ContinuityError(f"CR缺少明确批准决定：{cr_id}")
    if record.get("requester_actor_id") == record.get("approver_actor_id"):
        raise ContinuityError(f"CR申请人与审批人相同：{cr_id}")
    if record.get("task_id") != session.get("task_id"):
        raise ContinuityError(f"CR不属于当前任务：{cr_id}")

    exact_paths: list[str] = []
    for raw in (record.get("impact") or {}).get("files", []):
        value = str(raw).strip().replace("\\", "/")
        if (
            not value
            or value.startswith(("/", "\\"))
            or re.match(r"^[A-Za-z]:", value)
            or any(char in value for char in "*?[]")
            or any(part in {"", ".", ".."} for part in value.split("/"))
            or value == ".git"
            or value.startswith(".git/")
        ):
            raise ContinuityError(f"CR范围只允许仓库内精确文件路径：{raw}")
        exact_paths.append(value)
    if not exact_paths:
        raise ContinuityError(f"CR未声明影响文件：{cr_id}")

    scope = dict(session.get("scope") or {})
    exceptions = list(scope.get("approved_exceptions", []))
    for path in exact_paths:
        if path not in exceptions:
            exceptions.append(path)
    scope["approved_exceptions"] = exceptions
    source = str(scope.get("source") or "story+explicit")
    marker = f"approved-cr:{cr_id}"
    if marker not in source.split("+"):
        scope["source"] = source + "+" + marker
    session["scope"] = scope
    session["updated_at"] = iso_utc()
    save_session(root, session)
    append_event(root, "CHANGE_REQUEST_STATUS_UPDATED", {
        "cr_id": cr_id,
        "status": record.get("status"),
        "actor_id": actor_id,
        "session_id": session.get("session_id"),
        "operation": "SCOPE_APPLIED",
        "files": exact_paths,
    })
    return {
        "session_id": session.get("session_id"),
        "cr_id": cr_id,
        "applied_files": exact_paths,
        "checkpoint_required": True,
    }


def sanitize_filename(value: str) -> str:
    clean = re.sub(r"[\\/:*?\"<>|\s]+", "-", value.strip())
    return clean[:80].strip("-") or "change"


def initialize_continuity_files(root: Path) -> None:
    (root / CONTINUITY_DIR / "runtime").mkdir(parents=True, exist_ok=True)
    (root / CONTINUITY_DIR / "sessions").mkdir(parents=True, exist_ok=True)
    (root / CONTINUITY_DIR / "checkpoints").mkdir(parents=True, exist_ok=True)
    (root / CONTINUITY_DIR / "change_requests").mkdir(parents=True, exist_ok=True)
    for relative, content in [
        (ACTIVE_FILE, {"protocol_version": PROTOCOL_VERSION, "active_session_id": None, "status": "NONE", "updated_at": iso_utc()}),
        (SESSION_INDEX_FILE, {"version": PROTOCOL_VERSION, "sessions": []}),
        (TASK_CLAIMS_FILE, {"version": PROTOCOL_VERSION, "claims": []}),
        (CR_INDEX_FILE, {"version": PROTOCOL_VERSION, "change_requests": []}),
        (TASK_TRANSITIONS_FILE, {"version": PROTOCOL_VERSION, "transitions": []}),
    ]:
        path = root / relative
        if not path.exists():
            atomic_write_yaml(path, content)



def record_task_transition(
    root: Path,
    *,
    task_id: str,
    release: str,
    from_status: str | None,
    to_status: str,
    session_id: str | None,
    actor_id: str,
    reason: str,
    story_id: str | None = None,
) -> dict[str, Any]:
    ledger = load_index(root, TASK_TRANSITIONS_FILE, "transitions")
    transition = {
        "transition_id": f"TRN-{uuid.uuid4().hex[:12].upper()}",
        "timestamp": iso_utc(),
        "release": release,
        "task_id": task_id,
        "story_id": story_id,
        "from_status": from_status,
        "to_status": to_status,
        "session_id": session_id,
        "actor_id": actor_id,
        "reason": reason,
    }
    ledger["transitions"].append(transition)
    atomic_write_yaml(root / TASK_TRANSITIONS_FILE, ledger)
    sync_continuity_catalogs(root)
    return transition


def sync_continuity_catalogs(root: Path) -> None:
    """把机器状态投影为可检索CSV；YAML/JSONL仍是唯一事实源。"""
    sessions = load_index(root, SESSION_INDEX_FILE, "sessions").get("sessions", [])
    write_csv(
        root / "catalogs/session_index.csv",
        [
            {
                "session_id": row.get("session_id"),
                "task_id": row.get("task_id"),
                "story_id": row.get("story_id"),
                "actor_id": row.get("actor_id"),
                "status": row.get("status"),
                "started_at": row.get("started_at"),
                "updated_at": row.get("updated_at"),
                "closed_at": row.get("closed_at"),
                "latest_checkpoint": row.get("latest_checkpoint"),
                "handoff_bundle": row.get("handoff_bundle"),
                "record": row.get("record"),
                "session_log": row.get("session_log"),
            }
            for row in sessions
        ],
        ["session_id","task_id","story_id","actor_id","status","started_at","updated_at","closed_at","latest_checkpoint","handoff_bundle","record","session_log"],
    )
    crs = load_index(root, CR_INDEX_FILE, "change_requests").get("change_requests", [])
    write_csv(
        root / "catalogs/change_request_index.csv",
        [
            {
                "cr_id": row.get("cr_id"),
                "title": row.get("title"),
                "status": row.get("status"),
                "requester_actor_id": row.get("requester_actor_id"),
                "approver_actor_id": row.get("approver_actor_id"),
                "task_id": row.get("task_id"),
                "session_id": row.get("session_id"),
                "created_at": row.get("created_at"),
                "updated_at": row.get("updated_at"),
                "document": row.get("document"),
                "machine_record": row.get("machine_record"),
            }
            for row in crs
        ],
        ["cr_id","title","status","requester_actor_id","approver_actor_id","task_id","session_id","created_at","updated_at","document","machine_record"],
    )
    transitions = load_index(root, TASK_TRANSITIONS_FILE, "transitions").get("transitions", [])
    write_csv(
        root / "catalogs/task_transition_ledger.csv",
        transitions,
        ["transition_id","timestamp","release","task_id","story_id","from_status","to_status","session_id","actor_id","reason"],
    )
    handoffs: list[dict[str, Any]] = []
    handoff_root = root / "artifacts/handoffs"
    if handoff_root.exists():
        for path in sorted(handoff_root.glob("*/HANDOFF.yaml")):
            row = load_yaml(path, {}) or {}
            handoffs.append(
                {
                    "bundle_id": row.get("bundle_id"),
                    "created_at": row.get("created_at"),
                    "session_id": row.get("session_id"),
                    "actor_id": row.get("actor_id"),
                    "release": row.get("release"),
                    "task_id": row.get("task_id"),
                    "story_id": row.get("story_id"),
                    "project_fingerprint": (row.get("project_fingerprint") or {}).get("sha256"),
                    "context_hash": row.get("context_hash"),
                    "git_bundle": row.get("git_bundle"),
                    "path": path.parent.relative_to(root).as_posix(),
                }
            )
    write_csv(
        root / "catalogs/handoff_index.csv",
        handoffs,
        ["bundle_id","created_at","session_id","actor_id","release","task_id","story_id","project_fingerprint","context_hash","git_bundle","path"],
    )

def verify_manifest(directory: Path) -> dict[str, Any]:
    manifest = directory / "MANIFEST_SHA256.txt"
    if not manifest.exists():
        return {"valid": False, "errors": ["MANIFEST_SHA256.txt不存在"]}
    errors: list[str] = []
    count = 0
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            digest, relative = line.split("  ", 1)
        except ValueError:
            errors.append(f"格式错误：{line}")
            continue
        count += 1
        path = directory / relative
        if not path.exists():
            errors.append(f"缺失：{relative}")
        elif sha256_file(path) != digest:
            errors.append(f"哈希不一致：{relative}")
    return {"valid": not errors, "files": count, "errors": errors}


def update_change_request_status(
    root: Path,
    *,
    cr_id: str,
    actor_id: str,
    status: str,
    note: str,
    session_id: str | None = None,
    commits: Sequence[str] = (),
) -> dict[str, Any]:
    """在不建立第二套状态模型的前提下推进已批准CR。"""
    index = load_index(root, CR_INDEX_FILE, "change_requests")
    record = next((row for row in index["change_requests"] if row.get("cr_id") == cr_id), None)
    if not record:
        raise ContinuityError(f"CR不存在：{cr_id}")
    status = status.upper()
    allowed = {
        "APPROVED": {"IMPLEMENTING", "SUPERSEDED", "REJECTED"},
        "IMPLEMENTING": {"IMPLEMENTED", "SUPERSEDED"},
        "IMPLEMENTED": {"CLOSED", "SUPERSEDED"},
        "PROPOSED": {"REJECTED", "SUPERSEDED"},
        "HISTORICAL_APPROVED": {"CLOSED", "SUPERSEDED"},
    }
    current = record.get("status")
    if status not in allowed.get(current, set()):
        raise ContinuityError(f"非法CR状态迁移：{current} -> {status}")
    record["status"] = status
    record["updated_at"] = iso_utc()
    record.setdefault("decision_log", []).append(
        {"at": record["updated_at"], "actor_id": actor_id, "status": status, "note": note, "session_id": session_id}
    )
    if commits:
        linked = list(record.get("implementation_commits", []))
        for commit in commits:
            if commit not in linked:
                linked.append(commit)
        record["implementation_commits"] = linked
    if session_id:
        linked_sessions = list(record.get("session_ids", []))
        if session_id not in linked_sessions:
            linked_sessions.append(session_id)
        record["session_ids"] = linked_sessions
    machine = record.get("machine_record")
    if machine:
        atomic_write_yaml(root / machine, record)
    document = record.get("document")
    if document:
        path = root / document
        text = path.read_text(encoding="utf-8") if path.exists() else f"# {cr_id}\n"
        text += f"\n## 状态记录 · {record['updated_at']}\n\n- Actor：`{actor_id}`\n- Status：`{status}`\n- Session：`{session_id or 'N/A'}`\n- Note：{note}\n"
        atomic_write_text(path, text)
    for row in index["change_requests"]:
        if row.get("cr_id") == cr_id:
            row.update(record)
    atomic_write_yaml(root / CR_INDEX_FILE, index)
    append_event(root, "CHANGE_REQUEST_STATUS_UPDATED", {"cr_id": cr_id, "status": status, "actor_id": actor_id, "session_id": session_id})
    return record
