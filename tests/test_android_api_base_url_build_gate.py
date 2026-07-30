#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
APP_BUILD = ROOT / "apps" / "android" / "app" / "build.gradle.kts"


class AndroidApiBaseUrlBuildGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = APP_BUILD.read_text(encoding="utf-8")

    def test_packaging_tasks_require_the_api_gate(self) -> None:
        self.assertIn('name == "build"', self.source)
        for task_prefix in ("package", "assemble", "bundle"):
            with self.subTest(task_prefix=task_prefix):
                self.assertIn(f'name.startsWith("{task_prefix}")', self.source)
        self.assertIn("dependsOn(verifyApiBaseUrl)", self.source)

    def test_gate_rejects_placeholder_or_unsafe_endpoints(self) -> None:
        required_policy_checks = (
            'uri?.scheme == "https"',
            "!uri.host.isNullOrBlank()",
            '!uri.host.endsWith(".invalid")',
            "uri.userInfo == null",
            "uri.fragment == null",
        )
        for policy_check in required_policy_checks:
            with self.subTest(policy_check=policy_check):
                self.assertIn(policy_check, self.source)

    def test_apk_build_config_uses_the_validated_environment_value(self) -> None:
        self.assertIn('environmentVariable("HHY_API_BASE_URL")', self.source)
        self.assertIn(
            'buildConfigField("String", "API_BASE_URL", "\\\"${apiBaseUrl.get()}\\\"")',
            self.source,
        )


if __name__ == "__main__":
    unittest.main()
