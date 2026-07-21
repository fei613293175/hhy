#!/usr/bin/env python3
"""Validate release artifacts, with an opt-in terminal release-close gate."""
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

from check_ui_visual_acceptance import validate_release as validate_ui_visual_release


ROOT = Path(__file__).resolve().parents[1]
TERMINAL_RELEASE_STATUSES = {"DONE", "COMPLETED", "CLOSED", "RELEASED"}
TERMINAL_TEST_STATUSES = {"PASS", "PASSED", "GREEN", "SUCCESS"}
COMMIT_PATTERN = re.compile(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")
TAG_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._/-]*")
PLACEHOLDER_PATTERN = re.compile(
    r"PENDING|PLACEHOLDER|\bTODO\b|\bTBD\b|NOT_INITIALIZED|NOT_RUN|UNKNOWN|<[^>]+>",
    re.IGNORECASE,
)


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
    def __init__(self, release: str) -> None:
        self.release = release
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
        unfinished = [
            str(row.get("id") or "") for row in tasks
            if not isinstance(row, dict) or str(row.get("status") or "").upper() != "DONE"
        ]
        self.require(not unfinished, "TASKS_NOT_DONE", f"未完成任务：{unfinished}")
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
        self.require(status in TERMINAL_RELEASE_STATUSES, "MANIFEST_NOT_TERMINAL", f"Manifest状态不是终态：{status or 'EMPTY'}")

        operations = self.manifest_operations(manifest)
        self.require(bool(operations), "MANIFEST_OPERATION_COUNT", "必须声明至少1个operationId")
        self.require(len(set(operations)) == len(operations), "MANIFEST_OPERATION_DUPLICATE", "operationId存在重复")
        invalid = [value for value in operations if not self.complete_string(value)]
        self.require(not invalid, "MANIFEST_OPERATION_PLACEHOLDER", f"operationId含占位值：{invalid}")
        unknown = sorted(set(operations) - self.known_operation_ids())
        self.require(not unknown, "MANIFEST_OPERATION_UNKNOWN", f"OpenAPI中不存在operationId：{unknown}")

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
        self.require(test_status in TERMINAL_TEST_STATUSES, "APK_TEST_NOT_PASS", f"APK测试状态非法：{test_status or 'EMPTY'}")
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
        self.require(str(automation.get("status") or "").upper() == "PASS", "ANDROID_AUTOMATION_NOT_PASS", "Android自动门禁不是PASS")
        self.require(automation.get("owner_test_allowed") is True, "ANDROID_OWNER_TEST_NOT_ALLOWED", "自动门禁尚未允许项目所有者真机测试")
        self.require(str(automation.get("owner_physical_test") or "").upper() == "PASS", "ANDROID_OWNER_TEST_NOT_PASS", "项目所有者最终候选真机验收不是PASS")
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
            self.require(
                str((report.get("apk") or {}).get("sha256") or "").lower() == str(apk_manifest.get("sha256") or "").lower(),
                "ANDROID_CANDIDATE_REPORT_SHA_MISMATCH", "候选报告APK SHA256与交付清单不一致",
            )

    def validate_pointers(self, task_ids: list[str], release_commit: str | None) -> None:
        current = load_yaml(ROOT / "CURRENT_STATUS.yaml")
        next_task = load_yaml(ROOT / "NEXT_TASK.yaml")
        next_release = str(next_task.get("release") or "")
        next_id = str(next_task.get("id") or "")
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
        self.require(str(current.get("status") or "").upper() == "READY", "CURRENT_STATUS_NOT_READY", "CURRENT_STATUS必须为READY")
        completed = set(current.get("completed_tasks") or [])
        self.require(set(task_ids) <= completed, "CURRENT_COMPLETED_TASKS_MISSING", "CURRENT_STATUS未记录全部Release任务")
        in_progress = set(current.get("in_progress_tasks") or [])
        self.require(not (set(task_ids) & in_progress), "CURRENT_RELEASE_STILL_IN_PROGRESS", "已关闭Release仍有IN_PROGRESS任务")
        continuity = current.get("continuity") if isinstance(current.get("continuity"), dict) else {}
        self.require(not continuity.get("active_session_id"), "CURRENT_SESSION_STILL_ACTIVE", "Release关闭后仍有ACTIVE Session")
        if release_commit:
            self.require(
                str(current.get("last_green_commit") or "").lower() == release_commit,
                "CURRENT_GREEN_COMMIT_MISMATCH", "CURRENT_STATUS.last_green_commit与Release Commit不一致",
            )

    def validate_ui_visual_acceptance(self) -> int:
        visual_errors, page_count = validate_ui_visual_release(
            ROOT, self.release, require_pass=True
        )
        self.errors.extend(visual_errors)
        return page_count

    def run(self) -> int:
        visual_page_count = 0
        try:
            _tasks, task_ids = self.validate_tasks()
            acceptance = self.validate_acceptance()
            manifest, release_commit = self.validate_manifest()
            candidate_commit = self.validate_apk(manifest)
            self.validate_android_automation(manifest, candidate_commit)
            visual_page_count = self.validate_ui_visual_acceptance()
            self.validate_pointers(task_ids, release_commit)
        except (OSError, ValueError, yaml.YAMLError, csv.Error) as exc:
            self.errors.append(("CLOSE_GATE_READ_ERROR", str(exc)))
            acceptance = []
            manifest = {}
        if self.errors:
            print("RELEASE_CLOSE_GATE_FAILED", self.release, len(self.errors))
            for code, message in self.errors:
                print(code, message)
            return 1
        print(
            "RELEASE_CLOSE_GATE_OK", self.release,
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
        help="启用Release终态关闭门禁；日常开发检查不应启用",
    )
    args = parser.parse_args()
    if args.close_gate:
        if not args.release:
            parser.error("--close-gate 必须同时提供 --release")
        if regular_check(args.release) != 0:
            return 1
        return CloseGate(args.release).run()
    return regular_check(args.release)


if __name__ == "__main__":
    raise SystemExit(main())
