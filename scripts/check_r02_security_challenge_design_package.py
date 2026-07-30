#!/usr/bin/env python3
"""Read-only acceptance gate for the R02 security challenge design return package."""
from __future__ import annotations

from argparse import ArgumentParser
from contextlib import contextmanager
from hashlib import sha256
from pathlib import Path, PurePosixPath
from typing import Iterator
import json
import re
import struct
import tempfile
import zipfile


EXPECTED_SOURCE_COMMIT = "18e2db65b65afeb1e69d0c382ab53963fc97adec"
EXPECTED_EFFECT = (
    "design/effect-previews/B01-CAPTCHA/"
    "HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png"
)
REQUIRED_EDITABLE = (
    "CHATGPT_WEB_CHANGE_SUMMARY.md",
    "RETURN_MANIFEST.json",
    "docs/01-authentication/登录注册与安全验证详细规格_V1.2.2.md",
    "docs/02-ui/12批UI参考图绑定索引_V1.2.2.md",
    "docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md",
    "docs/02-ui/页面施工规格总索引_V1.2.2.md",
    "docs/02-ui/页面模板字段状态动作唯一事实源_V1.2.2.md",
    "docs/02-ui/page-specs/android/SCR-AUTH-001_密码登录.md",
    "docs/02-ui/page-specs/android/SCR-AUTH-002_短信验证码登录.md",
    "docs/02-ui/page-specs/android/SCR-AUTH-003_注册账号.md",
    "docs/02-ui/page-specs/android/SCR-AUTH-004_忘记密码.md",
    EXPECTED_EFFECT,
)
MANIFEST_CANDIDATES = (
    "design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json",
    "design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.template.json",
)
PLACEHOLDER_MARKERS = (
    "TO_BE_COMPLETED_BY_CHATGPT_WEB",
    "待 ChatGPT 网页端补充",
    "待设计",
    "- 待填写",
    "替换本模板内容",
)
FORBIDDEN_SUFFIXES = {
    ".apk", ".aab", ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".env"
}
MAX_ARCHIVE_BYTES = 100 * 1024 * 1024


class PackageError(ValueError):
    pass


def digest(path: Path) -> str:
    value = sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def load_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise PackageError(f"JSON不可读：{path.name}: {exc}") from exc
    if not isinstance(value, dict):
        raise PackageError(f"JSON根节点必须是对象：{path.name}")
    return value


def safe_archive_name(name: str) -> Path:
    normalized = name.replace("\\", "/")
    pure = PurePosixPath(normalized)
    if (
        not normalized
        or normalized.startswith("/")
        or re.match(r"^[A-Za-z]:", normalized)
        or any(part in {"", ".", ".."} for part in pure.parts)
    ):
        raise PackageError(f"ZIP含不安全路径：{name}")
    return Path(*pure.parts)


@contextmanager
def materialized_package(source: Path) -> Iterator[Path]:
    if source.is_dir():
        yield source.resolve()
        return
    if not source.is_file() or source.suffix.lower() != ".zip":
        raise PackageError("输入必须是设计回传目录或ZIP文件")
    with tempfile.TemporaryDirectory(prefix="hhy-r02-design-return-") as temp:
        target = Path(temp)
        with zipfile.ZipFile(source) as archive:
            total = 0
            for info in archive.infolist():
                relative = safe_archive_name(info.filename)
                total += info.file_size
                if total > MAX_ARCHIVE_BYTES:
                    raise PackageError("ZIP解压后超过100 MiB限制")
                mode = (info.external_attr >> 16) & 0o170000
                if mode == 0o120000:
                    raise PackageError(f"ZIP不允许符号链接：{info.filename}")
                output = (target / relative).resolve()
                if target.resolve() not in output.parents and output != target.resolve():
                    raise PackageError(f"ZIP路径越界：{info.filename}")
                if info.is_dir():
                    output.mkdir(parents=True, exist_ok=True)
                else:
                    output.parent.mkdir(parents=True, exist_ok=True)
                    with archive.open(info) as src, output.open("wb") as dst:
                        while block := src.read(1024 * 1024):
                            dst.write(block)
        yield target


