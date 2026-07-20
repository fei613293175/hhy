from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
INVARIANTS = ROOT / "database/tests/r06_content_home_invariants.sql"
RUNNER = ROOT / "scripts/run_r06_database_invariants.sh"
CONTAINER = ROOT / "scripts/run_r06_disposable_postgres_container.sh"


class R06ContentDatabaseScriptsTest(unittest.TestCase):
    def test_invariants_cover_type_history_and_home_guards(self) -> None:
        sql = INVARIANTS.read_text(encoding="utf-8")
        for marker in (
            "R06_DETAIL_TYPE_MISMATCH_WAS_ACCEPTED",
            "R06_CONTENT_TYPE_MUTATION_WAS_ACCEPTED",
            "R06_NEGATIVE_REFRESH_WAS_ACCEPTED",
            "R06_DUPLICATE_TEAM_OWNER_WAS_ACCEPTED",
            "R06_BLANK_CONTACT_CHANNEL_WAS_ACCEPTED",
            "R06_VERSION_MUTATION_WAS_ACCEPTED",
            "R06_STATUS_HISTORY_DELETE_WAS_ACCEPTED",
            "R06_NEGATIVE_HOME_ORDER_WAS_ACCEPTED",
            "R06_INVALID_BANNER_SCHEDULE_WAS_ACCEPTED",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))

    def test_runner_rolls_back_reapplies_and_retests_v029(self) -> None:
        shell = RUNNER.read_text(encoding="utf-8")
        self.assertIn("HHY_DB_SMOKE_CONFIRM", shell)
        self.assertIn("U029__r06_content_home_invariants.sql", shell)
        self.assertIn("V029__r06_content_home_invariants.sql", shell)
        self.assertIn('[[ "${remaining}" == "0|0|0" ]]', shell)
        self.assertIn('[[ "${reapplied}" == "12|7|8" ]]', shell)

    def test_container_is_postgres_17_disposable_and_not_published(self) -> None:
        shell = CONTAINER.read_text(encoding="utf-8")
        self.assertIn("postgres:17.10-alpine", shell)
        self.assertIn("hhy-r06-", shell)
        self.assertIn("trap cleanup EXIT", shell)
        self.assertIn("export CONTAINER", shell)
        self.assertNotIn("-p 5432", shell)
        self.assertIn("R06_EMPTY_DATABASE_TO_V029 PASS", shell)


if __name__ == "__main__":
    unittest.main()
