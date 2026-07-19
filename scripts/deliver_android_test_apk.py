#!/usr/bin/env python3
"""Safely prepare, verify, and accept an Android test APK delivery.

The build system is intentionally external to this script. ``prepare`` accepts
an APK plus a machine-readable build evidence document, verifies their release
identity, and performs the delivery transaction. SSH credentials are never
accepted: a preconfigured SSH alias (and its agent/key policy) is required.
"""
from __future__ import annotations

from argparse import ArgumentParser, Namespace
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Callable, Mapping, Protocol, Sequence
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
import hashlib
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time

import yaml


ROOT = Path(__file__).resolve().parents[1]
RELEASE_PATTERN = re.compile(r"(?:P00|R\d{2})")
COMMIT_PATTERN = re.compile(r"[0-9a-fA-F]{40}")
FINGERPRINT_PATTERN = re.compile(r"[0-9a-fA-F]{64}")
SSH_ALIAS_PATTERN = re.compile(r"[A-Za-z0-9_.@-]+")
VERSION_NAME_PATTERN = re.compile(r"\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?")
PUBLIC_DOWNLOAD_HOST = "download.orbexa.cc"
VERSION_CODE_BASE = 10200
REQUIRED_BUILD_CHECKS = {"testDebugUnitTest", "lintDebug", "assembleDebug", "apksigner"}
PLACEHOLDER_PATTERN = re.compile(r"PENDING|PLACEHOLDER|\bTODO\b|\bTBD\b|UNKNOWN", re.IGNORECASE)
FORBIDDEN_CREDENTIAL_OPTIONS = {
    "--password", "--ssh-password", "--key-password", "--passphrase",
    "--identity-file", "-i",
}


