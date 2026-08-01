from __future__ import annotations

import json
from pathlib import Path
import unittest

from tools.governance.gov50.orchestrator import validate_reviewer_result


ROOT = Path(__file__).resolve().parents[2]
TASK_ID = "TASK-R14-RECOVERY-015"
CANDIDATE = "a" * 40


def result(status: str, findings: list[dict]) -> dict:
    return {
        "schema": "hhy.reviewer-result/v5.0",
        "status": status,
        "task_id": TASK_ID,
        "commit": CANDIDATE,
        "summary": "reviewed",
        "findings": findings,
    }


class ReviewerResultContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.schema = json.loads(
            (ROOT / "governance/schemas/reviewer-result.schema.json").read_text(encoding="utf-8")
        )

    def test_provider_schema_avoids_conditional_composition(self) -> None:
        self.assertNotIn("allOf", self.schema)
        self.assertNotIn("if", self.schema)
        self.assertNotIn("then", self.schema)

    def test_pass_rejects_blocking_findings(self) -> None:
        blocking = [{
            "severity": "HIGH",
            "path": "apps/example.kt",
            "line": 1,
            "issue": "real issue",
            "evidence": "reproducible evidence",
            "requirement_ref": None,
        }]

        with self.assertRaisesRegex(ValueError, "PASS with blocking findings"):
            validate_reviewer_result(result("PASS", blocking), self.schema, TASK_ID, CANDIDATE)

    def test_block_requires_blocker_or_high_finding(self) -> None:
        low = [{
            "severity": "LOW",
            "path": None,
            "line": None,
            "issue": "minor issue",
            "evidence": "non-blocking evidence",
            "requirement_ref": None,
        }]

        with self.assertRaisesRegex(ValueError, "BLOCK requires"):
            validate_reviewer_result(result("BLOCK", low), self.schema, TASK_ID, CANDIDATE)

    def test_block_with_high_finding_is_valid(self) -> None:
        high = [{
            "severity": "HIGH",
            "path": "services/example.py",
            "line": 2,
            "issue": "blocking issue",
            "evidence": "verified evidence",
            "requirement_ref": "REQ-EXAMPLE",
        }]

        validate_reviewer_result(result("BLOCK", high), self.schema, TASK_ID, CANDIDATE)


if __name__ == "__main__":
    unittest.main()
