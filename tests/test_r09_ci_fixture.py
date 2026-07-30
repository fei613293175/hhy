from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/prepare_r09_ci_fixture.sh"


class R09CiFixtureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.shell = FIXTURE.read_text(encoding="utf-8")
        self.do_body = self.shell.split("DO $$", 1)[1].split("$$;", 1)[0]

    def test_sensitive_client_variable_is_resolved_before_do_block(self) -> None:
        context_position = self.shell.index(
            "CREATE TEMP TABLE hhy_r09_ci_fixture_context ON COMMIT DROP"
        )
        do_position = self.shell.index("DO $$")
        self.assertLess(context_position, do_position)
        self.assertIn("WHERE phone=:'ci_phone' AND status='ACTIVE'", self.shell)
        self.assertNotIn(":'ci_phone'", self.do_body)
        self.assertIn(
            "SELECT user_id INTO ci_user FROM hhy_r09_ci_fixture_context",
            self.do_body,
        )

    def test_fixture_requires_v035_and_only_prepares_app_facts(self) -> None:
        self.assertIn("version='035'", self.do_body)
        self.assertIn("'R09候选联调App'", self.do_body)
        self.assertIn("'APP'", self.do_body)
        self.assertIn("hhy.app_details", self.do_body)
        self.assertIn(
            "ON CONFLICT (content_id,version_no) DO NOTHING",
            self.do_body,
        )
        self.assertNotIn(
            "ON CONFLICT (content_id,version_no) DO UPDATE",
            self.do_body,
        )
        self.assertIn('"categoryCode":"TOOLS"', self.do_body)
        self.assertIn("'ANDROID'", self.do_body)
        self.assertIn("https://www.orbexa.cc", self.do_body)
        self.assertNotIn(".apk", self.do_body.lower())
        self.assertNotIn("下载量", self.do_body)
        self.assertNotIn("评分", self.do_body)

    def test_fixture_uses_zero_engagement_and_keeps_atomic_secret_guards(self) -> None:
        self.assertIn("VALUES (fixture_content,'0','0','0','0','0','0')", self.do_body)
        self.assertIn("-v ON_ERROR_STOP=1 -v ci_phone=", self.shell)
        self.assertIn("BEGIN;", self.shell)
        self.assertIn("COMMIT;", self.shell)
        self.assertIn("set -euo pipefail", self.shell)
        self.assertNotIn('echo "$phone"', self.shell)
        self.assertNotIn('printf "$phone"', self.shell)

    def test_fixture_binds_two_real_public_images_without_absolute_object_keys(self) -> None:
        self.assertIn("'https://download.orbexa.cc/r09-candidate-media/attempt-2/'", self.do_body)
        self.assertIn("'01-app-list.png'", self.do_body)
        self.assertIn("'03-app-editor.png'", self.do_body)
        self.assertIn("'4327c819bbaa435f681ac34f5dd01fff920bd850f26974b4708c792490b14878'", self.do_body)
        self.assertIn("'4d15b62c15e596e01a19fea2a6bd02f3fb3ba5b0d0f1645482785e90119cffde'", self.do_body)
        self.assertIn("DELETE FROM hhy.content_media WHERE content_id=fixture_content", self.do_body)
        self.assertIn("(fixture_content,fixture_media_list,'image/png',0)", self.do_body)
        self.assertIn("(fixture_content,fixture_media_editor,'image/png',1)", self.do_body)
        self.assertNotIn("object_key='https://", self.do_body)
        self.assertIn("string_agg(m.id::text,',' ORDER BY cm.sort_order)", self.shell)


if __name__ == "__main__":
    unittest.main()
