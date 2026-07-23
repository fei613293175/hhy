from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/prepare_r10_ci_fixture.sh"
JOURNEY = ROOT / "apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt"


class R10CandidateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = FIXTURE.read_text(encoding="utf-8")
        self.journey = JOURNEY.read_text(encoding="utf-8")
        self.do_body = self.fixture.split("DO $$", 1)[1].split("$$;", 1)[0]

    def test_fixture_is_limited_to_r10_candidate_staging(self) -> None:
        self.assertIn("hhy-r10-ci-candidate-*", self.fixture)
        self.assertIn("SPRING_PROFILES_ACTIVE", self.fixture)
        self.assertIn("HHY_CI_AUTOMATION_ENABLED", self.fixture)
        self.assertIn("version='036'", self.do_body)
        self.assertNotIn("version='037'", self.do_body)
        self.assertNotIn('echo "$phone"', self.fixture)

    def test_fixture_uses_real_group_facts_and_immutable_history(self) -> None:
        self.assertIn("'GROUP'", self.do_body)
        self.assertIn("hhy.group_details", self.do_body)
        self.assertIn("'WECHAT'", self.do_body)
        self.assertIn("'JOIN_PASSWORD'", self.do_body)
        self.assertIn("ON CONFLICT (content_id,version_no) DO NOTHING", self.do_body)
        self.assertNotIn("ON CONFLICT (content_id,version_no) DO UPDATE", self.do_body)
        self.assertIn("VALUES (fixture_content,'0','0','0','0','0','0')", self.do_body)
        for invented_metric in ("收益", "活跃度", "下载量", "评分"):
            self.assertNotIn(invented_metric, self.do_body)

    def test_journey_captures_exactly_home_and_three_group_pages(self) -> None:
        expected = (
            'captureStable("01-home.png")',
            'captureStable("02-group-list.png")',
            'captureStable("03-group-detail.png")',
            'captureStable("04-group-editor.png")',
        )
        for capture in expected:
            self.assertIn(capture, self.journey)
        self.assertEqual(4, self.journey.count("captureStable(\""))
        self.assertIn('clickResource("home.category.group")', self.journey)
        self.assertIn('clickResource("r10.group.edit")', self.journey)
        self.assertNotIn("authenticatedR09Pages", self.journey)
        self.assertNotIn("r09.app.", self.journey)


if __name__ == "__main__":
    unittest.main()