def locate_root(materialized: Path) -> Path:
    candidates = []
    if (materialized / "EDITABLE_OVERLAY").is_dir():
        candidates.append(materialized)
    candidates.extend(
        path.parent for path in materialized.glob("*/EDITABLE_OVERLAY") if path.is_dir()
    )
    unique = list(dict.fromkeys(path.resolve() for path in candidates))
    if len(unique) != 1:
        raise PackageError(f"必须且只能有一个包含EDITABLE_OVERLAY的包根目录，实际{len(unique)}个")
    root = unique[0]
    if not (root / "SHA256SUMS.txt").is_file():
        raise PackageError("包根目录缺少SHA256SUMS.txt")
    return root


def validate_forbidden_files(root: Path) -> None:
    forbidden = []
    for path in root.rglob("*"):
        if path.is_file() and (path.suffix.lower() in FORBIDDEN_SUFFIXES or path.name.lower() == ".env"):
            forbidden.append(path.relative_to(root).as_posix())
    if forbidden:
        raise PackageError("包内含禁止文件：" + ", ".join(forbidden))


def validate_reference_hashes(root: Path) -> int:
    lines = (root / "SHA256SUMS.txt").read_text(encoding="utf-8-sig").splitlines()
    records: list[tuple[str, str]] = []
    for line in lines:
        match = re.fullmatch(r"([0-9a-fA-F]{64})\s{2}(.+)", line.strip())
        if match and match.group(2).replace("\\", "/").startswith("REFERENCE_ONLY/"):
            records.append((match.group(1).lower(), match.group(2).replace("\\", "/")))
    if not records:
        raise PackageError("SHA256SUMS.txt没有REFERENCE_ONLY基线记录")
    failures = []
    for expected, relative in records:
        path = root / Path(*PurePosixPath(relative).parts)
        if not path.is_file():
            failures.append(f"缺失:{relative}")
        elif digest(path) != expected:
            failures.append(f"被修改:{relative}")
    if failures:
        raise PackageError("REFERENCE_ONLY完整性失败：" + ", ".join(failures))
    return len(records)


def png_dimensions(path: Path) -> tuple[int, int]:
    header = path.read_bytes()[:24]
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise PackageError("B01效果图不是有效PNG/IHDR文件")
    width, height = struct.unpack(">II", header[16:24])
    if width <= 0 or height <= 0:
        raise PackageError("B01效果图尺寸无效")
    return width, height


def optional_hash_matches(value: object, actual: str, field: str) -> None:
    if value is None:
        return
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", value):
        raise PackageError(f"{field}必须为null或64位SHA-256")
    if value.lower() != actual:
        raise PackageError(f"{field}与文件SHA-256不一致")


