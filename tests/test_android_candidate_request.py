from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.android_candidate_request import CandidateRequestError, load_request


class AndroidCandidateRequestTest(unittest.TestCase):
    def write_request(self, text: str) -> Path:
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "request.yaml"
        path.write_text(text, encoding="utf-8")
        return path

    def test_valid_request_is_normalized(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R06\n"
            "candidate: true\nremediation_attempt: 1\n"
            "request_id: R06-20260721-001\nreason: release candidate\n"
        ))
        self.assertTrue(payload["enabled"])
        self.assertEqual("R06", payload["release"])
        self.assertEqual(1, payload["remediation_attempt"])

    def test_pre_r06_and_out_of_range_releases_are_rejected(self) -> None:
        for release in ("R05", "R33", "DEVELOPMENT"):
            with self.subTest(release=release), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    f"schema_version: 1\nstatus: REQUESTED\nrelease: {release}\n"
                    "candidate: true\nremediation_attempt: 1\nrequest_id: R06-TEST-001\n"
                ))

    def test_disabled_or_unbounded_attempt_is_rejected(self) -> None:
        for candidate, attempt in ((False, 1), (True, 0), (True, 4), (True, "1")):
            candidate_text = "true" if candidate else "false"
            attempt_text = f"'{attempt}'" if isinstance(attempt, str) else str(attempt)
            with self.subTest(candidate=candidate, attempt=attempt), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\nrelease: R06\n"
                    f"candidate: {candidate_text}\nremediation_attempt: {attempt_text}\n"
                    "request_id: R06-TEST-001\n"
                ))


if __name__ == "__main__":
    unittest.main()
