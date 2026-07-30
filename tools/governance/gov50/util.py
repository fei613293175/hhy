from __future__ import annotations

import hashlib
import json
import os
import subprocess
import tempfile
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

import yaml


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
        os.replace(tmp_name, path)
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
