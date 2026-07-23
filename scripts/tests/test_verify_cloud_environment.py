from __future__ import annotations

import subprocess
import unittest
from unittest.mock import patch

from scripts.verify_cloud_environment import check_cloud_environment


POLICY = {
    "cloud_environment": {
        "default_assumption": "CONNECTED",
        "ssh_alias": "obx-test",
        "android": {
            "image": "hhy-android-toolchain:test",
            "image_id": "sha256:expected",
            "gradle_cache": "hhy-gradle-cache",
        },
    }
}


class VerifyCloudEnvironmentTest(unittest.TestCase):
    @patch("scripts.verify_cloud_environment.run_ssh")
    def test_android_preflight_uses_one_connection_and_reports_resources(self, run_ssh) -> None:
        run_ssh.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="\n".join(
                [
                    "hostname=obx",
                    "android_image_id=sha256:expected",
                    "gradle_cache=present",
                    "android_platform_36=present",
                    "swap_mb=8191",
                    "available_mb=4096",
                    "load_1m=1.25",
                    "android_build_slot=available",
                ]
            ),
            stderr="",
        )

        result = check_cloud_environment(
            ssh_executable="ssh",
            check_android=True,
            declared_disconnected=False,
            policy=POLICY,
        )

        self.assertEqual("PASS", result["status"])
        self.assertEqual(1, run_ssh.call_count)
        self.assertIn("flock -n /var/lock/hhy-android-build.lock", run_ssh.call_args.args[2])
        self.assertIn("hhy-android-sdk-platform-36", run_ssh.call_args.args[2])
        self.assertIn("android.jar", run_ssh.call_args.args[2])

    @patch("scripts.verify_cloud_environment.run_ssh")
    def test_android_preflight_blocks_without_resource_reserve(self, run_ssh) -> None:
        run_ssh.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="\n".join(
                [
                    "hostname=obx",
                    "android_image_id=sha256:expected",
                    "gradle_cache=present",
                    "android_platform_36=present",
                    "swap_mb=0",
                    "available_mb=512",
                    "load_1m=18.00",
                    "android_build_slot=busy",
                ]
            ),
            stderr="",
        )

        result = check_cloud_environment(
            ssh_executable="ssh",
            check_android=True,
            declared_disconnected=False,
            policy=POLICY,
        )

        self.assertEqual("BLOCKED", result["status"])
        failed = {row["name"] for row in result["checks"] if not row["ok"]}
        self.assertEqual({"swap_capacity", "available_memory"}, failed)

    @patch("scripts.verify_cloud_environment.run_ssh")
    def test_android_preflight_blocks_without_platform_cache(self, run_ssh) -> None:
        run_ssh.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="\n".join(
                [
                    "hostname=obx",
                    "android_image_id=sha256:expected",
                    "gradle_cache=present",
                    "swap_mb=8191",
                    "available_mb=4096",
                    "load_1m=1.25",
                    "android_build_slot=available",
                ]
            ),
            stderr="",
        )

        result = check_cloud_environment(
            ssh_executable="ssh",
            check_android=True,
            declared_disconnected=False,
            policy=POLICY,
        )

        self.assertEqual("BLOCKED", result["status"])
        failed = {row["name"] for row in result["checks"] if not row["ok"]}
        self.assertEqual({"android_platform_36"}, failed)


if __name__ == "__main__":
    unittest.main()
