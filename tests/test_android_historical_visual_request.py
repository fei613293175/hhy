from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "android-historical-visual-request.yml"
REQUEST_PATH = ROOT / "config" / "android-historical-visual-request.yaml"
RUNNER_PATH = ROOT / "scripts" / "run_android_historical_visual_audit.sh"
AUDIT_TEST_PATH = (
    ROOT
    / "apps"
    / "android"
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "cc"
    / "orbexa"
    / "hhy"
    / "HistoricalVisualAuditTest.kt"
)


class AndroidHistoricalVisualRequestWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = yaml.load(
            WORKFLOW_PATH.read_text(encoding="utf-8"),
            Loader=yaml.BaseLoader,
        )

    def test_only_request_file_triggers_offline_historical_workflow(self) -> None:
        self.assertEqual(
            self.workflow["on"]["push"]["paths"],
            ["config/android-historical-visual-request.yaml"],
        )
        historical = self.workflow["jobs"]["historical-visual"]
        self.assertEqual(
            historical["uses"],
            "./.github/workflows/android-quality-gate.yml",
        )
        self.assertEqual(historical["with"]["release"], "HISTORICAL-UI")
        self.assertEqual(historical["with"]["candidate"], "false")

    def test_reusable_workflow_receives_its_declared_permissions(self) -> None:
        historical = self.workflow["jobs"]["historical-visual"]
        self.assertEqual(
            historical["permissions"],
            {
                "contents": "read",
                "issues": "write",
                "id-token": "write",
            },
        )

    def test_request_runner_and_r14_surfaces_share_exact_screenshot_count(self) -> None:
        request = yaml.safe_load(REQUEST_PATH.read_text(encoding="utf-8"))
        runner = RUNNER_PATH.read_text(encoding="utf-8")
        audit_test = AUDIT_TEST_PATH.read_text(encoding="utf-8")
        self.assertEqual(request["request_id"], "HISTORICAL-UI-20260730-004")
        self.assertEqual(request["expected_screenshot_count"], 33)
        self.assertIn('screenshot_count" -ne 33', runner)
        self.assertEqual(audit_test.count('captureStable("'), 33)
        self.assertIn('onNodeWithTag("r13.invalid.actions").assertIsDisplayed()', audit_test)
        self.assertIn('onNodeWithTag("r14.report.actions").assertIsDisplayed()', audit_test)
        expected_r14_files = {
            "31-r14-conversations.png",
            "32-r14-chat-detail.png",
            "33-r14-contact-sheet.png",
            "34-r14-report-sheet.png",
            "35-r14-block-dialog.png",
            "36-r14-delete-dialog.png",
        }
        for filename in expected_r14_files:
            self.assertEqual(audit_test.count(f'captureStable("{filename}")'), 1)


if __name__ == "__main__":
    unittest.main()
