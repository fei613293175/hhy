from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
INVARIANTS = ROOT / "database/tests/r07_search_invariants.sql"
RUNNER = ROOT / "scripts/run_r07_database_invariants.sh"
CONTAINER = ROOT / "scripts/run_r07_disposable_postgres_container.sh"


class R07SearchDatabaseScriptsTest(unittest.TestCase):
    def test_invariants_cover_search_hot_term_contact_and_clear_idempotency(self) -> None:
        sql = INVARIANTS.read_text(encoding="utf-8")
        for marker in (
            "R07_BLANK_SEARCH_HISTORY_WAS_ACCEPTED",
            "R07_BLANK_HOT_TERM_WAS_ACCEPTED",
            "R07_NEGATIVE_HOT_WEIGHT_WAS_ACCEPTED",
            "R07_INVALID_HOT_SCHEDULE_WAS_ACCEPTED",
            "R07_DUPLICATE_HOT_TERM_WAS_ACCEPTED",
            "R07_INCOMPLETE_CONTACT_ACCESS_WAS_ACCEPTED",
            "R07_CONTACT_ACCESS_MUTATION_WAS_ACCEPTED",
            "R07_CONTACT_ACCESS_DELETE_WAS_ACCEPTED",
            "R07_HISTORY_CLEAR_DUPLICATE_KEY_WAS_ACCEPTED",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))

    def test_runner_rolls_back_reapplies_and_retests_v031(self) -> None:
        shell = RUNNER.read_text(encoding="utf-8")
        self.assertIn("HHY_DB_SMOKE_CONFIRM", shell)
        self.assertIn("U031__r07_search_invariants.sql", shell)
        self.assertIn("V031__r07_search_invariants.sql", shell)
        self.assertIn('[[ "${remaining}|${weight_default}" == "0|0|0|" ]]', shell)
        self.assertIn('[[ "${reapplied}|${weight_default}" == "4|4|1|0" ]]', shell)

    def test_container_covers_empty_and_upgrade_databases_without_published_port(self) -> None:
        shell = CONTAINER.read_text(encoding="utf-8")
        self.assertIn("postgres:17.10-alpine", shell)
        self.assertIn("hhy-r07-", shell)
        self.assertIn("trap cleanup EXIT", shell)
        self.assertNotIn("-p 5432", shell)
        self.assertIn("R07_EMPTY_DATABASE_TO_V031 PASS", shell)
        self.assertIn("R07_UPGRADE_DATABASE_TO_V031 PASS", shell)
        self.assertIn("R07_DISPOSABLE_POSTGRES_CONTAINER PASS", shell)


if __name__ == "__main__":
    unittest.main()
