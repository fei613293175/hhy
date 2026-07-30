from __future__ import annotations

import fnmatch
import re
from pathlib import Path
from typing import Any

from .util import git, read_yaml

MAX_TEXT_BYTES = 8 * 1024 * 1024
FORBIDDEN_BASENAMES = {".env", ".env.local", ".env.production", ".env.prod", ".pypirc", "credentials", "credentials.json", "service-account.json", "service_account.json"}
FORBIDDEN_SUFFIXES = {".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".kdbx"}
BINARY_SUFFIXES = {".apk", ".aab", ".zip", ".gz", ".tgz", ".7z", ".rar", ".jar", ".class", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".pdf", ".mp4", ".mov", ".woff", ".woff2", ".ttf", ".otf", ".so", ".dll", ".exe", ".bin"}
PATTERNS = (
    ("PRIVATE_KEY", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----")),
    ("GITHUB_TOKEN", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{70,255})\b")),
    ("AWS_ACCESS_KEY", re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("ALIYUN_ACCESS_KEY", re.compile(r"\bLTAI[A-Za-z0-9]{16,32}\b")),
    ("OPENAI_API_KEY", re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_-]{32,}\b")),
    ("STRIPE_LIVE_KEY", re.compile(r"\b(?:sk|rk)_live_[A-Za-z0-9]{20,}\b")),
    ("GOOGLE_API_KEY", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("SLACK_TOKEN", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{20,}\b")),
    ("SENDGRID_KEY", re.compile(r"\bSG\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{32,}\b")),
)
ASSIGNMENT = re.compile(r"(?i)(?:^|[\s,{])(?:[A-Za-z0-9_.-]*(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|client[_-]?secret|signing[_-]?key)[A-Za-z0-9_.-]*)\s*(?:=|:|=>)\s*[\"']?([^\s\"',;}#]{8,})")
GENERIC_ASSIGNMENT_SUFFIXES = {".env", ".yaml", ".yml", ".toml", ".ini", ".conf", ".properties"}
PLACEHOLDERS = ("example", "placeholder", "dummy", "redacted", "changeme", "secretref", "vaultref", "${", "{{", "<", "your_", "replace_", "test", "testing", "development", "local", "unset", "not_set", "hhy_ci_only", "null", "none", "false", "true", "vault_or_kms_reference_only", "kms_reference", "reference_only", "not_secret")


def _allowlist(repo: Path) -> list[dict[str, str]]:
    path = repo / "config" / "secret-scan-allowlist.yaml"
    data = read_yaml(path) if path.is_file() else {}
    rows = data.get("allow") or []
    result = []
    for row in rows:
        if not isinstance(row, dict) or not row.get("path") or not row.get("rule"):
            raise ValueError("secret allowlist entries require exact path and rule")
        if row["path"] in {"*", "**", "**/*"}:
            raise ValueError("blanket secret allowlist is forbidden")
        result.append({"path": str(row["path"]), "rule": str(row["rule"])})
    return result


def _allowed(path: str, rule: str, rows: list[dict[str, str]]) -> bool:
    return any(row["rule"] == rule and fnmatch.fnmatchcase(path, row["path"]) for row in rows)


def _candidate_paths(repo: Path, include_untracked: bool, full: bool) -> list[str]:
    if full:
        args = ["ls-files", "-c"]
        if include_untracked:
            args.extend(["-o", "--exclude-standard"])
        raw = git(repo, *args, "-z")
        return sorted({value for value in raw.split("\0") if value})
    values: set[str] = set()
    commands = [
        ("diff", "--cached", "--name-only", "-z"),
        ("diff", "--name-only", "-z"),
        ("diff-tree", "--no-commit-id", "--name-only", "-r", "-z", "HEAD"),
    ]
    if include_untracked:
        commands.append(("ls-files", "-o", "--exclude-standard", "-z"))
    for command in commands:
        raw = git(repo, *command, check=False)
        values.update(value for value in raw.split("\0") if value)
    return sorted(values)


def _is_text(path: Path) -> bool:
    if path.suffix.lower() in BINARY_SUFFIXES or not path.is_file():
        return False
    if path.stat().st_size > MAX_TEXT_BYTES:
        return False
    return b"\0" not in path.read_bytes()[:4096]



def _looks_generic_secret(value: str) -> bool:
    normalized = value.strip().strip("\"'")
    low = normalized.lower()
    if any(marker in low for marker in PLACEHOLDERS):
        return False
    if not normalized.isascii() or len(normalized) < 16 or normalized.startswith(("/", "./", "../")):
        return False
    if not re.fullmatch(r"[A-Za-z0-9+/=_\-.]{16,}", normalized):
        return False
    classes = sum(bool(re.search(pattern, normalized)) for pattern in (r"[a-z]", r"[A-Z]", r"[0-9]", r"[^A-Za-z0-9]"))
    return classes >= 2

def scan_secrets(repo: Path, full: bool = True, include_untracked: bool = True) -> dict[str, Any]:
    allow = _allowlist(repo)
    candidates = _candidate_paths(repo, include_untracked, full)
    findings: list[dict[str, Any]] = []
    scanned = 0
    for rel in candidates:
        path = repo / rel
        name = path.name.lower()
        suffix = path.suffix.lower()
        if name in FORBIDDEN_BASENAMES and name != ".env.example" and not _allowed(rel, "FORBIDDEN_SECRET_FILE", allow):
            findings.append({"rule": "FORBIDDEN_SECRET_FILE", "path": rel, "line": None})
        if suffix in FORBIDDEN_SUFFIXES and not _allowed(rel, "FORBIDDEN_SECRET_FILE", allow):
            findings.append({"rule": "FORBIDDEN_SECRET_FILE", "path": rel, "line": None})
        if not _is_text(path):
            continue
        scanned += 1
        text = path.read_text(encoding="utf-8", errors="replace")
        for number, line in enumerate(text.splitlines(), 1):
            for rule, pattern in PATTERNS:
                if pattern.search(line) and not _allowed(rel, rule, allow):
                    findings.append({"rule": rule, "path": rel, "line": number})
            if path.suffix.lower() in GENERIC_ASSIGNMENT_SUFFIXES and not rel.startswith(("docs/", "governance/archive/", ".continuity/", "tests/")):
                for match in ASSIGNMENT.finditer(line):
                    value = match.group(1).strip()
                    if _looks_generic_secret(value) and not _allowed(rel, "GENERIC_SECRET_ASSIGNMENT", allow):
                        findings.append({"rule": "GENERIC_SECRET_ASSIGNMENT", "path": rel, "line": number})
    unique = sorted({(f["rule"], f["path"], f["line"]): f for f in findings}.values(), key=lambda f: (f["path"], f["line"] or 0, f["rule"]))
    return {"schema": "hhy.secret-scan/v5.0", "status": "PASS" if not unique else "FAIL", "files_considered": len(candidates), "files_scanned": scanned, "finding_count": len(unique), "findings": unique}
