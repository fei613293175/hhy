from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/prepare_r11_ci_fixture.sh"
JOURNEY = ROOT / "apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt"
VISUAL_MANIFEST = ROOT / "tests/android/visual-manifests/R11.yaml"
BUILD = ROOT / "apps/android/app/build.gradle.kts"
RELEASE_POLICY = ROOT / "apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt"
VERSION_TEST = ROOT / "apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt"
REQUEST = ROOT / "config/android-candidate-request.yaml"
TEAM_LOGO = ROOT / "tests/android/fixtures/r11-team-logo.png"


class R11CandidateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = FIXTURE.read_text(encoding="utf-8")
        self.journey = JOURNEY.read_text(encoding="utf-8")
        self.do_body = self.fixture.split("DO $$", 1)[1].split("$$;", 1)[0]

    def test_fixture_is_limited_to_r11_candidate_staging(self) -> None:
        self.assertIn("hhy-r11-ci-candidate-*", self.fixture)
        self.assertIn("SPRING_PROFILES_ACTIVE", self.fixture)
        self.assertIn("HHY_CI_AUTOMATION_ENABLED", self.fixture)
        self.assertIn("version='038'", self.do_body)
        self.assertNotIn("version='039'", self.do_body)
        self.assertNotIn('echo "$phone"', self.fixture)

    def test_fixture_contains_every_reviewable_team_leader_fact(self) -> None:
        for column in (
            "team_name", "nickname", "logo_media_id", "region", "personal_intro",
            "team_intro", "size_range", "skills", "cooperation_types",
            "cooperation_requirement", "past_cases", "accept_private_chat",
        ):
            self.assertIn(column, self.do_body)
        self.assertIn("identity.status='VERIFIED'", self.fixture)
        self.assertIn("m.status='READY'", self.fixture)
        self.assertIn("m.visibility='PUBLIC'", self.fixture)
        self.assertIn("SELECT id,config_version_id INTO fixture_binding,fixture_config", self.do_body)
        self.assertLess(
            self.do_body.index("SELECT id,config_version_id INTO fixture_binding,fixture_config"),
            self.do_body.index("INSERT INTO hhy.provider_config_versions"),
        )
        self.assertIn("owned.type='TEAM_LEADER'", self.fixture)
        self.assertIn("owned.status<>'DELETED')=1", self.fixture)
        self.assertIn("fixture_content,'90011'", self.do_body)
        self.assertNotIn("fixture_content,'r11-ci-1'", self.do_body)
        self.assertIn("ON CONFLICT (content_id,version_no) DO NOTHING", self.do_body)
        self.assertNotIn("ON CONFLICT (content_id,version_no) DO UPDATE", self.do_body)
        self.assertIn("r11-team-logo.png", self.do_body)
        self.assertNotIn("01-app-list.png", self.do_body)
        self.assertTrue(TEAM_LOGO.is_file())

    def test_journey_captures_exactly_home_and_three_team_leader_pages(self) -> None:
        expected = (
            'captureStable("01-home.png")',
            'captureStable("02-team-leader-list.png")',
            'captureStable("03-team-leader-detail.png")',
            'captureStable("04-team-leader-editor.png")',
        )
        for capture in expected:
            self.assertIn(capture, self.journey)
        self.assertEqual(4, self.journey.count('captureStable("'))
        self.assertIn('clickResource("home.category.team-leader")', self.journey)
        self.assertIn('clickExactText("编辑")', self.journey)
        self.assertNotIn("authenticatedR10Pages", self.journey)
        self.assertNotIn("r10.group", self.journey)

    def test_visual_manifest_covers_the_exact_r11_journey(self) -> None:
        manifest = yaml.safe_load(VISUAL_MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual("R11", manifest["release"])
        self.assertEqual("AI_IMPLEMENTATION_AGENT", manifest["review_authority"])
        self.assertEqual(
            {
                "01-home.png", "02-team-leader-list.png",
                "03-team-leader-detail.png", "04-team-leader-editor.png",
            },
            {row["file"] for row in manifest["screens"]},
        )
        self.assertEqual(
            {
                "hhy.screen.r06.home.loaded",
                "hhy.screen.r11.team-leader.list.content",
                "hhy.screen.r11.team_leader.detail.content",
                "hhy.screen.r11.team-leader.editor.content",
            },
            {row["marker"] for row in manifest["screens"]},
        )
        detail = next(row for row in manifest["screens"] if row["screen_id"] == "SCR-DETAIL-004")
        self.assertIn("hhy-contact-v1:r11-ci-team-contact", detail["forbidden_text"])
        self.assertIn("WECHAT", detail["forbidden_text"])

    def test_candidate_identity_is_monotonic_and_unique(self) -> None:
        build = BUILD.read_text(encoding="utf-8")
        release_policy = RELEASE_POLICY.read_text(encoding="utf-8")
        version_test = VERSION_TEST.read_text(encoding="utf-8")
        request = yaml.safe_load(REQUEST.read_text(encoding="utf-8"))
        self.assertIn("versionCode = 10220", build)
        self.assertIn("VERSION_CODE: Int = 10220", release_policy)
        self.assertIn('"R11 test APK versionCode must remain monotonic", 10220', version_test)
        self.assertNotIn("10219", build + release_policy + version_test)
        self.assertEqual("R11", request["release"])
        self.assertTrue(request["candidate"])
        self.assertEqual(2, request["remediation_attempt"])
        self.assertEqual("R11-CANDIDATE-20260724-002", request["request_id"])


if __name__ == "__main__":
    unittest.main()
