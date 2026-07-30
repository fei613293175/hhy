from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Iterable

import yaml

LEGACY_EXECUTABLE_PATTERNS = (
    "run_continuity", "continuity_gate", "continuity_lib", "candidate_request.py",
    "android_candidate_request", "android_historical_visual_request", "historical_visual_request",
    "create_checkpoint", "checkpoint_create", "change_request", "session_log_required",
    ".continuity/", "context_pack", "attempt_exception", "attempt_override",
    "effective_attempt_limit", "approved_attempt_exceptions",
)
COMMAND_KEYS = {"run", "command", "commands", "script", "scripts", "acceptance_commands", "exec"}


def _strings(value: Any, command_context: bool = False) -> Iterable[str]:
    if isinstance(value, dict):
        for key, child in value.items():
            yield from _strings(child, command_context or str(key).lower() in COMMAND_KEYS)
    elif isinstance(value, list):
        for child in value:
            yield from _strings(child, command_context)
    elif isinstance(value, str) and command_context:
        yield value


def _scan_structured(path: Path) -> list[dict[str, Any]]:
    try:
        if path.suffix.lower() == ".json":
            data = json.loads(path.read_text(encoding="utf-8"))
        else:
            data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except Exception as exc:
        return [{"path": str(path), "rule": "PARSE_ERROR", "detail": str(exc)}]
    findings = []
    for value in _strings(data):
        low = value.lower()
        for pattern in LEGACY_EXECUTABLE_PATTERNS:
            if pattern in low:
                findings.append({"path": str(path), "rule": "LEGACY_EXECUTABLE_ROUTE", "pattern": pattern, "value": value[:500]})
    return findings


def scan_active_control_plane(repo: Path) -> dict[str, Any]:
    paths: list[Path] = []
    for root in [repo / ".github" / "workflows", repo / "governance" / "task_specs", repo / "governance" / "policies"]:
        if root.is_dir():
            paths.extend(p for p in root.rglob("*") if p.is_file() and p.suffix.lower() in {".yaml", ".yml", ".json"})
    hook_json = repo / ".codex" / "hooks.json"
    if hook_json.is_file():
        paths.append(hook_json)
    findings: list[dict[str, Any]] = []
    for path in sorted(set(paths)):
        rows = _scan_structured(path)
        for row in rows:
            try: row["path"] = path.relative_to(repo).as_posix()
            except Exception: pass
        findings.extend(rows)
    # Executable shell/Python entrypoints are scanned only when a legacy term appears
    # near an execution primitive. Explanatory text and deny-lists are not routes.
    executable_roots = [repo / ".codex" / "hooks", repo / ".githooks-v5", repo / "tools" / "governance"]
    trigger = re.compile(r"(?:subprocess|os\.system|run\s*\(|exec\s*\(|command|argv|python3|bash|sh\s)", re.I)
    for root in executable_roots:
        if not root.is_dir(): continue
        for path in root.rglob("*"):
            if not path.is_file() or path.name == "legacy.py": continue
            if path.suffix.lower() not in {".py", ".sh", ""}: continue
            for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                low=line.lower()
                if trigger.search(line) and any(pattern in low for pattern in LEGACY_EXECUTABLE_PATTERNS):
                    findings.append({"path": path.relative_to(repo).as_posix(), "line": number, "rule": "LEGACY_EXECUTABLE_ROUTE", "value": line[:500]})
    unique = sorted({json.dumps(row, ensure_ascii=False, sort_keys=True): row for row in findings}.values(), key=lambda r:(r.get("path",""),r.get("line",0),r.get("pattern","")))
    return {"schema": "hhy.active-control-plane-scan/v5.0", "status": "PASS" if not unique else "FAIL", "findings": unique, "scanned_file_count": len(set(paths))}
