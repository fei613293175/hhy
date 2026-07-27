#!/usr/bin/env python3
"""Validate release artifacts with distinct machine and production close gates."""
from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
from typing import Any
from urllib.parse import urlparse
import csv
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

import yaml

from check_ui_visual_acceptance import validate_historical as validate_ui_visual_historical


ROOT = Path(__file__).resolve().parents[1]
TERMINAL_RELEASE_STATUSES = {"DONE", "COMPLETED", "CLOSED", "RELEASED"}
MACHINE_RELEASE_STATUSES = TERMINAL_RELEASE_STATUSES | {
    "MACHINE_COMPLETE", "MACHINE_COMPLETE_OWNER_PENDING",
}
TERMINAL_TEST_STATUSES = {"PASS", "PASSED", "GREEN", "SUCCESS"}
COMMIT_PATTERN = re.compile(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")
TAG_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._/-]*")
PLACEHOLDER_PATTERN = re.compile(
    r"PENDING|PLACEHOLDER|\bTODO\b|\bTBD\b|NOT_INITIALIZED|NOT_RUN|UNKNOWN|<[^>]+>",
    re.IGNORECASE,
)
GOVERNANCE_AUDIT_EFFECTIVE_RELEASE = 12
GOVERNANCE_AUDIT_CHECKS = {
    "development_documents",
    "hard_gate_enforcement",
    "development_progress",
    "reusable_patterns",
    "problem_registry",
    "pitfalls",
}
ON_DEMAND_ANDROID_MODE = "ON_DEMAND_NON_BLOCKING_SPECIALTY"
REQUIRED_TEST_APK_CHECKS = {
    "verifyApiBaseUrl",
    "testDebugUnitTest",
    "lintDebug",
    "assembleDebug",
    "apksigner",
    "zipalign",
    "embeddedApiBaseUrl",
    "packageIdentity",
}


def git_executable() -> str:
    override = os.environ.get("HHY_GIT_BIN", "").strip()
    if override:
        candidate = Path(override).expanduser()
        if not candidate.is_file():
            raise OSError(f"HHY_GIT_BIN不存在：{candidate}")
        return str(candidate)
    system_git = shutil.which("git")
    if system_git:
        return system_git
    bundled = Path.home() / ".cache/codex-runtimes/codex-primary-runtime/dependencies/native/git/cmd/git.exe"
    if bundled.is_file():
        return str(bundled)
    raise OSError("Git不可执行：请安装Git或设置HHY_GIT_BIN")


def release_number(value: str) -> int | None:
    match = re.fullmatch(r"R(\d{2})", value.upper())
    return int(match.group(1)) if match else None


def load_yaml(path: Path) -> dict[str, Any]:
    value = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if not isinstance(value, dict):
        raise ValueError(f"{path.relative_to(ROOT)} 顶层必须为对象")
    return value


def read_release_plan() -> list[dict[str, str]]:
    with (ROOT / "catalogs/release_plan.csv").open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def regular_check(selected_release: str | None = None) -> int:
    rows = read_release_plan()
    if selected_release:
        rows = [row for row in rows if row.get("版本") == selected_release]
        if not rows:
            print("RELEASE_NOT_IN_PLAN", selected_release)
            return 1
    for row in rows:
        release = row["版本"]
        directory = ROOT / "releases" / release
        for name in ["RELEASE_MANIFEST.yaml", "TASKS.yaml", "ACCEPTANCE_MATRIX.csv"]:
            if not (directory / name).is_file():
                print("RELEASE_ARTIFACT_MISSING", release, name)
                return 1
        manifest = load_yaml(directory / "RELEASE_MANIFEST.yaml")
        if not manifest.get("requirements"):
            print("RELEASE_REQUIREMENTS_EMPTY", release)
            return 1
        apk_expected = row.get("Android测试APK") == "YES"
        if apk_expected != bool(manifest.get("android_test_apk_required")):
            print("APK_POLICY_MISMATCH", release)
            return 1
    print("RELEASE_ARTIFACTS_OK", len(rows))
    return 0


