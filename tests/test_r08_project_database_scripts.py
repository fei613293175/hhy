from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
INVARIANTS = ROOT / "database/tests/r08_project_invariants.sql"
RUNNER = ROOT / "scripts/run_r08_database_invariants.sh"
CONTAINER = ROOT / "scripts/run_r08_disposable_postgres_container.sh"


class R08ProjectDatabaseScriptsTest(unittest.TestCase):
    def test_sql_covers_positive_negative_idempotency_and_soft_delete_paths(self) -> None:
        sql = INVARIANTS.read_text(encoding="utf-8")
        for marker in (
            "R08_INVALID_CONTENT_STATUS_WAS_ACCEPTED",
            "R08_BLANK_PROJECT_COOPERATION_WAS_ACCEPTED",
            "R08_NON_NUMERIC_STATS_WERE_ACCEPTED",
            "R08_NON_NUMERIC_VERSION_WAS_ACCEPTED",
            "R08_ACTIVE_PROJECT_DETAIL_DELETE_WAS_ACCEPTED",
            "R08_PROJECT_DUPLICATE_IDEMPOTENCY_KEY_WAS_ACCEPTED",
            "UPDATE hhy.content_posts SET status = 'DELETED'",
        ):
            self.assertIn(marker, sql)

    def test_runner_requires_disposable_confirmation_and_replays_v033(self) -> None:
        shell = RUNNER.read_text(encoding="utf-8")
        self.assertIn("HHY_DB_SMOKE_CONFIRM", shell)
        self.assertIn("U033__r08_project_invariants.sql", shell)
        self.assertIn("V033__r08_project_invariants.sql", shell)
        self.assertIn("R08_V033_REPLAY PASS", shell)

    def test_container_runner_uses_exact_r08_target_and_postgres_17(self) -> None:
        shell = CONTAINER.read_text(encoding="utf-8")
        self.assertIn("postgres:17.10-alpine", shell)
        self.assertIn("hhy-r08-", shell)
        self.assertIn("R08_EMPTY_DATABASE_TO_V033 PASS", shell)
        self.assertIn("R08_UPGRADE_DATABASE_TO_V033 PASS", shell)
        self.assertNotIn("docker system prune", shell)


if __name__ == "__main__":
    unittest.main()
