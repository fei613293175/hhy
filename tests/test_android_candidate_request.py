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
    R13_ATTEMPT6_FIX_COMMIT = "808ee1d13bc15812609a2d2275318f5c1edae8e9"
    R13_ATTEMPT7_FIX_COMMIT = "0769abff1ecb12e4f4cd6415f66cdef8c92d0209"
    R13_ATTEMPT8_FIX_COMMIT = "02f3dc856b2dc6de4df0c1f94730b80388b5d563"
    R13_ATTEMPT9_FIX_COMMIT = "2832d47d5c2a27d73c7a7d2b4f63bc0f331a5e54"
    R13_ATTEMPT10_FIX_COMMIT = "8393b35464b16083833e7c9d95e7d303ce8a11a2"
    R13_ATTEMPT11_FIX_COMMIT = "7d53f3863b64ca1d2bc31baf7fd03ae36f6e3642"
    R13_ATTEMPT12_FIX_COMMIT = "10c904b02750641892e35616f162319867e7f0c9"
    R13_ATTEMPT13_FIX_COMMIT = "fedda55654a256db02877e1fdc7be25b3ba1ba4f"
    R13_ATTEMPT14_FIX_COMMIT = "8a73630d7cda118fa7fb88942e38b06939dc95a4"
    R13_ATTEMPT15_FIX_COMMIT = "035a80b21b51f6652cc1bf107a12e457eb06620b"

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

    def test_exact_r13_attempt_six_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 6\n"
            "request_id: R13-CANDIDATE-20260727-006\n"
            "attempt_exception_id: CR-0386\n"
            f"required_fix_commit: {self.R13_ATTEMPT6_FIX_COMMIT}\n"
        ))
        self.assertEqual(6, payload["effective_attempt_limit"])
        self.assertEqual("CR-0386", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT6_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_six_rejects_binding_drift_and_attempt_seven(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-006", "CR-0386", self.R13_ATTEMPT6_FIX_COMMIT, 6),
            "request": ("R13", "R13-CANDIDATE-20260727-007", "CR-0386", self.R13_ATTEMPT6_FIX_COMMIT, 6),
            "cr": ("R13", "R13-CANDIDATE-20260727-006", "CR-0385", self.R13_ATTEMPT6_FIX_COMMIT, 6),
            "commit": ("R13", "R13-CANDIDATE-20260727-006", "CR-0386", "b" * 40, 6),
            "attempt": ("R13", "R13-CANDIDATE-20260727-006", "CR-0386", self.R13_ATTEMPT6_FIX_COMMIT, 7),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_seven_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 7\n"
            "request_id: R13-CANDIDATE-20260727-007\n"
            "attempt_exception_id: CR-0387\n"
            f"required_fix_commit: {self.R13_ATTEMPT7_FIX_COMMIT}\n"
        ))
        self.assertEqual(7, payload["effective_attempt_limit"])
        self.assertEqual("CR-0387", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT7_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_seven_rejects_binding_drift_and_attempt_eight(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-007", "CR-0387", self.R13_ATTEMPT7_FIX_COMMIT, 7),
            "request": ("R13", "R13-CANDIDATE-20260727-008", "CR-0387", self.R13_ATTEMPT7_FIX_COMMIT, 7),
            "cr": ("R13", "R13-CANDIDATE-20260727-007", "CR-0386", self.R13_ATTEMPT7_FIX_COMMIT, 7),
            "commit": ("R13", "R13-CANDIDATE-20260727-007", "CR-0387", "b" * 40, 7),
            "attempt": ("R13", "R13-CANDIDATE-20260727-007", "CR-0387", self.R13_ATTEMPT7_FIX_COMMIT, 8),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_eight_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 8\n"
            "request_id: R13-CANDIDATE-20260727-008\n"
            "attempt_exception_id: CR-0388\n"
            f"required_fix_commit: {self.R13_ATTEMPT8_FIX_COMMIT}\n"
        ))
        self.assertEqual(8, payload["effective_attempt_limit"])
        self.assertEqual("CR-0388", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT8_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_eight_rejects_binding_drift_and_attempt_nine(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-008", "CR-0388", self.R13_ATTEMPT8_FIX_COMMIT, 8),
            "request": ("R13", "R13-CANDIDATE-20260727-009", "CR-0388", self.R13_ATTEMPT8_FIX_COMMIT, 8),
            "cr": ("R13", "R13-CANDIDATE-20260727-008", "CR-0387", self.R13_ATTEMPT8_FIX_COMMIT, 8),
            "commit": ("R13", "R13-CANDIDATE-20260727-008", "CR-0388", "b" * 40, 8),
            "attempt": ("R13", "R13-CANDIDATE-20260727-008", "CR-0388", self.R13_ATTEMPT8_FIX_COMMIT, 9),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_nine_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 9\n"
            "request_id: R13-CANDIDATE-20260727-009\n"
            "attempt_exception_id: CR-0389\n"
            f"required_fix_commit: {self.R13_ATTEMPT9_FIX_COMMIT}\n"
        ))
        self.assertEqual(9, payload["effective_attempt_limit"])
        self.assertEqual("CR-0389", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT9_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_nine_rejects_binding_drift_and_attempt_ten(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-009", "CR-0389", self.R13_ATTEMPT9_FIX_COMMIT, 9),
            "request": ("R13", "R13-CANDIDATE-20260727-010", "CR-0389", self.R13_ATTEMPT9_FIX_COMMIT, 9),
            "cr": ("R13", "R13-CANDIDATE-20260727-009", "CR-0388", self.R13_ATTEMPT9_FIX_COMMIT, 9),
            "commit": ("R13", "R13-CANDIDATE-20260727-009", "CR-0389", "b" * 40, 9),
            "attempt": ("R13", "R13-CANDIDATE-20260727-009", "CR-0389", self.R13_ATTEMPT9_FIX_COMMIT, 10),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_ten_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 10\n"
            "request_id: R13-CANDIDATE-20260727-010\n"
            "attempt_exception_id: CR-0390\n"
            f"required_fix_commit: {self.R13_ATTEMPT10_FIX_COMMIT}\n"
        ))
        self.assertEqual(10, payload["effective_attempt_limit"])
        self.assertEqual("CR-0390", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT10_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_ten_rejects_binding_drift_and_attempt_eleven(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-010", "CR-0390", self.R13_ATTEMPT10_FIX_COMMIT, 10),
            "request": ("R13", "R13-CANDIDATE-20260727-011", "CR-0390", self.R13_ATTEMPT10_FIX_COMMIT, 10),
            "cr": ("R13", "R13-CANDIDATE-20260727-010", "CR-0389", self.R13_ATTEMPT10_FIX_COMMIT, 10),
            "commit": ("R13", "R13-CANDIDATE-20260727-010", "CR-0390", "b" * 40, 10),
            "attempt": ("R13", "R13-CANDIDATE-20260727-010", "CR-0390", self.R13_ATTEMPT10_FIX_COMMIT, 11),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_eleven_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 11\n"
            "request_id: R13-CANDIDATE-20260727-011\n"
            "attempt_exception_id: CR-0391\n"
            f"required_fix_commit: {self.R13_ATTEMPT11_FIX_COMMIT}\n"
        ))
        self.assertEqual(11, payload["effective_attempt_limit"])
        self.assertEqual("CR-0391", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT11_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_eleven_rejects_binding_drift_and_attempt_twelve(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-011", "CR-0391", self.R13_ATTEMPT11_FIX_COMMIT, 11),
            "request": ("R13", "R13-CANDIDATE-20260727-012", "CR-0391", self.R13_ATTEMPT11_FIX_COMMIT, 11),
            "cr": ("R13", "R13-CANDIDATE-20260727-011", "CR-0390", self.R13_ATTEMPT11_FIX_COMMIT, 11),
            "commit": ("R13", "R13-CANDIDATE-20260727-011", "CR-0391", "b" * 40, 11),
            "attempt": ("R13", "R13-CANDIDATE-20260727-011", "CR-0391", self.R13_ATTEMPT11_FIX_COMMIT, 12),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_twelve_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 12\n"
            "request_id: R13-CANDIDATE-20260727-012\n"
            "attempt_exception_id: CR-0392\n"
            f"required_fix_commit: {self.R13_ATTEMPT12_FIX_COMMIT}\n"
        ))
        self.assertEqual(12, payload["effective_attempt_limit"])
        self.assertEqual("CR-0392", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT12_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_twelve_rejects_binding_drift_and_attempt_thirteen(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-012", "CR-0392", self.R13_ATTEMPT12_FIX_COMMIT, 12),
            "request": ("R13", "R13-CANDIDATE-20260727-013", "CR-0392", self.R13_ATTEMPT12_FIX_COMMIT, 12),
            "cr": ("R13", "R13-CANDIDATE-20260727-012", "CR-0391", self.R13_ATTEMPT12_FIX_COMMIT, 12),
            "commit": ("R13", "R13-CANDIDATE-20260727-012", "CR-0392", "b" * 40, 12),
            "attempt": ("R13", "R13-CANDIDATE-20260727-012", "CR-0392", self.R13_ATTEMPT12_FIX_COMMIT, 13),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_thirteen_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 13\n"
            "request_id: R13-CANDIDATE-20260727-013\n"
            "attempt_exception_id: CR-0393\n"
            f"required_fix_commit: {self.R13_ATTEMPT13_FIX_COMMIT}\n"
        ))
        self.assertEqual(13, payload["effective_attempt_limit"])
        self.assertEqual("CR-0393", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT13_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_thirteen_rejects_binding_drift_and_attempt_fourteen(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-013", "CR-0393", self.R13_ATTEMPT13_FIX_COMMIT, 13),
            "request": ("R13", "R13-CANDIDATE-20260727-014", "CR-0393", self.R13_ATTEMPT13_FIX_COMMIT, 13),
            "cr": ("R13", "R13-CANDIDATE-20260727-013", "CR-0392", self.R13_ATTEMPT13_FIX_COMMIT, 13),
            "commit": ("R13", "R13-CANDIDATE-20260727-013", "CR-0393", "b" * 40, 13),
            "attempt": ("R13", "R13-CANDIDATE-20260727-013", "CR-0393", self.R13_ATTEMPT13_FIX_COMMIT, 14),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_fourteen_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 14\n"
            "request_id: R13-CANDIDATE-20260727-014\n"
            "attempt_exception_id: CR-0395\n"
            f"required_fix_commit: {self.R13_ATTEMPT14_FIX_COMMIT}\n"
        ))
        self.assertEqual(14, payload["effective_attempt_limit"])
        self.assertEqual("CR-0395", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT14_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_fourteen_rejects_binding_drift_and_attempt_fifteen(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-014", "CR-0395", self.R13_ATTEMPT14_FIX_COMMIT, 14),
            "request": ("R13", "R13-CANDIDATE-20260727-015", "CR-0395", self.R13_ATTEMPT14_FIX_COMMIT, 14),
            "cr": ("R13", "R13-CANDIDATE-20260727-014", "CR-0393", self.R13_ATTEMPT14_FIX_COMMIT, 14),
            "commit": ("R13", "R13-CANDIDATE-20260727-014", "CR-0395", "b" * 40, 14),
            "attempt": ("R13", "R13-CANDIDATE-20260727-014", "CR-0395", self.R13_ATTEMPT14_FIX_COMMIT, 15),
        }
        for name, (release, request_id, cr, commit, attempt) in cases.items():
            with self.subTest(name=name), self.assertRaises(CandidateRequestError):
                load_request(self.write_request(
                    "schema_version: 1\nstatus: REQUESTED\n"
                    f"release: {release}\ncandidate: true\nremediation_attempt: {attempt}\n"
                    f"request_id: {request_id}\nattempt_exception_id: {cr}\n"
                    f"required_fix_commit: {commit}\n"
                ))

    def test_exact_r13_attempt_fifteen_exception_is_normalized_per_release(self) -> None:
        payload = load_request(self.write_request(
            "schema_version: 1\nstatus: REQUESTED\nrelease: R13\n"
            "candidate: true\nremediation_attempt: 15\n"
            "request_id: R13-CANDIDATE-20260727-015\n"
            "attempt_exception_id: CR-0398\n"
            f"required_fix_commit: {self.R13_ATTEMPT15_FIX_COMMIT}\n"
        ))
        self.assertEqual(15, payload["effective_attempt_limit"])
        self.assertEqual("CR-0398", payload["attempt_exception_id"])
        self.assertEqual(self.R13_ATTEMPT15_FIX_COMMIT, payload["required_fix_commit"])

    def test_r13_attempt_fifteen_rejects_binding_drift_and_attempt_sixteen(self) -> None:
        cases = {
            "release": ("R12", "R13-CANDIDATE-20260727-015", "CR-0398", self.R13_ATTEMPT15_FIX_COMMIT, 15),
            "request": ("R13", "R13-CANDIDATE-20260727-016", "CR-0398", self.R13_ATTEMPT15_FIX_COMMIT, 15),
            "cr": ("R13", "R13-CANDIDATE-20260727-015", "CR-0396", self.R13_ATTEMPT15_FIX_COMMIT, 15),
            "commit": ("R13", "R13-CANDIDATE-20260727-015", "CR-0398", "b" * 40, 15),
            "attempt": ("R13", "R13-CANDIDATE-20260727-015", "CR-0398", self.R13_ATTEMPT15_FIX_COMMIT, 16),
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
