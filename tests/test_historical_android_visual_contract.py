"""Regression guardrails for historical Android pages remediated during R08 close."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


class HistoricalAndroidVisualContractTests(unittest.TestCase):
    def test_change_password_preserves_real_action_and_b12_security_hierarchy(self) -> None:
        source = read("apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt")

        for marker in (
            'Text("当前密码"',
            'Text("新密码"',
            'Text("确认新密码"',
            "PasswordStrengthIndicator(strength)",
            'Text("密码安全建议"',
            "api.changePassword(accessToken, currentPassword, newPassword)",
        ):
            self.assertIn(marker, source)

    def test_account_cancellation_has_account_risk_and_verification_groups(self) -> None:
        source = read("apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt")

        for marker in (
            'Text("当前账号"',
            'Text("注销前请确认"',
            'Text("身份验证"',
            'Text("完整手机号"',
            "api.requestCancellation(accessToken, reason.trim(), smsCode, user.version)",
        ):
            self.assertIn(marker, source)

    def test_about_page_sanitizes_build_metadata_and_internal_release_notes(self) -> None:
        source = read("apps/android/app/src/main/java/cc/orbexa/hhy/AboutScreen.kt")

        self.assertIn("publicVersionName(BuildConfig.VERSION_NAME)", source)
        self.assertIn("publicReleaseNotes(policy?.releaseNotes, updateAvailable)", source)
        self.assertIn('listOf("debug", "p00", "测试包", "验收", "内测", "候选包", "staging")', source)
        self.assertNotIn('Text("当前版本 ${BuildConfig.VERSION_NAME}"', source)
        self.assertNotIn('Text(policy?.releaseNotes ?: "暂无更新说明"', source)


if __name__ == "__main__":
    unittest.main()