class CloseGate:
    def __init__(self, release: str, stage: str) -> None:
        self.release = release
        self.stage = stage
        self.production = stage == "production"
        self.errors: list[tuple[str, str]] = []

    def require(self, condition: bool, code: str, message: str) -> None:
        if not condition:
            self.errors.append((code, message))

    def complete_string(self, value: Any) -> bool:
        return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER_PATTERN.search(value)

    def validate_tasks(self) -> tuple[dict[str, Any], list[str]]:
        path = ROOT / "releases" / self.release / "TASKS.yaml"
        document = load_yaml(path)
        tasks = document.get("tasks") or []
        self.require(isinstance(tasks, list), "TASKS_FORMAT", "tasks 必须为列表")
        if not isinstance(tasks, list):
            return document, []
        expected = [f"TASK-{self.release}-{number:03d}" for number in range(1, 9)]
        actual = [str(row.get("id") or "") for row in tasks if isinstance(row, dict)]
        self.require(actual == expected, "TASKS_EXACT_SET", f"必须按顺序包含8个任务：{expected}")
        if self.production:
            unfinished = [
                str(row.get("id") or "") for row in tasks
                if not isinstance(row, dict) or str(row.get("status") or "").upper() != "DONE"
            ]
            self.require(not unfinished, "TASKS_NOT_DONE", f"未完成任务：{unfinished}")
        elif len(tasks) == 8:
            unfinished_prerequisites = [
                str(row.get("id") or "") for row in tasks[:-1]
                if not isinstance(row, dict) or str(row.get("status") or "").upper() != "DONE"
            ]
            final_status = str(tasks[-1].get("status") or "").upper() if isinstance(tasks[-1], dict) else ""
            self.require(
                not unfinished_prerequisites,
                "MACHINE_CLOSE_PREREQUISITE_NOT_DONE",
                f"机器收尾前七项任务未完成：{unfinished_prerequisites}",
            )
            self.require(
                final_status in {"READY", "IN_PROGRESS", "DONE"},
                "MACHINE_CLOSE_TASK_STATE_INVALID",
                f"最终收尾任务状态非法：{final_status or 'EMPTY'}",
            )
        return document, actual

    def validate_acceptance(self) -> list[dict[str, str]]:
        path = ROOT / "releases" / self.release / "ACCEPTANCE_MATRIX.csv"
        with path.open(encoding="utf-8-sig", newline="") as handle:
            rows = list(csv.DictReader(handle))
        expected = [f"AC-{self.release}-{number:03d}" for number in range(1, 7)]
        actual = [row.get("验收ID") or row.get("acceptance_id") or "" for row in rows]
        self.require(actual == expected, "AC_EXACT_SET", f"必须按顺序包含6个AC：{expected}")
        for row in rows:
            acceptance_id = row.get("验收ID") or row.get("acceptance_id") or "UNKNOWN"
            release = row.get("版本") or row.get("release")
            status = str(row.get("状态") or row.get("status") or "").upper()
            evidence = str(row.get("证据路径") or row.get("evidence_path") or "").strip()
            self.require(release == self.release, "AC_RELEASE_MISMATCH", f"{acceptance_id} 版本应为 {self.release}")
            self.require(status == "PASS", "AC_NOT_PASS", f"{acceptance_id} 状态为 {status or 'EMPTY'}")
            if not evidence:
                self.require(False, "AC_EVIDENCE_MISSING", f"{acceptance_id} 缺少证据路径")
                continue
            evidence_path = (ROOT / evidence).resolve()
            try:
                evidence_path.relative_to(ROOT.resolve())
                inside_root = True
            except ValueError:
                inside_root = False
            self.require(inside_root, "AC_EVIDENCE_OUTSIDE_ROOT", f"{acceptance_id} 证据越出仓库：{evidence}")
            self.require(evidence_path.is_file(), "AC_EVIDENCE_NOT_FILE", f"{acceptance_id} 证据文件不存在：{evidence}")
        return rows

    def manifest_operations(self, manifest: dict[str, Any]) -> list[str]:
        values: list[Any] = []
        raw = manifest.get("operation_ids")
        if isinstance(raw, list):
            values.extend(raw)
        contracts = manifest.get("contracts") or {}
        if isinstance(contracts, dict):
            for item in contracts.values():
                if isinstance(item, list):
                    values.extend(item)
                elif isinstance(item, dict):
                    nested = item.get("operation_ids")
                    if isinstance(nested, list):
                        values.extend(nested)
        return [str(value).strip() for value in values if str(value).strip()]

    def known_operation_ids(self) -> set[str]:
        result: set[str] = set()
        pattern = re.compile(r"^\s*operationId:\s*['\"]?([^'\"\s#]+)", re.MULTILINE)
        for relative in ["contracts/openapi.yaml", "contracts/admin-openapi.yaml"]:
            path = ROOT / relative
            if path.is_file():
                result.update(pattern.findall(path.read_text(encoding="utf-8")))
        return result

    def git_commit(self, commit: Any, code: str) -> str | None:
        value = str(commit or "").strip()
        self.require(bool(COMMIT_PATTERN.fullmatch(value)) and set(value) != {"0"}, code, f"非法Commit：{value or 'EMPTY'}")
        if not COMMIT_PATTERN.fullmatch(value) or set(value) == {"0"}:
            return None
        result = subprocess.run(
            [git_executable(), "rev-parse", "--verify", f"{value}^{{commit}}"], cwd=ROOT,
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        self.require(result.returncode == 0, code, f"Git中不存在Commit：{value}")
        return result.stdout.strip().lower() if result.returncode == 0 else None

    def git_tag(self, tag: Any, expected_commit: str | None) -> str | None:
        value = str(tag or "").strip()
        valid = bool(TAG_PATTERN.fullmatch(value)) and ".." not in value and not PLACEHOLDER_PATTERN.search(value)
        self.require(valid, "MANIFEST_TAG_INVALID", f"非法Release Tag：{value or 'EMPTY'}")
        if not valid:
            return None
        result = subprocess.run(
            [git_executable(), "rev-parse", "--verify", f"refs/tags/{value}^{{commit}}"], cwd=ROOT,
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        self.require(result.returncode == 0, "MANIFEST_TAG_INVALID", f"Git中不存在Release Tag：{value}")
        resolved = result.stdout.strip().lower() if result.returncode == 0 else None
        if resolved and expected_commit:
            self.require(resolved == expected_commit, "MANIFEST_TAG_COMMIT_MISMATCH", f"Tag {value} 未指向Release Commit")
        return resolved

    def validate_manifest(self) -> tuple[dict[str, Any], str | None]:
        path = ROOT / "releases" / self.release / "RELEASE_MANIFEST.yaml"
        manifest = load_yaml(path)
        status = str(manifest.get("status") or "").upper()
        self.require(manifest.get("release") == self.release, "MANIFEST_RELEASE_MISMATCH", "Manifest release不一致")
        allowed_statuses = TERMINAL_RELEASE_STATUSES if self.production else MACHINE_RELEASE_STATUSES
        self.require(
            status in allowed_statuses,
            "MANIFEST_NOT_TERMINAL" if self.production else "MANIFEST_NOT_MACHINE_COMPLETE",
            f"Manifest状态不符合{self.stage}关闭要求：{status or 'EMPTY'}",
        )

        operations = self.manifest_operations(manifest)
        if self.production:
            self.require(bool(operations), "MANIFEST_OPERATION_COUNT", "生产验收必须声明至少1个operationId")
        self.require(len(set(operations)) == len(operations), "MANIFEST_OPERATION_DUPLICATE", "operationId存在重复")
        invalid = [value for value in operations if not self.complete_string(value)]
        self.require(not invalid, "MANIFEST_OPERATION_PLACEHOLDER", f"operationId含占位值：{invalid}")
        unknown = sorted(set(operations) - self.known_operation_ids())
        self.require(not unknown, "MANIFEST_OPERATION_UNKNOWN", f"OpenAPI中不存在operationId：{unknown}")

        resolved_commit = None
        if self.production:
            git_block = manifest.get("git") if isinstance(manifest.get("git"), dict) else {}
            commit = manifest.get("release_commit") or manifest.get("commit") or git_block.get("commit")
            tag = manifest.get("release_tag") or manifest.get("tag") or git_block.get("tag")
            resolved_commit = self.git_commit(commit, "MANIFEST_COMMIT_INVALID")
            self.git_tag(tag, resolved_commit)
        return manifest, resolved_commit

    def pending_paths(self, value: Any, prefix: str = "") -> list[str]:
        result: list[str] = []
        if isinstance(value, dict):
            for key, item in value.items():
                result.extend(self.pending_paths(item, f"{prefix}.{key}" if prefix else str(key)))
        elif isinstance(value, list):
            for index, item in enumerate(value):
                result.extend(self.pending_paths(item, f"{prefix}[{index}]"))
        elif isinstance(value, str) and "PENDING" in value.upper():
            result.append(prefix)
        return result

    def validate_apk(self, manifest: dict[str, Any]) -> str | None:
        if not manifest.get("android_test_apk_required"):
            return None
        path = ROOT / "artifacts" / "apk" / self.release / "APK_MANIFEST.yaml"
        self.require(path.is_file(), "APK_MANIFEST_MISSING", f"APK Manifest不存在：{path.relative_to(ROOT)}")
        if not path.is_file():
            return None
        apk = load_yaml(path)
        self.require(apk.get("release") == self.release, "APK_RELEASE_MISMATCH", "APK release不一致")
        pending = self.pending_paths(apk)
        if not self.production:
            pending = [value for value in pending if value not in {"test_status", "owner_physical_test"}]
        self.require(not pending, "APK_PENDING_VALUE", f"APK Manifest仍含PENDING：{pending}")

        apk_file = apk.get("apk_file")
        self.require(self.complete_string(apk_file), "APK_FILE_MISSING", "apk_file不完整")
        sha = str(apk.get("sha256") or "").strip().lower()
        sha_valid = bool(re.fullmatch(r"[0-9a-f]{64}", sha)) and set(sha) != {"0"}
        self.require(sha_valid, "APK_SHA_INVALID", f"APK SHA256非法：{sha or 'EMPTY'}")
        version_name = apk.get("version_name")
        self.require(self.complete_string(version_name), "APK_VERSION_NAME_INVALID", "version_name不完整")
        version_code = apk.get("version_code")
        self.require(isinstance(version_code, int) and not isinstance(version_code, bool) and version_code > 0, "APK_VERSION_CODE_INVALID", f"version_code非法：{version_code}")
        apk_commit = self.git_commit(apk.get("commit"), "APK_COMMIT_INVALID")
        delivery = manifest.get("android_delivery") if isinstance(manifest.get("android_delivery"), dict) else {}
        declared_candidate = delivery.get("source_commit") or apk.get("commit")
        candidate_commit = self.git_commit(declared_candidate, "ANDROID_CANDIDATE_COMMIT_INVALID")
        if apk_commit and candidate_commit:
            self.require(apk_commit == candidate_commit, "APK_COMMIT_MISMATCH", "APK Commit与声明的Android候选Commit不一致")
        test_status = str(apk.get("test_status") or "").upper()
        allowed_test_statuses = TERMINAL_TEST_STATUSES if self.production else TERMINAL_TEST_STATUSES | {"PENDING"}
        self.require(test_status in allowed_test_statuses, "APK_TEST_NOT_PASS", f"APK测试状态非法：{test_status or 'EMPTY'}")
        url = str(apk.get("download_url") or "").strip()
        parsed = urlparse(url)
        self.require(
            self.complete_string(url) and parsed.scheme == "https" and bool(parsed.netloc),
            "APK_URL_INVALID", f"APK下载URL非法：{url or 'EMPTY'}",
        )
        size = apk.get("size_bytes")
        self.require(isinstance(size, int) and not isinstance(size, bool) and size > 0, "APK_SIZE_INVALID", f"APK大小非法：{size}")

        if self.complete_string(apk_file):
            local_apk = path.parent / str(apk_file)
            if local_apk.is_file():
                actual_sha = hashlib.sha256(local_apk.read_bytes()).hexdigest()
                self.require(actual_sha == sha, "APK_SHA_MISMATCH", "本地APK内容与Manifest SHA256不一致")
                self.require(local_apk.stat().st_size == size, "APK_SIZE_MISMATCH", "本地APK大小与Manifest不一致")
        return candidate_commit

    def validate_android_automation(self, manifest: dict[str, Any], expected_candidate_commit: str | None) -> None:
        number = release_number(self.release)
        if not manifest.get("android_test_apk_required") or number is None or number < 6:
            return
        automation = manifest.get("android_automation")
        self.require(isinstance(automation, dict), "ANDROID_AUTOMATION_MISSING", "R06起必须记录Android自动门禁结果")
        if not isinstance(automation, dict):
            return
        self.require(automation.get("policy_id") == "HHY-ANDROID-AUTOMATION-V1", "ANDROID_POLICY_MISMATCH", "Android自动化策略版本不一致")
        if not self.production and automation.get("mode") == ON_DEMAND_ANDROID_MODE:
            self.validate_test_apk_delivery(manifest, expected_candidate_commit)
            owner_status = str(automation.get("owner_physical_test") or "").upper()
            self.require(owner_status in {"PENDING", "PASS"}, "ANDROID_OWNER_TEST_STATE_INVALID", f"项目所有者真机验收状态不符合machine关闭要求：{owner_status or 'EMPTY'}")
            self.require(automation.get("next_release_development") == "ALLOWED", "ANDROID_NEXT_RELEASE_BLOCKED", "按需自动化模式必须允许合格TEST_APK继续下一版本")
            return
        self.require(str(automation.get("status") or "").upper() == "PASS", "ANDROID_AUTOMATION_NOT_PASS", "Android自动门禁不是PASS")
        self.require(automation.get("owner_test_allowed") is True, "ANDROID_OWNER_TEST_NOT_ALLOWED", "自动门禁尚未允许项目所有者真机测试")
        owner_status = str(automation.get("owner_physical_test") or "").upper()
        allowed_owner_statuses = {"PASS"} if self.production else {"PENDING", "PASS"}
        self.require(
            owner_status in allowed_owner_statuses,
            "ANDROID_OWNER_TEST_NOT_PASS" if self.production else "ANDROID_OWNER_TEST_STATE_INVALID",
            f"项目所有者真机验收状态不符合{self.stage}关闭要求：{owner_status or 'EMPTY'}",
        )
        candidate_commit = str(automation.get("commit") or "").lower()
        if expected_candidate_commit:
            self.require(candidate_commit == expected_candidate_commit, "ANDROID_CANDIDATE_COMMIT_MISMATCH", "Android自动化Commit与声明的候选Commit不一致")
        report_value = str(automation.get("candidate_report") or "").strip()
        report_path = (ROOT / report_value).resolve()
        try:
            report_path.relative_to(ROOT.resolve())
            report_inside = True
        except ValueError:
            report_inside = False
        self.require(report_inside and report_path.is_file(), "ANDROID_CANDIDATE_REPORT_MISSING", f"Android候选报告不存在：{report_value or 'EMPTY'}")
        if not report_inside or not report_path.is_file():
            return
        try:
            report = json.loads(report_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            self.require(False, "ANDROID_CANDIDATE_REPORT_INVALID", str(exc))
            return
        self.require(report.get("status") == "PASS", "ANDROID_CANDIDATE_REPORT_NOT_PASS", "候选报告不是PASS")
        self.require(report.get("owner_test_allowed") is True, "ANDROID_CANDIDATE_REPORT_OWNER_BLOCKED", "候选报告未允许真机验收")
        self.require(str(report.get("commit") or "").lower() == candidate_commit, "ANDROID_CANDIDATE_REPORT_COMMIT_MISMATCH", "候选报告Commit不一致")
        apk_manifest_path = ROOT / "artifacts" / "apk" / self.release / "APK_MANIFEST.yaml"
        if apk_manifest_path.is_file():
            apk_manifest = load_yaml(apk_manifest_path)
            report_sha = str((report.get("apk") or {}).get("sha256") or "").lower()
            delivered_sha = str(apk_manifest.get("sha256") or "").lower()
            if report_sha != delivered_sha:
                bridge_path = report_path.with_name("build-evidence.json")
                try:
                    bridge = json.loads(bridge_path.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError) as exc:
                    self.require(False, "ANDROID_CANDIDATE_REPORT_SHA_MISMATCH", f"候选APK与稳定签名交付APK不同且缺少有效转换证据：{exc}")
                else:
                    bridge_valid = (
                        bridge.get("build_status") == "PASS"
                        and bridge.get("stable_signing") is True
                        and str(bridge.get("release") or "") == self.release
                        and str(bridge.get("commit") or "").lower() == candidate_commit
                        and str(bridge.get("source_candidate_apk_sha256") or "").lower() == report_sha
                        and str(bridge.get("apk_sha256") or "").lower() == delivered_sha
                        and bridge.get("apk_size_bytes") == apk_manifest.get("size_bytes")
                    )
                    self.require(
                        bridge_valid,
                        "ANDROID_CANDIDATE_REPORT_SHA_MISMATCH",
                        "候选APK到稳定签名交付APK的Commit、SHA、大小或PASS转换证据不完整",
                    )

    def repository_file(self, value: Any, code: str, label: str) -> Path | None:
        relative = str(value or "").strip().replace("\\", "/")
        if not relative:
            self.require(False, code, f"{label}路径为空")
            return None
        candidate = (ROOT / relative).resolve()
        try:
            candidate.relative_to(ROOT.resolve())
            inside = True
        except ValueError:
            inside = False
        self.require(inside and candidate.is_file(), code, f"{label}不存在：{relative}")
        return candidate if inside and candidate.is_file() else None

    def json_evidence(self, value: Any, code: str, label: str) -> dict[str, Any]:
        path = self.repository_file(value, code, label)
        if path is None:
            return {}
        try:
            document = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            self.require(False, code, f"{label}不可解析：{exc}")
            return {}
        self.require(isinstance(document, dict), code, f"{label}顶层必须为对象")
        return document if isinstance(document, dict) else {}

    def validate_test_apk_delivery(self, manifest: dict[str, Any], candidate_commit: str | None) -> None:
        delivery = manifest.get("android_delivery") if isinstance(manifest.get("android_delivery"), dict) else {}
        apk_path = ROOT / "artifacts" / "apk" / self.release / "APK_MANIFEST.yaml"
        apk = load_yaml(apk_path) if apk_path.is_file() else {}
        sha = str(apk.get("sha256") or "").lower()
        version_name = apk.get("version_name")
        version_code = apk.get("version_code")
        apk_file = apk.get("apk_file")
        size = apk.get("size_bytes")
        fingerprint = str(apk.get("signing_fingerprint") or "").lower()

        self.require(delivery.get("machine_delivery") == "PASS", "TEST_APK_MACHINE_DELIVERY_NOT_PASS", "TEST_APK机器交付不是PASS")
        self.require(delivery.get("next_release_development") == "ALLOWED", "TEST_APK_NEXT_RELEASE_BLOCKED", "TEST_APK交付未允许继续下一版本")
        self.require(str(delivery.get("owner_physical_test") or "").upper() in {"PENDING", "PASS"}, "TEST_APK_OWNER_STATE_INVALID", "TEST_APK真机状态必须为PENDING或PASS")
        for field, expected in {
            "apk_file": apk_file,
            "version_name": version_name,
            "version_code": version_code,
            "sha256": sha,
            "signing_fingerprint": fingerprint,
        }.items():
            actual = delivery.get(field)
            if field in {"sha256", "signing_fingerprint"}:
                actual = str(actual or "").lower()
            self.require(actual == expected, "TEST_APK_IDENTITY_MISMATCH", f"android_delivery.{field}与APK Manifest不一致")
        if candidate_commit:
            self.require(str(delivery.get("source_commit") or "").lower() == candidate_commit, "TEST_APK_COMMIT_MISMATCH", "TEST_APK source_commit与APK Manifest不一致")

        build = self.json_evidence(delivery.get("build_evidence"), "TEST_APK_BUILD_EVIDENCE_INVALID", "TEST_APK构建证据")
        self.require(build.get("release") == self.release, "TEST_APK_BUILD_RELEASE_MISMATCH", "TEST_APK构建证据Release不一致")
        self.require(str(build.get("commit") or "").lower() == (candidate_commit or ""), "TEST_APK_BUILD_COMMIT_MISMATCH", "TEST_APK构建证据Commit不一致")
        self.require(build.get("build_status") == "PASS", "TEST_APK_BUILD_NOT_PASS", "TEST_APK固定工具链构建不是PASS")
        self.require(set(build.get("checks") or []) >= REQUIRED_TEST_APK_CHECKS, "TEST_APK_BUILD_CHECKS_MISSING", "TEST_APK构建证据缺少正式API、编译、单测、Lint、打包、签名或身份检查")
        self.require(build.get("stable_signing") is True, "TEST_APK_SIGNING_NOT_STABLE", "TEST_APK未使用稳定测试签名")
        self.require(build.get("api_base_url") == "https://api.orbexa.cc", "TEST_APK_API_INVALID", "TEST_APK未绑定正式HTTPS API")
        for field, expected in {
            "version_name": version_name,
            "version_code": version_code,
            "apk_sha256": sha,
            "apk_size_bytes": size,
            "signing_fingerprint": fingerprint,
            "signing_profile_id": delivery.get("signing_profile_id"),
        }.items():
            actual = build.get(field)
            if field in {"apk_sha256", "signing_fingerprint"}:
                actual = str(actual or "").lower()
            self.require(actual == expected, "TEST_APK_BUILD_IDENTITY_MISMATCH", f"TEST_APK构建证据{field}不一致")

        evidence = self.json_evidence(delivery.get("evidence"), "TEST_APK_DELIVERY_EVIDENCE_INVALID", "TEST_APK四方交付证据")
        for field, expected in {
            "release": self.release,
            "commit": candidate_commit,
            "apk_file": apk_file,
            "version_name": version_name,
            "version_code": version_code,
            "sha256": sha,
            "size_bytes": size,
        }.items():
            actual = evidence.get(field)
            if field in {"commit", "sha256"}:
                actual = str(actual or "").lower()
            self.require(actual == expected, "TEST_APK_DELIVERY_IDENTITY_MISMATCH", f"TEST_APK四方交付证据{field}不一致")
        signing = evidence.get("signing") if isinstance(evidence.get("signing"), dict) else {}
        self.require(signing.get("status") == "PASS" and signing.get("stable") is True, "TEST_APK_DELIVERY_SIGNING_NOT_PASS", "TEST_APK四方证据签名不是稳定PASS")
        self.require(signing.get("profile_id") == delivery.get("signing_profile_id"), "TEST_APK_DELIVERY_SIGNING_MISMATCH", "TEST_APK签名Profile不一致")
        self.require(str(signing.get("fingerprint") or "").lower() == fingerprint, "TEST_APK_DELIVERY_SIGNING_MISMATCH", "TEST_APK签名指纹不一致")
        for endpoint in ("local", "desktop", "remote", "https"):
            row = evidence.get(endpoint) if isinstance(evidence.get(endpoint), dict) else {}
            self.require(row.get("status") == "PASS", "TEST_APK_FOUR_WAY_NOT_PASS", f"TEST_APK {endpoint}交付不是PASS")
            if endpoint != "local":
                self.require(str(row.get("sha256") or "").lower() == sha, "TEST_APK_FOUR_WAY_SHA_MISMATCH", f"TEST_APK {endpoint} SHA不一致")
            if endpoint in {"remote", "https"}:
                self.require(row.get("size_bytes") == size, "TEST_APK_FOUR_WAY_SIZE_MISMATCH", f"TEST_APK {endpoint}大小不一致")
        https = evidence.get("https") if isinstance(evidence.get("https"), dict) else {}
        self.require(https.get("http_status") == 200 and https.get("range_status") == 206, "TEST_APK_HTTPS_STATUS_INVALID", "TEST_APK HTTPS 200/Range 206证据不完整")
        self.repository_file(delivery.get("test_guide"), "TEST_APK_GUIDE_MISSING", "TEST_APK桌面测试说明")

    def validate_pointers(self, task_ids: list[str], release_commit: str | None) -> None:
        current = load_yaml(ROOT / "CURRENT_STATUS.yaml")
        next_task = load_yaml(ROOT / "NEXT_TASK.yaml")
        next_release = str(next_task.get("release") or "")
        next_id = str(next_task.get("id") or "")
        current_release = str(current.get("active_release") or "")
        current_task = str(current.get("active_task") or "")
        completed = set(current.get("completed_tasks") or [])
        in_progress = set(current.get("in_progress_tasks") or [])

        if not self.production and current_release == self.release:
            final_task = task_ids[-1] if task_ids else ""
            self.require(current_task == final_task, "MACHINE_CLOSE_TASK_NOT_ACTIVE", f"机器收尾必须位于最终任务：{final_task}")
            self.require(set(task_ids[:-1]) <= completed, "CURRENT_COMPLETED_TASKS_MISSING", "CURRENT_STATUS未记录全部机器收尾前置任务")
            self.require(not (set(task_ids[:-1]) & in_progress), "CURRENT_RELEASE_PREREQUISITE_IN_PROGRESS", "机器收尾前置任务仍为IN_PROGRESS")
            return

        self.require(next_release and next_release != self.release, "NEXT_RELEASE_NOT_ADVANCED", f"NEXT_TASK仍指向 {self.release}")
        self.require(str(next_task.get("status") or "").upper() == "READY", "NEXT_TASK_NOT_READY", "NEXT_TASK必须为READY")
        target_path = ROOT / "releases" / next_release / "TASKS.yaml"
        self.require(target_path.is_file(), "NEXT_TASK_PLAN_MISSING", f"下一Release任务计划不存在：{next_release}")
        if target_path.is_file():
            target = load_yaml(target_path).get("tasks") or []
            first = target[0] if isinstance(target, list) and target else {}
            self.require(first.get("id") == next_id, "NEXT_TASK_NOT_FIRST", f"{next_id} 不是 {next_release} 首任务")
            self.require(str(first.get("status") or "").upper() == "READY", "NEXT_TASK_PLAN_NOT_READY", f"{next_id} 在TASKS中不是READY")

        for field in ["phase", "active_release"]:
            self.require(current.get(field) == next_release, "CURRENT_NEXT_RELEASE_MISMATCH", f"CURRENT_STATUS.{field} 与 NEXT_TASK.release 不一致")
        for field in ["active_task", "next_task"]:
            self.require(current.get(field) == next_id, "CURRENT_NEXT_TASK_MISMATCH", f"CURRENT_STATUS.{field} 与 NEXT_TASK.id 不一致")
        current_status = str(current.get("status") or "").upper()
        self.require(
            current_status in {"READY", "IN_PROGRESS"},
            "CURRENT_STATUS_NOT_CONTINUABLE",
            f"CURRENT_STATUS必须为READY或IN_PROGRESS，实际为{current_status or 'EMPTY'}",
        )
        self.require(set(task_ids) <= completed, "CURRENT_COMPLETED_TASKS_MISSING", "CURRENT_STATUS未记录全部Release任务")
        self.require(not (set(task_ids) & in_progress), "CURRENT_RELEASE_STILL_IN_PROGRESS", "已关闭Release仍有IN_PROGRESS任务")
        continuity = current.get("continuity") if isinstance(current.get("continuity"), dict) else {}
        active_session_allowed = current_release != self.release and current_task not in set(task_ids)
        self.require(
            not continuity.get("active_session_id") or active_session_allowed,
            "CURRENT_SESSION_STILL_ACTIVE",
            "被关闭Release仍有ACTIVE Session",
        )
        if release_commit:
            self.require(
                str(current.get("last_green_commit") or "").lower() == release_commit,
                "CURRENT_GREEN_COMMIT_MISMATCH", "CURRENT_STATUS.last_green_commit与Release Commit不一致",
            )

    def validate_ui_visual_acceptance(self) -> int:
        visual_errors, page_count = validate_ui_visual_historical(ROOT, self.release)
        self.errors.extend(visual_errors)
        return page_count

    def validate_governance_audit(self, manifest: dict[str, Any], candidate_commit: str) -> None:
        number = release_number(self.release)
        if number is None or number < GOVERNANCE_AUDIT_EFFECTIVE_RELEASE:
            return
        audit = manifest.get("governance_audit")
        if not isinstance(audit, dict):
            self.errors.append(("GOVERNANCE_AUDIT_MISSING", "R12起机器关闭必须包含六项全局漂移审计"))
            return
        self.require(audit.get("status") == "PASS", "GOVERNANCE_AUDIT_NOT_PASS", "全局漂移审计状态必须为PASS")
        self.require(
            str(audit.get("source_commit") or "").lower() == candidate_commit.lower(),
            "GOVERNANCE_AUDIT_COMMIT_MISMATCH",
            "全局漂移审计必须绑定最终候选源码Commit",
        )
        checks = audit.get("checks") if isinstance(audit.get("checks"), dict) else {}
        self.require(set(checks) == GOVERNANCE_AUDIT_CHECKS, "GOVERNANCE_AUDIT_CHECKS", "全局漂移审计必须精确覆盖六项事实")
        for key in sorted(GOVERNANCE_AUDIT_CHECKS):
            self.require(checks.get(key) == "PASS", "GOVERNANCE_AUDIT_CHECK_NOT_PASS", f"全局漂移审计未通过：{key}")
        evidence_value = str(audit.get("evidence") or "").strip()
        evidence = ROOT / evidence_value if evidence_value else None
        self.require(bool(evidence_value), "GOVERNANCE_AUDIT_EVIDENCE_MISSING", "全局漂移审计缺少仓库证据")
        self.require(bool(evidence and evidence.is_file()), "GOVERNANCE_AUDIT_EVIDENCE_MISSING", evidence_value or "EMPTY")
        expected_sha = str(audit.get("evidence_sha256") or "").lower()
        self.require(bool(re.fullmatch(r"[0-9a-f]{64}", expected_sha)), "GOVERNANCE_AUDIT_SHA_INVALID", "全局漂移审计证据SHA-256非法")
        if evidence and evidence.is_file() and re.fullmatch(r"[0-9a-f]{64}", expected_sha):
            actual_sha = hashlib.sha256(evidence.read_bytes()).hexdigest()
            self.require(actual_sha == expected_sha, "GOVERNANCE_AUDIT_SHA_MISMATCH", evidence_value)

    def run(self) -> int:
        visual_page_count = 0
        try:
            _tasks, task_ids = self.validate_tasks()
            acceptance = self.validate_acceptance()
            manifest, release_commit = self.validate_manifest()
            candidate_commit = self.validate_apk(manifest)
            self.validate_android_automation(manifest, candidate_commit)
            visual_page_count = self.validate_ui_visual_acceptance()
            self.validate_governance_audit(manifest, candidate_commit)
            self.validate_pointers(task_ids, release_commit)
        except (OSError, ValueError, yaml.YAMLError, csv.Error) as exc:
            self.errors.append(("CLOSE_GATE_READ_ERROR", str(exc)))
            acceptance = []
            manifest = {}
        if self.errors:
            print(f"RELEASE_{self.stage.upper()}_CLOSE_GATE_FAILED", self.release, len(self.errors))
            for code, message in self.errors:
                print(code, message)
            return 1
        print(
            f"RELEASE_{self.stage.upper()}_CLOSE_GATE_OK", self.release,
            f"tasks={len(task_ids)}", f"acceptance={len(acceptance)}",
            f"operations={len(self.manifest_operations(manifest))}",
            f"visual_pages={visual_page_count}",
        )
        return 0


def main() -> int:
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--release", help="只校验指定Release")
    parser.add_argument(
        "--close-gate", action="store_true",
        help="已废弃的歧义参数；必须明确选择机器收尾或生产验收",
    )
    parser.add_argument("--machine-close-gate", action="store_true", help="启用大版本机器收尾门禁；允许项目所有者真机结果PENDING")
    parser.add_argument("--production-close-gate", action="store_true", help="启用正式生产验收终态门禁；要求项目所有者真机PASS和Release Tag")
    args = parser.parse_args()
    if args.close_gate:
        print("AMBIGUOUS_CLOSE_GATE --close-gate已废弃；请明确使用--machine-close-gate或--production-close-gate")
        return 2
    if args.machine_close_gate and args.production_close_gate:
        parser.error("机器收尾与生产验收门禁不能同时执行")
    if args.machine_close_gate or args.production_close_gate:
        if not args.release:
            parser.error("关闭门禁必须同时提供 --release")
        if regular_check(args.release) != 0:
            return 1
        stage = "production" if args.production_close_gate else "machine"
        return CloseGate(args.release, stage).run()
    return regular_check(args.release)


if __name__ == "__main__":
    raise SystemExit(main())
