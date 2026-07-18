"""Static contract guardrails for the currently implemented R02 auth slice.

These tests intentionally distinguish the delivered refresh/startup foundation from
the remaining password, SMS and invitation-registration work.  They are not a
replacement for backend integration tests or an Android build.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class R02AuthSliceContractTests(unittest.TestCase):
    def test_frozen_r02_auth_contract_includes_login_registration_and_refresh(self) -> None:
        contract = read("contracts/openapi.yaml")
        required_paths = {
            "/api/v1/auth/security-challenges",
            "/api/v1/auth/password/login",
            "/api/v1/auth/sms/send",
            "/api/v1/auth/sms/login",
            "/api/v1/auth/invite-codes/validate",
            "/api/v1/auth/registration-config",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
        }

        for path in required_paths:
            self.assertIn(f"  {path}:\n", contract)
        self.assertIn("operationId: authPostAuthRefresh", contract)
        self.assertIn("x-idempotent: true", contract)
        self.assertIn("RefreshTokenAuth", contract)
        self.assertIn("operationId: authGetAuthRegistrationConfig", contract)

    def test_refresh_endpoint_is_wired_with_required_security_headers(self) -> None:
        controller = read(
            "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/UserAuthController.java"
        )
        security = read(
            "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/security/SecurityConfiguration.java"
        )

        self.assertIn('@RequestMapping("/api/v1/auth")', controller)
        self.assertIn('@PostMapping("/refresh")', controller)
        self.assertIn('@RequestHeader("X-Refresh-Token")', controller)
        self.assertIn('@RequestHeader("X-Idempotency-Key")', controller)
        self.assertIn("HttpMethod.POST", security)
        self.assertIn('"/api/v1/auth/refresh"', security)

    def test_refresh_service_preserves_rotation_idempotency_and_session_invariants(self) -> None:
        service = read(
            "services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthService.java"
        )

        required_fragments = (
            "tokens.sameSecret(headerRefreshToken, request.refreshToken())",
            '"authPostAuthRefresh"',
            "repository.claimIdempotency(",
            "repository.findSessionForUpdate(oldRefreshHash)",
            "loginAllowedStatus(session.userStatus())",
            "session.expiresAt().isAfter(now)",
            "tokens.newRefreshToken()",
            "repository.rotateSession(",
            "snapshots.encrypt(",
            "snapshots.decrypt(",
        )
        for fragment in required_fragments:
            self.assertIn(fragment, service)
        self.assertIn(
            'Set.of("ACTIVE", "FROZEN", "RESTRICTED").contains(status)',
            service,
        )

    def test_backend_slice_covers_frozen_login_registration_handlers(self) -> None:
        """All R02 authentication adapters must remain present once implemented."""
        controller = read(
            "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/UserAuthController.java"
        )
        mapped_paths = set(re.findall(r'@PostMapping\("([^"]+)"\)', controller))

        self.assertEqual({
            "/security-challenges",
            "/password/login",
            "/sms/send",
            "/sms/login",
            "/invite-codes/validate",
            "/register",
            "/password/reset",
            "/refresh",
        }, mapped_paths)

    def test_android_startup_gate_has_explicit_safe_paths_before_auth_ui_exists(self) -> None:
        gate = read("apps/android/core/network/src/main/java/cc/orbexa/hhy/network/StartupGate.kt")
        screen = read(
            "apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt"
        )

        for state in (
            "StartupGateState.Maintenance",
            "StartupGateState.UpdateRequired",
            "StartupGateState.Ready",
            "StartupGateState.Unavailable",
        ):
            self.assertIn(state, gate)
            self.assertIn(state, screen)
        self.assertIn("policy.updateType == UpdateType.FORCED", gate)
        self.assertIn("if (!current.forced && skippedVersionCode == current.policy.latestVersionCode)", screen)
        self.assertIn("RetryButton", screen)
        self.assertIn("HhyShellScreen", read("apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt"))

    def test_auth_ui_renders_the_server_challenge_and_preserves_frozen_password_bound(self) -> None:
        auth_screen = read(
            "apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt"
        )
        migration = read("database/migrations/V017__r02_user_auth_invariants.sql")

        self.assertIn('result.data["imageBase64"]', auth_screen)
        self.assertIn("private fun ChallengeImage", auth_screen)
        self.assertIn("BitmapFactory.decodeByteArray", auth_screen)
        self.assertIn("'auth.password.max_length','72'::jsonb", migration)

    def test_auth_ui_keeps_error_codes_internal_and_shows_request_id_for_support(self) -> None:
        auth_screen = read(
            "apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt"
        )

        self.assertIn("errorForStatus(it, result.errorCode, result.retryAfterSeconds)", auth_screen)
        self.assertIn('Text("请求编号：$it"', auth_screen)
        self.assertNotIn("错误码：", auth_screen)
        self.assertNotIn("val errorCode: String?", auth_screen)

    def test_registration_uses_server_current_versions_and_never_manual_version_input(self) -> None:
        auth_screen = read(
            "apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt"
        )
        auth_api = read(
            "apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractAuthApi.kt"
        )
        store = read(
            "services/backend/access/src/main/java/cc/orbexa/hhy/access/user/UserAuthStore.java"
        )

        self.assertIn("api.registrationConfig()", auth_screen)
        self.assertIn("agreementVersionIds", auth_screen)
        self.assertIn("我已阅读并同意当前协议", auth_screen)
        self.assertNotIn("协议版本（逗号分隔）", auth_screen)
        self.assertIn('get("/api/v1/auth/registration-config")', auth_api)
        self.assertIn("agreement.current_version_id", store)

    def test_debug_startup_defaults_to_the_published_staging_channel(self) -> None:
        app_build = read("apps/android/app/build.gradle.kts")

        self.assertIn('environmentVariable("HHY_APP_CHANNEL").orElse("official")', app_build)
        self.assertIn('environmentVariable("HHY_APP_ENVIRONMENT").orElse("STAGING")', app_build)


if __name__ == "__main__":
    unittest.main()