def validate_editable(root: Path, expected_source_commit: str) -> dict:
    editable = root / "EDITABLE_OVERLAY"
    missing = [relative for relative in REQUIRED_EDITABLE if not (editable / relative).is_file()]
    manifest_paths = [editable / relative for relative in MANIFEST_CANDIDATES if (editable / relative).is_file()]
    if len(manifest_paths) != 1:
        missing.append("B01-CAPTCHA Manifest（最终文件或已完成模板必须且只能有一个）")
    if missing:
        raise PackageError("EDITABLE_OVERLAY缺少必需文件：" + ", ".join(missing))

    placeholder_hits = []
    for path in editable.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in {".md", ".json", ".yaml", ".yml", ".txt", ".csv"}:
            continue
        text = path.read_text(encoding="utf-8-sig")
        for marker in PLACEHOLDER_MARKERS:
            if marker in text:
                placeholder_hits.append(f"{path.relative_to(editable).as_posix()}:{marker}")
    if placeholder_hits:
        raise PackageError("可编辑区仍含模板占位：" + ", ".join(placeholder_hits[:20]))

    return_manifest = load_json(editable / "RETURN_MANIFEST.json")
    if return_manifest.get("package") != "HHY_R02_SECURITY_CHALLENGE_UI_DESIGN":
        raise PackageError("RETURN_MANIFEST.package不匹配")
    if return_manifest.get("source_commit") != expected_source_commit:
        raise PackageError("RETURN_MANIFEST.source_commit与接入基线不匹配")
    if return_manifest.get("status") in {None, "", "TO_BE_COMPLETED_BY_CHATGPT_WEB"}:
        raise PackageError("RETURN_MANIFEST.status尚未完成")
    changed = return_manifest.get("created_or_modified_files")
    def declared_change_path(item: object) -> str | None:
        if isinstance(item, str) and item.strip():
            return item.strip()
        if isinstance(item, dict):
            value = item.get("path")
            if isinstance(value, str) and value.strip():
                return value.strip()
        return None

    declared_paths = [declared_change_path(item) for item in changed] if isinstance(changed, list) else []
    if not declared_paths or any(path is None for path in declared_paths):
        raise PackageError("RETURN_MANIFEST.created_or_modified_files必须列出实际变化")

    effect_path = editable / EXPECTED_EFFECT
    effect_sha = digest(effect_path)
    width, height = png_dimensions(effect_path)
    effect_record = return_manifest.get("effect_preview")
    accepted_effect_paths = {EXPECTED_EFFECT, f"EDITABLE_OVERLAY/{EXPECTED_EFFECT}"}
    if not isinstance(effect_record, dict) or effect_record.get("file") not in accepted_effect_paths:
        raise PackageError("RETURN_MANIFEST.effect_preview.file不匹配")
    optional_hash_matches(effect_record.get("sha256"), effect_sha, "RETURN_MANIFEST.effect_preview.sha256")

    effect_manifest = load_json(manifest_paths[0])
    if effect_manifest.get("batch_id") != "B01-CAPTCHA":
        raise PackageError("B01 Manifest.batch_id不匹配")
    if effect_manifest.get("file") != Path(EXPECTED_EFFECT).name:
        raise PackageError("B01 Manifest.file不匹配")
    panels = effect_manifest.get("panels")
    panel_ids = {row.get("panel") for row in panels if isinstance(row, dict)} if isinstance(panels, list) else set()
    if panel_ids != {f"P{number:02d}" for number in range(1, 9)}:
        raise PackageError("B01 Manifest必须完整列出P01—P08")
    parameters = effect_manifest.get("implementation_parameters")
    required_parameters = {"typography_sp", "colors", "motion_ms", "accessibility"}
    if not isinstance(parameters, dict) or any(parameters.get(key) in (None, "", [], {}) for key in required_parameters):
        raise PackageError("B01 Manifest的精确实现参数不完整")
    if not any(parameters.get(key) not in (None, "", [], {}) for key in ("dialog", "dimensions_dp")):
        raise PackageError("B01 Manifest缺少弹层尺寸参数")
    optional_hash_matches(effect_manifest.get("sha256"), effect_sha, "B01 Manifest.sha256")
    return {
        "effect_preview": EXPECTED_EFFECT,
        "effect_sha256": effect_sha,
        "effect_dimensions_px": {"width": width, "height": height},
        "effect_manifest": manifest_paths[0].relative_to(editable).as_posix(),
        "declared_changes": len(changed),
    }


def validate(source: Path, expected_source_commit: str = EXPECTED_SOURCE_COMMIT) -> dict:
    with materialized_package(source) as materialized:
        root = locate_root(materialized)
        validate_forbidden_files(root)
        reference_files = validate_reference_hashes(root)
        details = validate_editable(root, expected_source_commit)
        return {
            "status": "PASS",
            "package": str(source.resolve()),
            "source_commit": expected_source_commit,
            "reference_files_verified": reference_files,
            **details,
            "read_only": True,
        }


def main() -> int:
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path, help="网页端回传目录或ZIP路径")
    parser.add_argument("--expected-source-commit", default=EXPECTED_SOURCE_COMMIT)
    args = parser.parse_args()
    try:
        result = validate(args.package, args.expected_source_commit)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (PackageError, OSError, zipfile.BadZipFile) as exc:
        print(json.dumps({"status": "FAIL", "package": str(args.package), "error": str(exc)}, ensure_ascii=False, indent=2))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
