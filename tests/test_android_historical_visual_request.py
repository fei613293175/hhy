from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "android-historical-visual-request.yml"


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


if __name__ == "__main__":
    unittest.main()
