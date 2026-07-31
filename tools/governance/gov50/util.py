from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

import yaml


def resolve_codex_executable() -> str | None:
    """Prefer the user-installed Codex CLI over the WindowsApps alias."""
    explicit = os.environ.get("HHY_CODEX") or os.environ.get("CODEX_EXECUTABLE")
    candidates: list[Path] = []
    if explicit:
        candidates.append(Path(explicit).expanduser())
    local_bin = Path.home() / "AppData" / "Local" / "OpenAI" / "Codex" / "bin"
    if local_bin.is_dir():
        candidates.extend(sorted(local_bin.glob("*/codex.exe"), key=lambda path: path.stat().st_mtime, reverse=True))
    for command in ("codex.exe", "codex"):
        resolved = shutil.which(command)
        if resolved:
            candidates.append(Path(resolved))
    seen: set[str] = set()
    for candidate in candidates:
        try:
            resolved = str(candidate.resolve())
        except OSError:
            continue
        key = resolved.lower()
        if key in seen:
            continue
        seen.add(key)
        if "windowsapps" in key:
            continue
        if candidate.is_file():
            return resolved
    return None


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        for attempt in range(8):
            try:
                os.replace(tmp_name, path)
                return
            except PermissionError:
                if attempt == 7:
                    raise
                time.sleep(0.1 * (attempt + 1))
    finally:
        try:
            os.unlink(tmp_name)
        except FileNotFoundError:
            pass


def write_json(path: Path, value: Any) -> None:
    _atomic_write(path, (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n").encode("utf-8"))


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_yaml(path: Path, value: Any) -> None:
    text = yaml.safe_dump(value, allow_unicode=True, sort_keys=False, width=120)
    _atomic_write(path, text.encode("utf-8"))


def read_yaml(path: Path) -> Any:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def git(repo: Path, *args: str, check: bool = True, env: dict[str, str] | None = None) -> str:
    merged = os.environ.copy()
    if env:
        merged.update(env)
    proc = subprocess.run(["git", *args], cwd=repo, text=True, capture_output=True, env=merged)
    if check and proc.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed ({proc.returncode}): {proc.stderr.strip() or proc.stdout.strip()}")
    return proc.stdout.strip()


def git_status_lines(repo: Path, *args: str, check: bool = True, env: dict[str, str] | None = None) -> list[str]:
    """Read porcelain status without stripping its two-character status prefix."""
    merged = os.environ.copy()
    if env:
        merged.update(env)
    proc = subprocess.run(["git", *args], cwd=repo, text=True, capture_output=True, env=merged)
    if check and proc.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed ({proc.returncode}): {proc.stderr.strip() or proc.stdout.strip()}")
    return proc.stdout.splitlines()


def run(command: Iterable[str], cwd: Path, timeout: int = 600, env: dict[str, str] | None = None) -> dict[str, Any]:
    argv = [str(v) for v in command]
    merged = os.environ.copy()
    if env:
        merged.update(env)
    try:
        proc = subprocess.run(argv, cwd=cwd, text=True, capture_output=True, timeout=timeout, env=merged)
        return {
            "status": "PASS" if proc.returncode == 0 else "FAIL",
            "exit_code": proc.returncode,
            "command": argv,
            "stdout_tail": proc.stdout[-12000:],
            "stderr_tail": proc.stderr[-12000:],
        }
    except subprocess.TimeoutExpired as exc:
        return {
            "status": "FAIL",
            "exit_code": 124,
            "command": argv,
            "stdout_tail": (exc.stdout or "")[-12000:] if isinstance(exc.stdout, str) else "",
            "stderr_tail": f"TIMEOUT after {timeout}s",
        }
    except FileNotFoundError as exc:
        return {"status": "FAIL", "exit_code": 127, "command": argv, "stdout_tail": "", "stderr_tail": str(exc)}
    except PermissionError as exc:
        return {
            "status": "FAIL",
            "exit_code": 13,
            "command": argv,
            "stdout_tail": "",
            "stderr_tail": str(exc),
            "error_category": "WINDOWS_ACCESS_DENIED",
        }


@contextmanager
def repository_lock(repo: Path, timeout: int = 30):
    lock = repo / ".git" / "hhy-governance-v50.lock"
    deadline = time.monotonic() + timeout
    token = f"{os.getpid()}:{utc_now()}"
    while True:
        try:
            fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                handle.write(token)
            break
        except FileExistsError:
            # A terminated Codex/CI process must not permanently deadlock the
            # project. Reclaim only locks whose recorded local PID no longer
            # exists; a live owner is never pre-empted.
            try:
                raw = lock.read_text(encoding="utf-8").strip()
                pid = int(raw.split(":", 1)[0])
                try:
                    os.kill(pid, 0)
                    alive = True
                except ProcessLookupError:
                    alive = False
                except PermissionError:
                    alive = True
                except OSError:
                    # Windows reports a nonexistent PID as a generic OSError
                    # (for example WinError 87), while POSIX uses ESRCH.
                    alive = False
                if not alive:
                    lock.unlink(missing_ok=True)
                    continue
            except (OSError, ValueError):
                pass
            if time.monotonic() >= deadline:
                raise TimeoutError(f"repository governance lock is busy: {lock}")
            time.sleep(0.2)
    try:
        yield
    finally:
        try:
            if lock.read_text(encoding="utf-8") == token:
                lock.unlink()
        except FileNotFoundError:
            pass
