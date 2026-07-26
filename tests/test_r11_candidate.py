from pathlib import Path
import json
import re
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/prepare_r11_ci_fixture.sh"
VISUAL_MANIFEST = ROOT / "tests/android/visual-manifests/R11.yaml"
BUILD = ROOT / "apps/android/app/build.gradle.kts"
RELEASE_POLICY = ROOT / "apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt"
TEAM_LOGO = ROOT / "tests/android/fixtures/r11-team-logo.png"
BUILD_EVIDENCE = ROOT / "artifacts/validation/r11-task007-android/build-evidence.json"
CANDIDATE_REPORT = ROOT / "artifacts/validation/r11-task007-android/candidate-report.json"


class R11CandidateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = FIXTURE.read_text(encoding="utf-8")
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

    def test_archived_visual_evidence_matches_the_r11_contract(self) -> None:
        manifest = yaml.safe_load(VISUAL_MANIFEST.read_text(encoding="utf-8"))
        candidate_report = json.loads(CANDIDATE_REPORT.read_text(encoding="utf-8"))
        expected = {row["file"] for row in manifest["screens"]}
        archived = candidate_report["baseline_approval"]["screens"]
        self.assertEqual(expected, {row["file"] for row in archived})
        self.assertEqual(len(expected), len({row["sha256"] for row in archived}))
        for row in archived:
            self.assertRegex(row["sha256"], r"^[0-9a-f]{64}$")

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

    def test_archived_candidate_identity_is_immutable_and_current_identity_is_newer(self) -> None:
        build = BUILD.read_text(encoding="utf-8")
        release_policy = RELEASE_POLICY.read_text(encoding="utf-8")
        build_evidence = json.loads(BUILD_EVIDENCE.read_text(encoding="utf-8"))
        candidate_report = json.loads(CANDIDATE_REPORT.read_text(encoding="utf-8"))
        build_version = int(re.search(r"\bversionCode\s*=\s*(\d+)", build).group(1))
        policy_version = int(re.search(r"\bVERSION_CODE:\s*Int\s*=\s*(\d+)", release_policy).group(1))

        self.assertEqual("R11", build_evidence["release"])
        self.assertEqual(10220, build_evidence["version_code"])
        self.assertEqual("PASS", build_evidence["build_status"])
        self.assertEqual("a3c32668ae1d6502d859efd3e8c18947f650e150", build_evidence["commit"])
        self.assertEqual("R11", candidate_report["release"])
        self.assertEqual("PASS", candidate_report["status"])
        self.assertTrue(candidate_report["owner_test_allowed"])
        self.assertEqual(build_evidence["commit"], candidate_report["commit"])
        self.assertEqual("30069588243", candidate_report["source_github_run_id"])
        self.assertEqual("30074128265", candidate_report["github_run_id"])
        self.assertEqual("hhy-R11-a3c3266-candidate.apk", candidate_report["apk"]["file"])
        self.assertEqual(
            "0ad93ced14d5a47c1300c0dc340fde836613ec19243b6eedec2f4c42cf94bd5f",
            candidate_report["apk"]["sha256"],
        )
        self.assertEqual(build_version, policy_version)
        self.assertGreater(build_version, build_evidence["version_code"])


if __name__ == "__main__":
    unittest.main()
