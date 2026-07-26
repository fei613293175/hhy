from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.android_candidate_request import CandidateRequestError, load_request


class AndroidCandidateRequestTest(unittest.TestCase):
    R12_ATTEMPT4_FIX_COMMIT = "daae207af319008411995b7ac23a13c9042fc5a2"
    R12_ATTEMPT5_FIX_COMMIT = "fbde523e8e751b74f7300ef125c70fa9eb4d03fd"
    R12_ATTEMPT6_FIX_COMMIT = "f11901ea1d3c61ccbcc59ec4cab3e77e24b1f1b0"
    R12_ATTEMPT7_FIX_COMMIT = "bcde496e9821dce301d4b13bfe0b1f590f1380f5"
    R13_ATTEMPT4_FIX_COMMIT = "c602d2f0194980efcf19f233148f0b0932254ba6"
    R13_ATTEMPT5_FIX_COMMIT = "f8239c989fd9d17e74273ab5eaaa5d4349488882"

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

    def test_historical_visual_request_is_non_candidate_only(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: HISTORICAL-UI\n"
            "candidate: false\nremediation_attempt: 1\n"
            "request_id: HISTORICAL-UI-20260722-001\nreason: offline visual audit\n"
        ))
        self.assertTrue(payload["enabled"])
        self.assertEqual("HISTORICAL-UI", payload["release"])
        self.assertFalse(payload["candidate"])

    def test_pre_r06_and_out_of_range_releases_are_rejected(self) -> None:
        for release in ("R05", "R33", "DEVELOPMENT"):
            with self.subTest(release=release), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    f"schema_version: 1\nstatus: REQUESTED\nrelease: {release}\n"
                    "candidate: true\nremediation_attempt: 1\nrequest_id: R06-TEST-001\n"
                ))

    def test_disabled_or_unbounded_attempt_is_rejected(self) -> None:
        for candidate, attempt in ((True, 0), (True, 4), (True, 5), (True, "1")):
            candidate_text = "true" if candidate else "false"
            attempt_text = f"'{attempt}'" if isinstance(attempt, str) else str(attempt)
            with self.subTest(candidate=candidate, attempt=attempt), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\nrelease: R06\n"
                    f"candidate: {candidate_text}\nremediation_attempt: {attempt_text}\n"
                    "request_id: R06-TEST-001\n"
                ))

    def test_exact_r12_attempt_four_exception_is_normalized(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R12\n"
            "candidate: true\nremediation_attempt: 4\n"
            "request_id: R12-CANDIDATE-20260726-004\n"
            "attempt_exception_id: CR-0344\n"
            f"required_fix_commit: {self.R12_ATTEMPT4_FIX_COMMIT}\n"
        ))
        self.assertEqual(4, payload["effective_attempt_limit"])
        self.assertEqual("CR-0344", payload["attempt_exception_id"])
        self.assertEqual(self.R12_ATTEMPT4_FIX_COMMIT, payload["required_fix_commit"])

    def test_exact_r12_attempt_five_exception_is_normalized(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R12\n"
            "candidate: true\nremediation_attempt: 5\n"
            "request_id: R12-CANDIDATE-20260726-005\n"
            "attempt_exception_id: CR-0352\n"
            f"required_fix_commit: {self.R12_ATTEMPT5_FIX_COMMIT}\n"
        ))
        self.assertEqual(5, payload["effective_attempt_limit"])
        self.assertEqual("CR-0352", payload["attempt_exception_id"])
        self.assertEqual(self.R12_ATTEMPT5_FIX_COMMIT, payload["required_fix_commit"])

    def test_exact_r13_attempt_four_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 4\n"
            "request_id: R13-CANDIDATE-20260727-004\n"
            "attempt_exception_id: CR-0383\n"
            f"required_fix_commit: {self.R13_ATTEMPT4_FIX_COMMIT}\n"
        ))
        self.assertEqual(4, payload["effective_attempt_limit"])
        self.assertEqual("CR-0383", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT4_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_four_rejects_binding_drift_and_attempt_five(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-004", "CR-0383", self.R13_ATTEMPT4_FIX_COMMIT, 4),
            "request": ("R13", "R13-CANDIDATE-20260727-005", "CR-0383", self.R13_ATTEMPT4_FIX_COMMIT, 4),
            "cr": ("R13", "R13-CANDIDATE-20260727-004", "CR-9999", self.R13_ATTEMPT4_FIX_COMMIT, 4),
            "commit": ("R13", "R13-CANDIDATE-20260727-004", "CR-0383", "b" * 40, 4),
            "attempt": ("R13", "R13-CANDIDATE-20260727-004", "CR-0383", self.R13_ATTEMPT4_FIX_COMMIT, 5),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_five_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 5\n"
            "request_id: R13-CANDIDATE-20260727-005\n"
            "attempt_exception_id: CR-0385\n"
            f"required_fix_commit: {self.R13_ATTEMPT5_FIX_COMMIT}\n"
        ))
        self.assertEqual(5, payload["effective_attempt_limit"])
        self.assertEqual("CR-0385", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT5_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_five_rejects_binding_drift_and_attempt_six(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-005", "CR-0385", self.R13_ATTEMPT5_FIX_COMMIT, 5),
            "request": ("R13", "R13-CANDIDATE-20260727-006", "CR-0385", self.R13_ATTEMPT5_FIX_COMMIT, 5),
            "cr": ("R13", "R13-CANDIDATE-20260727-005", "CR-0383", self.R13_ATTEMPT5_FIX_COMMIT, 5),
            "commit": ("R13", "R13-CANDIDATE-20260727-005", "CR-0385", "b" * 40, 5),
            "attempt": ("R13", "R13-CANDIDATE-20260727-005", "CR-0385", self.R13_ATTEMPT5_FIX_COMMIT, 6),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r12_attempt_six_exception_is_normalized(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R12\n"
            "candidate: true\nremediation_attempt: 6\n"
            "request_id: R12-CANDIDATE-20260726-006\n"
            "attempt_exception_id: CR-0355\n"
            f"required_fix_commit: {self.R12_ATTEMPT6_FIX_COMMIT}\n"
        ))
        self.assertEqual(6, payload["effective_attempt_limit"])
        self.assertEqual("CR-0355", payload["attempt_exception_id"])
        self.assertEqual(self.R12_ATTEMPT6_FIX_COMMIT, payload["required_fix_commit"])

    def test_exact_r12_attempt_seven_exception_is_normalized(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R12\n"
            "candidate: true\nremediation_attempt: 7\n"
            "request_id: R12-CANDIDATE-20260726-007\n"
            "attempt_exception_id: CR-0358\n"
            f"required_fix_commit: {self.R12_ATTEMPT7_FIX_COMMIT}\n"
        ))
        self.assertEqual(7, payload["effective_attempt_limit"])
        self.assertEqual("CR-0358", payload["attempt_exception_id"])
        self.assertEqual(self.R12_ATTEMPT7_FIX_COMMIT, payload["required_fix_commit"])

    def test_attempt_four_requires_every_approved_binding(self) -> None:
        cases = {
            "release": ("R13", "R12-CANDIDATE-20260726-004", "CR-0344", self.R12_ATTEMPT4_FIX_COMMIT, 4),
            "request": ("R12", "R12-CANDIDATE-20260726-999", "CR-0344", self.R12_ATTEMPT4_FIX_COMMIT, 4),
            "cr": ("R12", "R12-CANDIDATE-20260726-004", "CR-9999", self.R12_ATTEMPT4_FIX_COMMIT, 4),
            "commit": ("R12", "R12-CANDIDATE-20260726-004", "CR-0344", "b" * 40, 4),
            "attempt": ("R12", "R12-CANDIDATE-20260726-004", "CR-0344", self.R12_ATTEMPT4_FIX_COMMIT, 5),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_attempt_five_requires_every_approved_binding(self) -> None:
        cases = {
            "release": ("R13", "R12-CANDIDATE-20260726-005", "CR-0352", self.R12_ATTEMPT5_FIX_COMMIT, 5),
            "request": ("R12", "R12-CANDIDATE-20260726-004", "CR-0352", self.R12_ATTEMPT5_FIX_COMMIT, 5),
            "cr": ("R12", "R12-CANDIDATE-20260726-005", "CR-0344", self.R12_ATTEMPT5_FIX_COMMIT, 5),
            "commit": ("R12", "R12-CANDIDATE-20260726-005", "CR-0352", "b" * 40, 5),
            "attempt": ("R12", "R12-CANDIDATE-20260726-005", "CR-0352", self.R12_ATTEMPT5_FIX_COMMIT, 6),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_attempt_six_requires_every_approved_binding(self) -> None:
        cases = {
            "release": ("R13", "R12-CANDIDATE-20260726-006", "CR-0355", self.R12_ATTEMPT6_FIX_COMMIT, 6),
            "request": ("R12", "R12-CANDIDATE-20260726-005", "CR-0355", self.R12_ATTEMPT6_FIX_COMMIT, 6),
            "cr": ("R12", "R12-CANDIDATE-20260726-006", "CR-0352", self.R12_ATTEMPT6_FIX_COMMIT, 6),
            "commit": ("R12", "R12-CANDIDATE-20260726-006", "CR-0355", "b" * 40, 6),
            "attempt": ("R12", "R12-CANDIDATE-20260726-006", "CR-0355", self.R12_ATTEMPT6_FIX_COMMIT, 7),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_attempt_seven_requires_every_approved_binding_and_rejects_eight(self) -> None:
        cases = {
            "release": ("R13", "R12-CANDIDATE-20260726-007", "CR-0358", self.R12_ATTEMPT7_FIX_COMMIT, 7),
            "request": ("R12", "R12-CANDIDATE-20260726-006", "CR-0358", self.R12_ATTEMPT7_FIX_COMMIT, 7),
            "cr": ("R12", "R12-CANDIDATE-20260726-007", "CR-0355", self.R12_ATTEMPT7_FIX_COMMIT, 7),
            "commit": ("R12", "R12-CANDIDATE-20260726-007", "CR-0358", "b" * 40, 7),
            "attempt": ("R12", "R12-CANDIDATE-20260726-007", "CR-0358", self.R12_ATTEMPT7_FIX_COMMIT, 8),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_candidate_and_release_mode_cannot_be_mixed(self) -> None:
        for release, candidate in (("R06", False), ("HISTORICAL-UI", True)):
            with self.subTest(release=release, candidate=candidate), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: {str(candidate).lower()}\n"
                    "remediation_attempt: 1\nrequest_id: HISTORICAL-UI-TEST-001\n"
                ))


if __name__ == "__main__":
    unittest.main()
