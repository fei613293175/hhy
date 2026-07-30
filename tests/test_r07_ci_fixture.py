from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/prepare_r07_ci_fixture.sh"


class R07CiFixtureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.shell = FIXTURE.read_text(encoding="utf-8")
        self.do_body = self.shell.split("DO $$", 1)[1].split("$$;", 1)[0]

    def test_sensitive_client_variable_is_resolved_before_do_block(self) -> None:
        context_position = self.shell.index(
            "CREATE TEMP TABLE hhy_r07_ci_fixture_context ON COMMIT DROP"
        )
        do_position = self.shell.index("DO $$")
        self.assertLess(context_position, do_position)
        self.assertIn("WHERE phone=:'ci_phone' AND status='ACTIVE'", self.shell)
        self.assertNotIn(":'ci_phone'", self.do_body)
        self.assertIn(
            "SELECT user_id INTO ci_user FROM hhy_r07_ci_fixture_context",
            self.do_body,
        )

    def test_migration_guard_uses_frozen_schema(self) -> None:
        self.assertIn("FROM hhy.flyway_schema_history", self.do_body)
        self.assertNotIn("FROM flyway_schema_history", self.do_body)

    def test_fixture_keeps_atomic_failure_and_secret_output_guards(self) -> None:
        self.assertIn("-v ON_ERROR_STOP=1 -v ci_phone=", self.shell)
        self.assertIn("BEGIN;", self.shell)
        self.assertIn("COMMIT;", self.shell)
        self.assertIn("set -euo pipefail", self.shell)
        self.assertNotIn('echo "$phone"', self.shell)
        self.assertNotIn('printf "$phone"', self.shell)


if __name__ == "__main__":
    unittest.main()
