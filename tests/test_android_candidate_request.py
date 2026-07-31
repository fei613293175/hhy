from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.android_candidate_request import (
    CandidateRequestError,
    load_request,
    write_github_output,
)


ROOT = Path(__file__).resolve().parents[1]


class AndroidCandidateRequestTest(unittest.TestCase):
    def write_request(self, text: str) -> Path:
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "request.yaml"
        path.write_text(text, encoding="utf-8")
        return path

    def request_text(
        self,
        *,
        release: str = "R14",
        candidate: str = "true",
        attempt: object = 1,
        request_id: str = "R14-CANDIDATE-TEST-001",
        extra: str = "",
    ) -> str:
        attempt_text = f"'{attempt}'" if isinstance(attempt, str) else str(attempt)
        return (
            "schema_version: 1\n"
            "status: REQUESTED\n"
            f"release: {release}\n"
            f"candidate: {candidate}\n"
            f"remediation_attempt: {attempt_text}\n"
            f"request_id: {request_id}\n"
            f"{extra}"
        )

    def test_ordinary_attempts_one_through_three_are_accepted(self) -> None:
        for attempt in (1, 2, 3):
            with self.subTest(attempt=attempt):
                payload = load_request(self.write_request(self.request_text(attempt=attempt)))
                self.assertEqual(attempt, payload["remediation_attempt"])
                self.assertEqual(3, payload["effective_attempt_limit"])
                self.assertIsNone(payload["attempt_exception_id"])
                self.assertIsNone(payload["required_fix_commit"])

    def test_current_r14_recovery_request_is_an_ordinary_attempt(self) -> None:
        payload = load_request(ROOT / "config/android-candidate-request.yaml")
        self.assertEqual("R14", payload["release"])
        self.assertEqual(2, payload["remediation_attempt"])
        self.assertEqual("R14-CANDIDATE-20260731-002", payload["request_id"])
        self.assertIsNone(payload["attempt_exception_id"])

    def test_attempts_outside_one_through_three_are_always_rejected(self) -> None:
        for attempt in (-1, 0, 4, 20):
            with self.subTest(attempt=attempt), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(self.request_text(attempt=attempt)))

    def test_legacy_attempt_override_fields_are_always_rejected(self) -> None:
        fields = (
            "attempt_exception_id: CR-0509\n",
            "required_fix_commit: 0220185599581170af24db6ca575592da940e9f4\n",
            (
                "attempt_exception_id: CR-0509\n"
                "required_fix_commit: 0220185599581170af24db6ca575592da940e9f4\n"
            ),
        )
        for attempt in (2, 4, 20):
            for extra in fields:
                with self.subTest(attempt=attempt, extra=extra), self.assertRaises(CandidateRequestError):
                    load_request(self.write_request(self.request_text(attempt=attempt, extra=extra)))

    def test_candidate_release_range_is_r06_through_r32(self) -> None:
        for release in ("R06", "R14", "R32"):
            with self.subTest(release=release):
                payload = load_request(self.write_request(self.request_text(release=release)))
                self.assertEqual(release, payload["release"])
        for release in ("R05", "R33", "DEVELOPMENT", "HISTORICAL-UI"):
            with self.subTest(release=release), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(self.request_text(release=release)))

    def test_historical_visual_request_must_be_non_candidate(self) -> None:
        payload = load_request(self.write_request(self.request_text(
            release="HISTORICAL-UI",
            candidate="false",
            request_id="HISTORICAL-UI-TEST-001",
        )))
        self.assertFalse(payload["candidate"])
        with self.assertRaises(CandidateRequestError):
            load_request(self.write_request(self.request_text(candidate="false")))

    def test_non_integer_attempt_is_rejected(self) -> None:
        for attempt in ("1", True):
            with self.subTest(attempt=attempt), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(self.request_text(attempt=attempt)))

    def test_invalid_request_identity_is_rejected(self) -> None:
        for request_id in ("", "short", "contains spaces"):
            with self.subTest(request_id=request_id), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(self.request_text(request_id=request_id)))

    def test_github_output_preserves_bounded_contract(self) -> None:
        payload = load_request(self.write_request(self.request_text(attempt=3)))
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        output = Path(temp.name) / "github-output.txt"
        write_github_output(output, payload)
        values = dict(line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines())
        self.assertEqual("3", values["remediation_attempt"])
        self.assertEqual("3", values["effective_attempt_limit"])
        self.assertEqual("", values["attempt_exception_id"])
        self.assertEqual("", values["required_fix_commit"])


if __name__ == "__main__":
    unittest.main()
