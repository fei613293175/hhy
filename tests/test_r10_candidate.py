import hashlib
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/prepare_r10_ci_fixture.sh"
VISUAL_MANIFEST = ROOT / "tests/android/visual-manifests/R10.yaml"
SOURCE_REPORT = ROOT / "artifacts/validation/r10-task007-android/source-candidate-report.json"
VISUAL_BASELINE = ROOT / "tests/android/visual-baselines/R10"


class R10CandidateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = FIXTURE.read_text(encoding="utf-8")
        self.source_report = json.loads(SOURCE_REPORT.read_text(encoding="utf-8"))
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

    def test_archived_candidate_contains_exactly_home_and_three_group_pages(self) -> None:
        expected = {
            "01-home.png",
            "02-group-list.png",
            "03-group-detail.png",
            "04-group-editor.png",
        }
        self.assertEqual("R10", self.source_report["release"])
        evidence = self.source_report["runtime_report"]["screenshot_evidence"]
        self.assertEqual(expected, {row["screenshot"] for row in evidence})
        self.assertEqual(4, len(evidence))
        for row in evidence:
            baseline = VISUAL_BASELINE / row["screenshot"]
            self.assertTrue(baseline.is_file(), row["screenshot"])
            digest = hashlib.sha256(baseline.read_bytes()).hexdigest()
            self.assertEqual(row["sha256"], digest, row["screenshot"])

    def test_visual_manifest_covers_the_exact_r10_journey(self) -> None:
        manifest = VISUAL_MANIFEST.read_text(encoding="utf-8")
        for screenshot in ("01-home.png", "02-group-list.png", "03-group-detail.png", "04-group-editor.png"):
            self.assertIn(f"file: {screenshot}", manifest)
        for marker in (
            "hhy.screen.r06.home.loaded",
            "hhy.screen.r10.group.list.content",
            "hhy.screen.r10.group.detail.content",
            "hhy.screen.r10.group.editor.content",
        ):
            self.assertIn(f"marker: {marker}", manifest)
        self.assertIn("R10-ci-owner", manifest)
        self.assertIn("R10-ci-join", manifest)
        self.assertIn("remain masked in screenshots", manifest)


if __name__ == "__main__":
    unittest.main()
