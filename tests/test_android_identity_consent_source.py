import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "apps/android/app/build.gradle.kts"
MAIN = ROOT / "apps/android/app/src/main/java/cc/orbexa/hhy/MainActivity.kt"
API = ROOT / "apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ContractIdentityApi.kt"
SCREEN = ROOT / "apps/android/feature/identity/src/main/java/cc/orbexa/hhy/identity/IdentityFlowScreen.kt"


class AndroidIdentityConsentSourceTest(unittest.TestCase):
    def test_consent_version_is_not_baked_into_apk(self) -> None:
        self.assertNotIn("HHY_IDENTITY_CONSENT_VERSION", BUILD.read_text(encoding="utf-8"))
        self.assertNotIn("IDENTITY_CONSENT_VERSION", MAIN.read_text(encoding="utf-8"))

    def test_form_loads_server_consent_and_never_displays_version_id(self) -> None:
        api = API.read_text(encoding="utf-8")
        screen = SCREEN.read_text(encoding="utf-8")
        self.assertIn("/api/v1/identity/consent", api)
        self.assertIn("api.consent(accessToken)", screen)
        self.assertIn("currentConsent.consentVersion", screen)
        self.assertIn("currentConsent?.content", screen)
        self.assertNotIn("Text(currentConsent.consentVersion", screen)
        self.assertNotIn("requestId", screen)


if __name__ == "__main__":
    unittest.main()
