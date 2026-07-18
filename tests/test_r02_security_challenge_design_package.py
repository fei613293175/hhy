#!/usr/bin/env python3
"""Regression coverage for the R02 external design return package gate."""
from __future__ import annotations

from base64 import b64decode
from hashlib import sha256
from pathlib import Path
import json
import shutil
import sys
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from check_r02_security_challenge_design_package import (  # noqa: E402
    EXPECTED_EFFECT,
    EXPECTED_SOURCE_COMMIT,
    PackageError,
    REQUIRED_EDITABLE,
    validate,
)


PNG = b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+XxP4WQAAAABJRU5ErkJggg=="
)


class DesignReturnPackageTest(unittest.TestCase):
    def make_package(self, parent: Path) -> Path:
        root = parent / "return-package"
        editable = root / "EDITABLE_OVERLAY"
        for relative in REQUIRED_EDITABLE:
            path = editable / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            if relative == EXPECTED_EFFECT:
                path.write_bytes(PNG)
            elif relative == "RETURN_MANIFEST.json":
                path.write_text(
                    json.dumps(
                        {
                            "package": "HHY_R02_SECURITY_CHALLENGE_UI_DESIGN",
                            "source_commit": EXPECTED_SOURCE_COMMIT,
                            "status": "DESIGN_COMPLETED",
                            "created_or_modified_files": ["docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md"],
                            "effect_preview": {"file": EXPECTED_EFFECT, "sha256": None},
                            "unresolved_owner_decisions": [],
                        },
                        ensure_ascii=False,
                    ),
                    encoding="utf-8",
                )
            else:
                path.write_text("已完成的设计内容与精确参数。\n", encoding="utf-8")

        effect_manifest = editable / "design/effect-previews/B01-CAPTCHA/HHY_B01_CAPTCHA_MANIFEST.json"
        effect_manifest.write_text(
            json.dumps(
                {
                    "batch_id": "B01-CAPTCHA",
                    "file": Path(EXPECTED_EFFECT).name,
                    "sha256": sha256(PNG).hexdigest(),
                    "panels": [{"panel": f"P{number:02d}", "name": "状态"} for number in range(1, 9)],
                    "implementation_parameters": {
                        "canvas": {"width": 1080},
                        "dimensions_dp": {"width": 320},
                        "typography_sp": {"body": 16},
                        "colors": {"primary": "#1677FF"},
                        "motion_ms": {"enter": 180},
                        "accessibility": {"touch_target_dp": 48},
                    },
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        reference = root / "REFERENCE_ONLY/reference.txt"
        reference.parent.mkdir(parents=True)
        reference.write_text("immutable\n", encoding="utf-8")
        root.joinpath("SHA256SUMS.txt").write_text(
            f"{sha256(reference.read_bytes()).hexdigest()}  REFERENCE_ONLY/reference.txt\n",
            encoding="utf-8",
        )
        return root

    def test_valid_directory_and_zip_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            parent = Path(temp)
            root = self.make_package(parent)
            self.assertEqual(validate(root)["status"], "PASS")
            archive = Path(shutil.make_archive(str(parent / "return"), "zip", root.parent, root.name))
            result = validate(archive)
            self.assertEqual(result["status"], "PASS")
            self.assertEqual(result["reference_files_verified"], 1)
            self.assertEqual(result["effect_dimensions_px"], {"width": 1, "height": 1})

    def test_missing_effect_or_placeholder_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.make_package(Path(temp))
            (root / "EDITABLE_OVERLAY" / EXPECTED_EFFECT).unlink()
            with self.assertRaisesRegex(PackageError, "缺少必需文件"):
                validate(root)
            root = self.make_package(Path(temp) / "second")
            summary = root / "EDITABLE_OVERLAY/CHATGPT_WEB_CHANGE_SUMMARY.md"
            summary.write_text("- 待填写\n", encoding="utf-8")
            with self.assertRaisesRegex(PackageError, "模板占位"):
                validate(root)

    def test_changed_reference_and_mismatched_effect_hash_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.make_package(Path(temp))
            (root / "REFERENCE_ONLY/reference.txt").write_text("changed\n", encoding="utf-8")
            with self.assertRaisesRegex(PackageError, "REFERENCE_ONLY完整性失败"):
                validate(root)
            root = self.make_package(Path(temp) / "second")
            manifest = root / "EDITABLE_OVERLAY/RETURN_MANIFEST.json"
            payload = json.loads(manifest.read_text(encoding="utf-8"))
            payload["effect_preview"]["sha256"] = "0" * 64
            manifest.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
            with self.assertRaisesRegex(PackageError, "SHA-256不一致"):
                validate(root)

    def test_zip_path_traversal_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "unsafe.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("../escape.txt", "no")
            with self.assertRaisesRegex(PackageError, "不安全路径"):
                validate(archive)

    def test_forbidden_binary_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = self.make_package(Path(temp))
            (root / "unexpected.apk").write_bytes(b"not-an-apk")
            with self.assertRaisesRegex(PackageError, "禁止文件"):
                validate(root)


if __name__ == "__main__":
    unittest.main()
