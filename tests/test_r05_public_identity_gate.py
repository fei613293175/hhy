import unittest
from unittest.mock import patch

from scripts import check_r05_public_identity


class R05PublicIdentityGateTest(unittest.TestCase):
    def test_rejects_noncanonical_api_url(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "canonical"):
            check_r05_public_identity.verify("http://127.0.0.1:28085", "HHYTEST2026", 1)

    @patch.object(check_r05_public_identity, "challenge_answer", return_value="1234")
    @patch.object(check_r05_public_identity, "request_json")
    def test_verifies_registration_and_authenticated_consent(self, request_json, _answer) -> None:
        request_json.side_effect = [
            (200, {
                "success": True,
                "data": {"challengeId": "challenge", "imageBase64": "aW1hZ2U="},
            }),
            (200, {"success": True, "data": {"accessToken": "access-token"}}),
            (200, {
                "success": True,
                "data": {
                    "consentVersion": "91",
                    "title": "实名认证授权说明",
                    "content": "用于实名认证的受控授权说明正文。" * 20,
                },
            }),
        ]

        result = check_r05_public_identity.verify(
            "https://api.orbexa.cc", "HHYTEST2026", 1,
        )

        self.assertEqual("PASS", result["status"])
        self.assertEqual(200, result["registration_http"])
        self.assertEqual(200, result["identity_consent_http"])
        self.assertEqual(3, request_json.call_count)
        consent_request = request_json.call_args_list[2].args[0]
        self.assertEqual("Bearer access-token", consent_request.headers["Authorization"])


if __name__ == "__main__":
    unittest.main()
