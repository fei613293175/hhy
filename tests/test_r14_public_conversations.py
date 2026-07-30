import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from scripts.check_r14_public_conversations import validate_base_url, verify


ROOT = Path(__file__).resolve().parents[1]


class _Response:
    status = 200

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return None

    def read(self) -> bytes:
        return json.dumps({
            "success": True,
            "requestId": "request-r14-pass",
            "data": {"items": []},
        }).encode()


class R14PublicConversationsTest(unittest.TestCase):
    def test_public_and_explicit_loopback_are_the_only_allowed_origins(self) -> None:
        self.assertEqual(
            "https://api.orbexa.cc",
            validate_base_url("https://api.orbexa.cc/", False),
        )
        self.assertEqual(
            "http://127.0.0.1:28115",
            validate_base_url("http://127.0.0.1:28115", True),
        )
        for value in (
            "http://api.orbexa.cc",
            "https://user:pass@api.orbexa.cc",
            "https://example.com",
            "http://127.0.0.1:28115?token=bad",
        ):
            with self.assertRaises(RuntimeError):
                validate_base_url(value, True)

    @patch("scripts.check_r14_public_conversations.urlopen", return_value=_Response())
    def test_success_output_contains_no_token(self, mocked) -> None:
        token = "header.payload.signature"
        result = verify("https://api.orbexa.cc", token, 20, False)
        self.assertEqual(200, result["conversation_http"])
        self.assertNotIn(token, json.dumps(result))
        request = mocked.call_args.args[0]
        self.assertIn("/api/v1/conversations?", request.full_url)

    def test_route_script_has_r14_mode_without_persisting_token(self) -> None:
        script = (ROOT / "scripts/switch_android_candidate_route.sh").read_text()
        self.assertIn("R14_CONVERSATIONS", script)
        self.assertNotIn("echo ${HHY_CANDIDATE_ACCESS_TOKEN}", script)


if __name__ == "__main__":
    unittest.main()