class DeliveryError(RuntimeError):
    """A delivery invariant was not satisfied."""


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def stream_sha256(path: Path, block_size: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(block_size), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_release(value: str) -> str:
    release = value.strip().upper()
    if not RELEASE_PATTERN.fullmatch(release):
        raise DeliveryError("release must be P00 or R followed by exactly two digits")
    return release


def validate_commit(value: str) -> str:
    commit = value.strip().lower()
    if not COMMIT_PATTERN.fullmatch(commit) or set(commit) == {"0"}:
        raise DeliveryError("commit must be a non-zero full 40-character Git SHA")
    return commit


def expected_version_code(release: str) -> int:
    release = validate_release(release)
    if release == "P00":
        return VERSION_CODE_BASE
    return VERSION_CODE_BASE + int(release[1:])


def validate_version_code(release: str, value: int) -> int:
    minimum = expected_version_code(release)
    if isinstance(value, bool) or value < minimum:
        raise DeliveryError(f"versionCode for {release} must be at least {minimum}")
    return value


def validate_version_name(value: str) -> str:
    version_name = value.strip()
    if not VERSION_NAME_PATTERN.fullmatch(version_name) or not version_name.lower().endswith("debug"):
        raise DeliveryError("test APK versionName must be a semantic version ending in debug")
    return version_name


def normalize_fingerprint(value: str) -> str:
    fingerprint = re.sub(r"[^0-9a-fA-F]", "", value).lower()
    if not FINGERPRINT_PATTERN.fullmatch(fingerprint) or set(fingerprint) == {"0"}:
        raise DeliveryError("signing fingerprint must be a non-zero SHA-256 value")
    return fingerprint


def canonical_apk_name(release: str, commit: str) -> str:
    return f"hhy-{validate_release(release).lower()}-{validate_commit(commit)[:7]}-debug.apk"


def validate_public_base_url(value: str) -> str:
    parsed = urlsplit(value.strip())
    if (
        parsed.scheme != "https"
        or parsed.hostname != PUBLIC_DOWNLOAD_HOST
        or parsed.username is not None
        or parsed.password is not None
        or parsed.port not in (None, 443)
        or parsed.path not in ("", "/")
        or parsed.query
        or parsed.fragment
    ):
        raise DeliveryError(f"public base URL must be https://{PUBLIC_DOWNLOAD_HOST}")
    return f"https://{PUBLIC_DOWNLOAD_HOST}"


def validate_ssh_alias(value: str) -> str:
    alias = value.strip()
    if not SSH_ALIAS_PATTERN.fullmatch(alias) or alias.startswith("-"):
        raise DeliveryError("SSH alias contains unsupported characters")
    return alias


def validate_remote_root(value: str) -> str:
    raw = value.strip()
    if not raw.startswith("/") or "\x00" in raw or any(char.isspace() for char in raw):
        raise DeliveryError("remote root must be an absolute POSIX path without whitespace")
    path = PurePosixPath(raw)
    if ".." in path.parts or any(not re.fullmatch(r"[A-Za-z0-9._-]+", part) for part in path.parts[1:]):
        raise DeliveryError("remote root contains an unsafe path component")
    return str(path)


def complete_text(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text or PLACEHOLDER_PATTERN.search(text):
        raise DeliveryError(f"{label} is missing or contains a placeholder")
    return text


def load_mapping(path: Path, label: str) -> dict[str, Any]:
    if not path.is_file():
        raise DeliveryError(f"{label} file does not exist: {path}")
    try:
        if path.suffix.lower() == ".json":
            value = json.loads(path.read_text(encoding="utf-8"))
        else:
            value = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, ValueError, yaml.YAMLError) as exc:
        raise DeliveryError(f"cannot read {label}: {exc}") from exc
    if not isinstance(value, dict):
        raise DeliveryError(f"{label} must contain an object")
    return value


def atomic_json(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(value, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except BaseException:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass
        raise


def atomic_yaml(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            yaml.safe_dump(dict(value), handle, allow_unicode=True, sort_keys=False)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except BaseException:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass
        raise


def verified_copy(source: Path, target: Path, expected_sha: str, expected_size: int) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if not target.is_file() or target.stat().st_size != expected_size or stream_sha256(target) != expected_sha:
            raise DeliveryError(f"refusing to overwrite a different artifact: {target}")
        return
    descriptor, temporary = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".tmp", dir=target.parent)
    os.close(descriptor)
    temp_path = Path(temporary)
    try:
        shutil.copyfile(source, temp_path)
        if temp_path.stat().st_size != expected_size or stream_sha256(temp_path) != expected_sha:
            raise DeliveryError(f"copied artifact failed SHA-256 verification: {target}")
        os.replace(temp_path, target)
    finally:
        temp_path.unlink(missing_ok=True)


@dataclass(frozen=True)
class ArtifactIdentity:
    release: str
    commit: str
    version_name: str
    version_code: int
    apk_file: str
    sha256: str
    size_bytes: int
    signing_fingerprint: str


def validate_build_evidence(
    path: Path,
    release: str,
    commit: str,
    version_name: str,
    version_code: int,
    expected_fingerprint: str,
) -> dict[str, Any]:
    evidence = load_mapping(path, "build evidence")
    expected = normalize_fingerprint(expected_fingerprint)
    if validate_release(str(evidence.get("release") or "")) != release:
        raise DeliveryError("build evidence release does not match")
    if validate_commit(str(evidence.get("commit") or "")) != commit:
        raise DeliveryError("build evidence commit does not match")
    if validate_version_name(str(evidence.get("version_name") or "")) != version_name:
        raise DeliveryError("build evidence versionName does not match")
    raw_version_code = evidence.get("version_code")
    if isinstance(raw_version_code, bool) or not isinstance(raw_version_code, int):
        raise DeliveryError("build evidence versionCode must be an integer")
    validate_version_code(release, raw_version_code)
    if raw_version_code != version_code:
        raise DeliveryError("build evidence versionCode does not match")
    if str(evidence.get("build_status") or "").upper() != "PASS":
        raise DeliveryError("build evidence is not PASS")
    checks = {str(value) for value in (evidence.get("checks") or [])}
    if not REQUIRED_BUILD_CHECKS <= checks:
        raise DeliveryError("build evidence is missing required Gradle/apksigner checks")
    if evidence.get("stable_signing") is not True:
        raise DeliveryError("formal prepare requires stable signing evidence")
    profile = complete_text(evidence.get("signing_profile_id"), "signing_profile_id")
    if profile.lower() in {"debug", "default-debug", "temporary", "generated"}:
        raise DeliveryError("temporary/default debug signing is forbidden for delivery")
    actual = normalize_fingerprint(str(evidence.get("signing_fingerprint") or ""))
    if actual != expected:
        raise DeliveryError("APK signing fingerprint does not match the fixed staging profile")
    built_at = complete_text(evidence.get("built_at"), "build evidence built_at")
    try:
        parsed_built_at = datetime.fromisoformat(built_at.replace("Z", "+00:00"))
    except ValueError as exc:
        raise DeliveryError("build evidence built_at must be an ISO-8601 timestamp") from exc
    if parsed_built_at.tzinfo is None:
        raise DeliveryError("build evidence built_at must include a timezone")
    api_url = str(evidence.get("api_base_url") or "").strip()
    parsed_api = urlsplit(api_url)
    if (
        parsed_api.scheme != "https"
        or not parsed_api.hostname
        or parsed_api.hostname.endswith(".invalid")
        or parsed_api.username is not None
        or parsed_api.password is not None
    ):
        raise DeliveryError("build evidence contains an unsafe API base URL")
    return evidence


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str = ""
    stderr: str = ""


class Runner(Protocol):
    def __call__(self, args: Sequence[str], timeout: int) -> CommandResult:
        ...


def subprocess_runner(args: Sequence[str], timeout: int) -> CommandResult:
    try:
        result = subprocess.run(
            list(args), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            timeout=timeout, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise DeliveryError(f"external command could not complete: {type(exc).__name__}") from exc
    return CommandResult(result.returncode, result.stdout, result.stderr)


def run_checked(runner: Runner, args: Sequence[str], timeout: int, label: str) -> CommandResult:
    result = runner(args, timeout)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        if len(detail) > 500:
            detail = detail[:500] + "..."
        raise DeliveryError(f"{label} failed" + (f": {detail}" if detail else ""))
    return result


@dataclass(frozen=True)
class RemotePublication:
    state: str
    remote_path: str
    sha256: str
    size_bytes: int


class RemotePublisher:
    def __init__(
        self,
        ssh_alias: str,
        remote_root: str,
        *,
        runner: Runner = subprocess_runner,
        chunk_size: int = 4 * 1024 * 1024,
        retries: int = 3,
        retry_delay: float = 1.0,
        nonce_factory: Callable[[], str] | None = None,
    ) -> None:
        self.ssh_alias = validate_ssh_alias(ssh_alias)
        self.remote_root = validate_remote_root(remote_root)
        if chunk_size <= 0:
            raise DeliveryError("chunk size must be positive")
        if retries < 1 or retries > 10:
            raise DeliveryError("upload retries must be between 1 and 10")
        self.runner = runner
        self.chunk_size = chunk_size
        self.retries = retries
        self.retry_delay = retry_delay
        self.nonce_factory = nonce_factory or (lambda: os.urandom(8).hex())

    def _ssh(self, command: str, label: str, timeout: int = 120) -> CommandResult:
        return run_checked(
            self.runner,
            ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=15", self.ssh_alias, command],
            timeout,
            label,
        )

    def _paths(self, release: str, apk_file: str) -> tuple[str, str]:
        expected_prefix = f"hhy-{validate_release(release).lower()}-"
        if not re.fullmatch(re.escape(expected_prefix) + r"[0-9a-f]{7}-debug\.apk", apk_file):
            raise DeliveryError("remote APK filename is not canonical")
        release_dir = str(PurePosixPath(self.remote_root) / f"{release.lower()}-artifacts")
        final_path = str(PurePosixPath(release_dir) / apk_file)
        return release_dir, final_path

    def publish(self, apk_path: Path, identity: ArtifactIdentity) -> RemotePublication:
        release_dir, final_path = self._paths(identity.release, identity.apk_file)
        nonce = self.nonce_factory()
        if not re.fullmatch(r"[0-9a-f]{8,32}", nonce):
            raise DeliveryError("upload nonce is invalid")
        staging_dir = str(PurePosixPath(release_dir) / ".uploads" / f"{identity.apk_file}.{nonce}")
        quoted_stage = shlex.quote(staging_dir)
        self._ssh(
            f"set -eu; mkdir -p {shlex.quote(release_dir)}; "
            f"chmod 0755 {shlex.quote(release_dir)}; "
            f"umask 077; mkdir -p {quoted_stage}",
            "remote staging preparation",
        )
        uploaded: list[str] = []
        try:
            with tempfile.TemporaryDirectory(prefix="hhy-apk-chunks-") as temporary:
                with apk_path.open("rb") as source:
                    index = 0
                    while True:
                        block = source.read(self.chunk_size)
                        if not block:
                            break
                        part_name = f"part-{index:06d}"
                        local_part = Path(temporary) / part_name
                        local_part.write_bytes(block)
                        remote_part = str(PurePosixPath(staging_dir) / part_name)
                        last_error: DeliveryError | None = None
                        for attempt in range(self.retries):
                            try:
                                run_checked(
                                    self.runner,
                                    [
                                        "scp", "-q", "-o", "BatchMode=yes", "-o", "ConnectTimeout=15",
                                        str(local_part), f"{self.ssh_alias}:{remote_part}",
                                    ],
                                    180,
                                    f"upload chunk {index}",
                                )
                                last_error = None
                                break
                            except DeliveryError as exc:
                                last_error = exc
                                if attempt + 1 < self.retries and self.retry_delay:
                                    time.sleep(self.retry_delay)
                        if last_error is not None:
                            raise last_error
                        uploaded.append(remote_part)
                        index += 1
            if not uploaded:
                raise DeliveryError("APK is empty")
            assembled = str(PurePosixPath(staging_dir) / "assembled.apk")
            part_arguments = " ".join(shlex.quote(value) for value in uploaded)
            command = (
                "set -eu; : HHY_ATOMIC_PUBLISH; "
                f"trap 'rm -rf {quoted_stage}' EXIT; "
                f"cat {part_arguments} > {shlex.quote(assembled)}; "
                f"test \"$(wc -c < {shlex.quote(assembled)} | tr -d '[:space:]')\" = {shlex.quote(str(identity.size_bytes))}; "
                f"test \"$(sha256sum {shlex.quote(assembled)} | cut -d ' ' -f1)\" = {shlex.quote(identity.sha256)}; "
                f"if [ -e {shlex.quote(final_path)} ]; then "
                f"test \"$(sha256sum {shlex.quote(final_path)} | cut -d ' ' -f1)\" = {shlex.quote(identity.sha256)}; "
                f"test \"$(wc -c < {shlex.quote(final_path)} | tr -d '[:space:]')\" = {shlex.quote(str(identity.size_bytes))}; "
                f"printf 'EXISTING {identity.sha256} {identity.size_bytes}\\n'; "
                "else "
                f"chmod 0644 {shlex.quote(assembled)}; mv {shlex.quote(assembled)} {shlex.quote(final_path)}; "
                f"printf 'CREATED {identity.sha256} {identity.size_bytes}\\n'; fi"
            )
            result = self._ssh(command, "remote SHA-256 verification and atomic publish", timeout=300)
            fields = result.stdout.strip().split()
            if len(fields) != 3 or fields[0] not in {"CREATED", "EXISTING"}:
                raise DeliveryError("remote publish returned an invalid result")
            try:
                remote_size = int(fields[2])
            except ValueError as exc:
                raise DeliveryError("remote publish returned an invalid size") from exc
            if fields[1].lower() != identity.sha256 or remote_size != identity.size_bytes:
                raise DeliveryError("remote publish result does not match the APK")
            return RemotePublication(fields[0], final_path, fields[1].lower(), remote_size)
        except BaseException:
            cleanup = f"rm -rf {quoted_stage}"
            try:
                self._ssh(cleanup, "remote staging cleanup", timeout=60)
            except DeliveryError:
                pass
            raise

    def verify(self, publication: RemotePublication) -> dict[str, Any]:
        command = (
            "set -eu; : HHY_REMOTE_VERIFY; "
            f"test -f {shlex.quote(publication.remote_path)}; "
            f"printf '%s %s\\n' \"$(sha256sum {shlex.quote(publication.remote_path)} | cut -d ' ' -f1)\" "
            f"\"$(wc -c < {shlex.quote(publication.remote_path)} | tr -d '[:space:]')\""
        )
        result = self._ssh(command, "remote artifact verification")
        fields = result.stdout.strip().split()
        try:
            remote_size = int(fields[1]) if len(fields) == 2 else -1
        except ValueError as exc:
            raise DeliveryError("remote artifact returned an invalid size") from exc
        if len(fields) != 2 or fields[0].lower() != publication.sha256 or remote_size != publication.size_bytes:
            raise DeliveryError("remote artifact hash or size mismatch")
        return {"status": "PASS", "sha256": fields[0].lower(), "size_bytes": remote_size}

    def retract_if_created(self, publication: RemotePublication) -> None:
        if publication.state != "CREATED":
            return
        command = (
            "set -eu; : HHY_RETRACT_CREATED; "
            f"if [ -f {shlex.quote(publication.remote_path)} ] && "
            f"[ \"$(sha256sum {shlex.quote(publication.remote_path)} | cut -d ' ' -f1)\" = {shlex.quote(publication.sha256)} ]; "
            f"then rm -f {shlex.quote(publication.remote_path)}; fi"
        )
        self._ssh(command, "retract newly published artifact", timeout=60)


class HttpResponse(Protocol):
    status: int
    headers: Mapping[str, str]

    def read(self, size: int = -1) -> bytes:
        ...

    def geturl(self) -> str:
        ...

    def __enter__(self) -> "HttpResponse":
        ...

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        ...


HttpOpener = Callable[[Request, int], HttpResponse]


def default_http_opener(request: Request, timeout: int) -> HttpResponse:
    return urlopen(request, timeout=timeout)  # type: ignore[return-value]


def verify_https_download(
    url: str,
    local_apk: Path,
    expected_sha: str,
    expected_size: int,
    *,
    opener: HttpOpener = default_http_opener,
    timeout: int = 120,
) -> dict[str, Any]:
    parsed = urlsplit(url)
    if (
        parsed.scheme != "https"
        or parsed.hostname != PUBLIC_DOWNLOAD_HOST
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise DeliveryError("download URL is outside the approved HTTPS host")
    request = Request(url, headers={
        "User-Agent": "hhy-apk-delivery/1",
        "Cache-Control": "no-cache",
    })
    digest = hashlib.sha256()
    downloaded = 0
    try:
        with opener(request, timeout) as response:
            status = int(getattr(response, "status", 0) or 0)
            if status != 200:
                raise DeliveryError(f"APK HTTPS download returned status {status}")
            if response.geturl() != url:
                raise DeliveryError("APK HTTPS download redirected away from the canonical URL")
            content_type = str(response.headers.get("Content-Type") or "").split(";", 1)[0].strip().lower()
            if content_type != "application/vnd.android.package-archive":
                raise DeliveryError("APK HTTPS response has an invalid Content-Type")
            content_length = str(response.headers.get("Content-Length") or "")
            if not content_length.isdigit() or int(content_length) != expected_size:
                raise DeliveryError("APK HTTPS Content-Length mismatch")
            disposition = str(response.headers.get("Content-Disposition") or "").lower()
            if "attachment" not in disposition:
                raise DeliveryError("APK HTTPS response is missing attachment disposition")
            while True:
                block = response.read(1024 * 1024)
                if not block:
                    break
                digest.update(block)
                downloaded += len(block)
    except DeliveryError:
        raise
    except HTTPError as exc:
        raise DeliveryError(f"APK HTTPS download returned HTTP {exc.code}") from exc
    except Exception as exc:
        raise DeliveryError(f"APK HTTPS download failed: {type(exc).__name__}") from exc
    if downloaded != expected_size or digest.hexdigest() != expected_sha:
        raise DeliveryError("APK HTTPS body hash or size mismatch")

    range_end = min(1023, expected_size - 1)
    if range_end < 0:
        raise DeliveryError("APK is empty")
    range_request = Request(
        url,
        headers={
            "User-Agent": "hhy-apk-delivery/1",
            "Cache-Control": "no-cache",
            "Range": f"bytes=0-{range_end}",
        },
    )
    try:
        with opener(range_request, timeout) as response:
            status = int(getattr(response, "status", 0) or 0)
            if status != 206:
                raise DeliveryError(f"APK Range request returned status {status}")
            expected_content_range = f"bytes 0-{range_end}/{expected_size}"
            if str(response.headers.get("Content-Range") or "").lower() != expected_content_range:
                raise DeliveryError("APK Range Content-Range mismatch")
            range_body = response.read(range_end + 2)
    except DeliveryError:
        raise
    except HTTPError as exc:
        raise DeliveryError(f"APK Range request returned HTTP {exc.code}") from exc
    except Exception as exc:
        raise DeliveryError(f"APK Range verification failed: {type(exc).__name__}") from exc
    with local_apk.open("rb") as handle:
        local_prefix = handle.read(range_end + 1)
    if range_body != local_prefix:
        raise DeliveryError("APK Range body does not match the local artifact")
    return {
        "status": "PASS",
        "http_status": 200,
        "range_status": 206,
        "content_type": "application/vnd.android.package-archive",
        "sha256": expected_sha,
        "size_bytes": expected_size,
        "verified_at": utc_now(),
    }


@dataclass(frozen=True)
class PrepareConfig:
    release: str
    commit: str
    version_name: str
    version_code: int
    apk: Path
    build_evidence: Path
    expected_signing_fingerprint: str
    desktop_dir: Path
    artifact_root: Path
    evidence_root: Path
    public_base_url: str


class Publisher(Protocol):
    def publish(self, apk_path: Path, identity: ArtifactIdentity) -> RemotePublication:
        ...

    def verify(self, publication: RemotePublication) -> dict[str, Any]:
        ...

    def retract_if_created(self, publication: RemotePublication) -> None:
        ...


HttpsVerifier = Callable[[str, Path, str, int], dict[str, Any]]


def prepare_delivery(
    config: PrepareConfig,
    publisher: Publisher,
    *,
    https_verifier: HttpsVerifier = verify_https_download,
    dry_run: bool = False,
) -> dict[str, Any]:
    release = validate_release(config.release)
    commit = validate_commit(config.commit)
    version_name = validate_version_name(config.version_name)
    version_code = validate_version_code(release, config.version_code)
    public_base = validate_public_base_url(config.public_base_url)
    if not config.apk.is_file():
        raise DeliveryError(f"APK does not exist: {config.apk}")
    size = config.apk.stat().st_size
    if size <= 0:
        raise DeliveryError("APK is empty")
    sha = stream_sha256(config.apk)
    expected_fingerprint = normalize_fingerprint(config.expected_signing_fingerprint)
    build_evidence = validate_build_evidence(
        config.build_evidence, release, commit, version_name, version_code, expected_fingerprint,
    )
    apk_file = canonical_apk_name(release, commit)
    identity = ArtifactIdentity(
        release, commit, version_name, version_code, apk_file, sha, size, expected_fingerprint,
    )
    artifact_dir = config.artifact_root / release
    artifact_apk = artifact_dir / apk_file
    desktop_apk = config.desktop_dir / apk_file
    evidence_path = config.evidence_root / f"{release.lower()}-apk-delivery" / "delivery-evidence.json"
    manifest_path = artifact_dir / "APK_MANIFEST.yaml"
    remote_relative = f"{release.lower()}-artifacts/{apk_file}"
    download_url = f"{public_base}/{remote_relative}"
    plan = {
        "release": release,
        "commit": commit,
        "apk_file": apk_file,
        "sha256": sha,
        "size_bytes": size,
        "artifact_copy": str(artifact_apk),
        "desktop_copy": str(desktop_apk),
        "download_url": download_url,
        "manifest": str(manifest_path),
        "delivery_evidence": str(evidence_path),
        "dry_run": dry_run,
    }
    if dry_run:
        return plan

    if manifest_path.exists() or evidence_path.exists():
        raise DeliveryError("delivery state already exists; use verify or accept instead of prepare")

    verified_copy(config.apk, artifact_apk, sha, size)
    verified_copy(config.apk, desktop_apk, sha, size)
    publication: RemotePublication | None = None
    try:
        publication = publisher.publish(artifact_apk, identity)
        remote_evidence = publisher.verify(publication)
        https_evidence = https_verifier(download_url, artifact_apk, sha, size)
        if remote_evidence.get("status") != "PASS" or https_evidence.get("status") != "PASS":
            raise DeliveryError("remote or HTTPS verification did not pass")
        created_at = utc_now()
        evidence: dict[str, Any] = {
            "schema_version": 1,
            "release": release,
            "commit": commit,
            "apk_file": apk_file,
            "version_name": version_name,
            "version_code": version_code,
            "sha256": sha,
            "size_bytes": size,
            "signing": {
                "status": "PASS",
                "profile_id": build_evidence["signing_profile_id"],
                "fingerprint": expected_fingerprint,
                "stable": True,
            },
            "local": {"status": "PASS", "artifact_copy": str(artifact_apk)},
            "desktop": {"status": "PASS", "path": str(desktop_apk), "sha256": sha},
            "remote": {**remote_evidence, "path": publication.remote_path, "publish_state": publication.state},
            "https": {**https_evidence, "url": download_url},
            "owner_physical_test": {"status": "PENDING"},
            "created_at": created_at,
        }
        manifest: dict[str, Any] = {
            "manifest_schema": 2,
            "release": release,
            "apk_file": apk_file,
            "version_name": version_name,
            "version_code": version_code,
            "commit": commit,
            "sha256": sha,
            "size_bytes": size,
            "signing_fingerprint": expected_fingerprint,
            "built_at": complete_text(build_evidence.get("built_at"), "build evidence built_at"),
            "delivery_status": "PASS",
            "test_status": "PENDING",
            "owner_physical_test": "PENDING",
            "download_url": download_url,
            "desktop_copy": str(desktop_apk),
            "delivery_evidence": str(evidence_path.relative_to(ROOT) if evidence_path.is_relative_to(ROOT) else evidence_path),
        }
        atomic_json(evidence_path, evidence)
        atomic_yaml(manifest_path, manifest)
    except BaseException:
        if publication is not None and publication.state == "CREATED":
            try:
                publisher.retract_if_created(publication)
            except DeliveryError:
                pass
        evidence_path.unlink(missing_ok=True)
        manifest_path.unlink(missing_ok=True)
        raise
    return {**plan, "dry_run": False, "status": "PENDING_OWNER_ACCEPTANCE"}


def manifest_and_evidence_paths(
    release: str, artifact_root: Path, evidence_root: Path,
) -> tuple[Path, Path]:
    release = validate_release(release)
    return (
        artifact_root / release / "APK_MANIFEST.yaml",
        evidence_root / f"{release.lower()}-apk-delivery" / "delivery-evidence.json",
    )


def load_manifest_delivery(
    release: str, artifact_root: Path, evidence_root: Path,
) -> tuple[Path, dict[str, Any], Path, dict[str, Any]]:
    manifest_path, evidence_path = manifest_and_evidence_paths(release, artifact_root, evidence_root)
    manifest = load_mapping(manifest_path, "APK manifest")
    evidence = load_mapping(evidence_path, "delivery evidence")
    if manifest.get("manifest_schema") != 2 or evidence.get("schema_version") != 1:
        raise DeliveryError("APK manifest/evidence schema is unsupported")
    if validate_release(str(manifest.get("release") or "")) != release:
        raise DeliveryError("APK manifest release does not match")
    if validate_release(str(evidence.get("release") or "")) != release:
        raise DeliveryError("delivery evidence release does not match")
    for field in ["commit", "apk_file", "version_name", "version_code", "sha256", "size_bytes"]:
        if manifest.get(field) != evidence.get(field):
            raise DeliveryError(f"APK manifest/evidence mismatch: {field}")
    return manifest_path, manifest, evidence_path, evidence


def verify_existing_delivery(
    release: str,
    artifact_root: Path,
    evidence_root: Path,
    publisher: Publisher,
    *,
    https_verifier: HttpsVerifier = verify_https_download,
    dry_run: bool = False,
) -> dict[str, Any]:
    release = validate_release(release)
    manifest_path, manifest, evidence_path, evidence = load_manifest_delivery(release, artifact_root, evidence_root)
    apk_file = complete_text(manifest.get("apk_file"), "apk_file")
    commit = validate_commit(str(manifest.get("commit") or ""))
    if apk_file != canonical_apk_name(release, commit):
        raise DeliveryError("APK filename is not canonical")
    version_code = manifest.get("version_code")
    if not isinstance(version_code, int) or isinstance(version_code, bool):
        raise DeliveryError("versionCode is invalid")
    validate_version_code(release, version_code)
    sha = str(manifest.get("sha256") or "").lower()
    size = manifest.get("size_bytes")
    if not FINGERPRINT_PATTERN.fullmatch(sha) or not isinstance(size, int) or size <= 0:
        raise DeliveryError("manifest hash or size is invalid")
    artifact_apk = artifact_root / release / apk_file
    desktop_apk = Path(complete_text(manifest.get("desktop_copy"), "desktop_copy"))
    for label, path in [("artifact", artifact_apk), ("desktop", desktop_apk)]:
        if not path.is_file() or path.stat().st_size != size or stream_sha256(path) != sha:
            raise DeliveryError(f"{label} APK no longer matches the manifest")
    if dry_run:
        return {"status": "DRY_RUN", "manifest": str(manifest_path), "evidence": str(evidence_path)}
    remote = evidence.get("remote") if isinstance(evidence.get("remote"), dict) else {}
    publication = RemotePublication(
        str(remote.get("publish_state") or "EXISTING"),
        complete_text(remote.get("path"), "remote.path"),
        sha,
        size,
    )
    remote_evidence = publisher.verify(publication)
    https_evidence = https_verifier(complete_text(manifest.get("download_url"), "download_url"), artifact_apk, sha, size)
    if remote_evidence.get("status") != "PASS" or https_evidence.get("status") != "PASS":
        raise DeliveryError("delivery re-verification failed")
    evidence["remote"] = {**remote, **remote_evidence}
    evidence["https"] = {**(evidence.get("https") or {}), **https_evidence}
    evidence["last_verified_at"] = utc_now()
    atomic_json(evidence_path, evidence)
    return {"status": "PASS", "manifest": str(manifest_path), "evidence": str(evidence_path)}


def accept_owner_test(
    release: str,
    artifact_root: Path,
    evidence_root: Path,
    confirmation: str,
    *,
    evidence_reference: str | None = None,
    dry_run: bool = False,
) -> dict[str, Any]:
    release = validate_release(release)
    confirmation = complete_text(confirmation, "owner confirmation")
    if len(confirmation) < 10:
        raise DeliveryError("owner confirmation is too short")
    manifest_path, manifest, evidence_path, evidence = load_manifest_delivery(release, artifact_root, evidence_root)
    for section in ["signing", "local", "desktop", "remote", "https"]:
        value = evidence.get(section)
        if not isinstance(value, dict) or value.get("status") != "PASS":
            raise DeliveryError(f"machine delivery section is not PASS: {section}")
    sha = str(manifest.get("sha256") or "")
    size = manifest.get("size_bytes")
    desktop = Path(complete_text(manifest.get("desktop_copy"), "desktop_copy"))
    if not isinstance(size, int) or not desktop.is_file() or desktop.stat().st_size != size or stream_sha256(desktop) != sha:
        raise DeliveryError("desktop APK does not match the accepted manifest")
    current_manifest = str(manifest.get("owner_physical_test") or "").upper()
    owner = evidence.get("owner_physical_test") if isinstance(evidence.get("owner_physical_test"), dict) else {}
    current_evidence = str(owner.get("status") or "").upper()
    if current_manifest == "PASS" and current_evidence == "PASS":
        if manifest.get("owner_confirmation") != confirmation:
            raise DeliveryError("owner acceptance is immutable and has different confirmation text")
        return {"status": "PASS", "idempotent": True, "manifest": str(manifest_path)}
    if current_manifest != "PENDING" or current_evidence not in {"PENDING", "PASS"}:
        raise DeliveryError("owner acceptance can only transition from PENDING to PASS")
    accepted_at = utc_now()
    owner_result: dict[str, Any] = {
        "status": "PASS",
        "confirmation": confirmation,
        "accepted_at": accepted_at,
    }
    if evidence_reference:
        owner_result["evidence_reference"] = complete_text(evidence_reference, "owner evidence reference")
    if dry_run:
        return {"status": "DRY_RUN", "manifest": str(manifest_path), "evidence": str(evidence_path)}
    evidence["owner_physical_test"] = owner_result
    manifest["owner_physical_test"] = "PASS"
    manifest["owner_confirmation"] = confirmation
    manifest["owner_accepted_at"] = accepted_at
    if evidence_reference:
        manifest["owner_evidence_reference"] = owner_result["evidence_reference"]
    manifest["test_status"] = "PASS"
    atomic_json(evidence_path, evidence)
    atomic_yaml(manifest_path, manifest)
    return {"status": "PASS", "idempotent": False, "manifest": str(manifest_path)}


def add_storage_arguments(parser: ArgumentParser) -> None:
    parser.add_argument("--artifact-root", type=Path, default=ROOT / "artifacts" / "apk")
    parser.add_argument("--evidence-root", type=Path, default=ROOT / "artifacts" / "validation")


def add_remote_arguments(parser: ArgumentParser) -> None:
    parser.add_argument("--ssh-alias", required=True, help="Preconfigured SSH alias; credentials are not accepted")
    parser.add_argument("--remote-root", required=True, help="Absolute remote download root")
    parser.add_argument("--chunk-size-mib", type=int, default=4)
    parser.add_argument("--upload-retries", type=int, default=3)


def build_parser() -> ArgumentParser:
    parser = ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare = subparsers.add_parser("prepare", help="Validate, copy, publish, and verify an APK")
    prepare.add_argument("--release", required=True)
    prepare.add_argument("--commit", required=True)
    prepare.add_argument("--version-name", required=True)
    prepare.add_argument("--version-code", required=True, type=int)
    prepare.add_argument("--apk", required=True, type=Path)
    prepare.add_argument("--build-evidence", required=True, type=Path)
    prepare.add_argument("--expected-signing-fingerprint", required=True)
    prepare.add_argument("--desktop-dir", type=Path, default=Path.home() / "Desktop")
    prepare.add_argument("--public-base-url", default=f"https://{PUBLIC_DOWNLOAD_HOST}")
    prepare.add_argument("--dry-run", action="store_true")
    add_storage_arguments(prepare)
    add_remote_arguments(prepare)

    verify = subparsers.add_parser("verify", help="Re-verify local, desktop, remote, and HTTPS copies")
    verify.add_argument("--release", required=True)
    verify.add_argument("--dry-run", action="store_true")
    add_storage_arguments(verify)
    add_remote_arguments(verify)

    accept = subparsers.add_parser("accept", help="Record explicit project-owner physical-device acceptance")
    accept.add_argument("--release", required=True)
    accept.add_argument("--confirmation", required=True)
    accept.add_argument("--evidence-reference")
    accept.add_argument("--dry-run", action="store_true")
    add_storage_arguments(accept)
    return parser


def make_publisher(args: Namespace) -> RemotePublisher:
    return RemotePublisher(
        args.ssh_alias,
        args.remote_root,
        chunk_size=args.chunk_size_mib * 1024 * 1024,
        retries=args.upload_retries,
    )


def reject_credential_arguments(argv: Sequence[str]) -> None:
    for argument in argv:
        option = argument.split("=", 1)[0]
        if option in FORBIDDEN_CREDENTIAL_OPTIONS:
            raise DeliveryError("credential options are forbidden; use a preconfigured SSH alias and agent")


def main(argv: Sequence[str] | None = None) -> int:
    actual = list(sys.argv[1:] if argv is None else argv)
    try:
        reject_credential_arguments(actual)
        args = build_parser().parse_args(actual)
        if args.command == "prepare":
            result = prepare_delivery(
                PrepareConfig(
                    args.release,
                    args.commit,
                    args.version_name,
                    args.version_code,
                    args.apk,
                    args.build_evidence,
                    args.expected_signing_fingerprint,
                    args.desktop_dir,
                    args.artifact_root,
                    args.evidence_root,
                    args.public_base_url,
                ),
                make_publisher(args),
                dry_run=args.dry_run,
            )
        elif args.command == "verify":
            result = verify_existing_delivery(
                args.release,
                args.artifact_root,
                args.evidence_root,
                make_publisher(args),
                dry_run=args.dry_run,
            )
        else:
            result = accept_owner_test(
                args.release,
                args.artifact_root,
                args.evidence_root,
                args.confirmation,
                evidence_reference=args.evidence_reference,
                dry_run=args.dry_run,
            )
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except DeliveryError as exc:
        print(f"APK_DELIVERY_FAILED: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
