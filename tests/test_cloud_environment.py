from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from verify_cloud_environment import check_cloud_environment, load_policy  # noqa: E402


class CloudEnvironmentTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = load_policy()

    def test_user_declared_disconnect_blocks_without_ssh(self) -> None:
        with patch("verify_cloud_environment.run_ssh") as ssh:
            result = check_cloud_environment(ssh_executable="ssh", check_android=True, declared_disconnected=True, policy=self.policy)
        self.assertEqual("BLOCKED", result["status"])
        self.assertEqual("USER_DECLARED_CLOUD_DISCONNECTED", result["reason"])
        ssh.assert_not_called()

    @patch("verify_cloud_environment.run_ssh")
    def test_existing_cloud_image_and_cache_pass_read_only_preflight(self, ssh) -> None:
        image_id = self.policy["cloud_environment"]["android"]["image_id"]
        probe = "\n".join(
            [
                "hostname=ebs-178264",
                f"android_image_id={image_id}",
                "gradle_cache=present",
                "android_platform_36=present",
                "swap_mb=8192",
                "available_mb=4096",
                "load_1m=0.25",
                "android_build_slot=available",
            ]
        )
        ssh.return_value = subprocess.CompletedProcess([], 0, probe + "\n", "")
        result = check_cloud_environment(ssh_executable="ssh", check_android=True, declared_disconnected=False, policy=self.policy)
        self.assertEqual("PASS", result["status"])
        self.assertTrue(result["development_allowed"])
        self.assertEqual(1, ssh.call_count)
        commands = [call.args[2] for call in ssh.call_args_list]
        self.assertTrue(
            all(
                "docker build" not in command and "sdkmanager" not in command and "bootstrap" not in command
                for command in commands
            )
        )

    @patch("verify_cloud_environment.run_ssh")
    def test_wrong_existing_image_blocks_and_never_rebuilds(self, ssh) -> None:
        ssh.return_value = subprocess.CompletedProcess([], 1, "hostname=ebs-178264\n", "image id mismatch")
        result = check_cloud_environment(ssh_executable="ssh", check_android=True, declared_disconnected=False, policy=self.policy)
        self.assertEqual("BLOCKED", result["status"])
        self.assertEqual("CLOUD_PREFLIGHT_FAILED", result["reason"])
        self.assertEqual(1, ssh.call_count)
        commands = [call.args[2] for call in ssh.call_args_list]
        self.assertFalse(any("docker build" in command or "sdkmanager" in command for command in commands))


if __name__ == "__main__":
    unittest.main()
