from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "android-visual-evidence-v5.yml"
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
R13_SURFACES_PATH = (
    ROOT / "apps" / "android" / "feature" / "activity" / "src" / "main" / "java"
    / "cc" / "orbexa" / "hhy" / "activity" / "R13ActivityScreens.kt"
)
R14_SHEETS_PATH = (
    ROOT / "apps" / "android" / "feature" / "chat" / "src" / "main" / "java"
    / "cc" / "orbexa" / "hhy" / "chat" / "R14ChatSheets.kt"
)


class AndroidHistoricalVisualRequestWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = yaml.load(
            WORKFLOW_PATH.read_text(encoding="utf-8"),
            Loader=yaml.BaseLoader,
        )

    def test_dispatch_requires_exact_release_and_commit_inputs(self) -> None:
        workflow_dispatch = self.workflow["on"]["workflow_dispatch"]
        self.assertEqual(set(workflow_dispatch["inputs"]), {"release", "commit"})
        self.assertEqual(workflow_dispatch["inputs"]["release"]["required"], "true")
        self.assertEqual(workflow_dispatch["inputs"]["commit"]["required"], "true")

        binding_step = self.workflow["jobs"]["offline-compose-visuals"]["steps"][1]
        self.assertIn('test "$(git rev-parse HEAD)" = "${{ inputs.commit }}"', binding_step["run"])
        self.assertIn('test "${{ inputs.release }}" = "R14"', binding_step["run"])

    def test_reusable_workflow_receives_its_declared_permissions(self) -> None:
        self.assertEqual(self.workflow["permissions"], {"contents": "read"})

    def test_request_runner_and_r14_surfaces_share_exact_screenshot_count(self) -> None:
        request = yaml.safe_load(REQUEST_PATH.read_text(encoding="utf-8"))
        runner = RUNNER_PATH.read_text(encoding="utf-8")
        audit_test = AUDIT_TEST_PATH.read_text(encoding="utf-8")
        self.assertEqual(request["request_id"], "HISTORICAL-UI-20260730-005")
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

    def test_long_sheets_use_device_height_and_a_tokenized_bottom_buffer(self) -> None:
        for path in (R13_SURFACES_PATH, R14_SHEETS_PATH):
            source = path.read_text(encoding="utf-8")
            self.assertIn("LocalConfiguration.current.screenHeightDp.dp - HhySpacing.Xxl", source)
            self.assertIn(".heightIn(max = sheetMaxHeight)", source)
            self.assertIn(".height(sheetMaxHeight)", source)
            self.assertIn(".navigationBarsPadding()", source)
            self.assertIn(".imePadding()", source)
            self.assertIn(".padding(bottom = HhySpacing.Xxl)", source)


if __name__ == "__main__":
    unittest.main()
