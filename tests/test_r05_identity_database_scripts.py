from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
INVARIANTS = ROOT / "database/tests/r05_identity_invariants.sql"
RUNNER = ROOT / "scripts/run_r05_database_invariants.sh"
CONTAINER = ROOT / "scripts/run_r05_disposable_postgres_container.sh"


class R05IdentityDatabaseScriptsTest(unittest.TestCase):
    def test_invariants_cover_success_rejection_immutability_and_audit(self) -> None:
        sql = INVARIANTS.read_text(encoding="utf-8")
        for marker in (
            "R05_DUPLICATE_SESSION_IDEMPOTENCY_WAS_ACCEPTED",
            "R05_SECOND_ACTIVE_SESSION_WAS_ACCEPTED",
            "R05_TERMINAL_SESSION_WITHOUT_COMPLETION_WAS_ACCEPTED",
            "R05_SELF_RETRY_WAS_ACCEPTED",
            "R05_DUPLICATE_PROVIDER_ORDER_WAS_ACCEPTED",
            "R05_MEDIA_WITHOUT_OBJECT_WAS_ACCEPTED",
            "R05_PUBLIC_IDENTITY_MEDIA_WAS_ACCEPTED",
            "R05_REVIEW_MUTATION_WAS_ACCEPTED",
            "R05_ACCESS_AUDIT_MUTATION_WAS_ACCEPTED",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))
        self.assertEqual(sql.count("WHEN object_not_in_prerequisite_state"), 2)

    def test_database_runner_requires_disposable_confirmation_and_replays_v023(self) -> None:
        shell = RUNNER.read_text(encoding="utf-8")
        self.assertIn("HHY_DB_SMOKE_CONFIRM", shell)
        self.assertIn("U023__r05_identity_invariants.sql", shell)
        self.assertIn("U024__r05_identity_api_storage.sql", shell)
        self.assertIn("U025__r05_identity_provider_payload.sql", shell)
        self.assertIn("V023__r05_identity_invariants.sql", shell)
        self.assertIn("V024__r05_identity_api_storage.sql", shell)
        self.assertIn("V025__r05_identity_provider_payload.sql", shell)
        self.assertIn('[[ "${remaining}" == "0|0|0|0" ]]', shell)
        self.assertIn('[[ "${reapplied}" == "32|12|1|30" ]]', shell)

    def test_container_is_postgres_17_disposable_and_not_published(self) -> None:
        shell = CONTAINER.read_text(encoding="utf-8")
        self.assertIn("postgres:17.10-alpine", shell)
        self.assertIn("hhy-r05-", shell)
        self.assertIn("trap cleanup EXIT", shell)
        self.assertNotIn("-p 5432", shell)
        self.assertIn("run_r05_database_invariants.sh", shell)


if __name__ == "__main__":
    unittest.main()
