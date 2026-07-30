from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]


class OwnerTestEnvironmentTest(unittest.TestCase):
    def setUp(self) -> None:
        self.config = yaml.safe_load(
            (ROOT / "config/owner-test-environment.yaml").read_text(encoding="utf-8")
        )
        self.restore = (
            ROOT / "scripts/restore_android_candidate_route.sh"
        ).read_text(encoding="utf-8")
        self.promote = (
            ROOT / "scripts/promote_owner_test_backend.sh"
        ).read_text(encoding="utf-8")

    def test_public_owner_test_database_is_persistent_and_not_candidate(self) -> None:
        active = self.config["active"]
        self.assertEqual("hhy-owner-test-postgres", active["database_container"])
        self.assertEqual("hhy-owner-test-postgres-data", active["database_volume"])
        self.assertEqual("hhy-owner-test", active["docker_network"])
        self.assertNotIn("smoke", active["database_name"])
        self.assertNotIn("candidate", active["database_name"])
        self.assertEqual("FORWARD_ONLY", self.config["persistence"]["migration_mode"])
        self.assertTrue(self.config["persistence"]["forbid_database_recreate"])
        self.assertTrue(self.config["persistence"]["forbid_volume_replace"])

    def test_candidate_route_must_restore_on_pass_and_failure(self) -> None:
        separation = self.config["candidate_separation"]
        self.assertEqual("ALWAYS", separation["restore_owner_test_route_after_candidate"])
        self.assertTrue(separation["restore_on_candidate_pass"])
        self.assertTrue(separation["restore_on_candidate_failure"])
        self.assertEqual(45, separation["maximum_route_lease_minutes"])
        self.assertIn("HHY_OWNER_TEST_ROUTE_CONFIRM", self.restore)
        self.assertIn("HHY_CI_AUTOMATION_ENABLED", self.restore)
        self.assertIn("hhy-owner-test-postgres-data", self.restore)
        self.assertIn("ANDROID_OWNER_TEST_ROUTE_RESTORED", self.restore)

    def test_promotion_is_application_only_with_snapshot_and_count_guard(self) -> None:
        self.assertIn("HHY_OWNER_TEST_PROMOTION_CONFIRM", self.promote)
        self.assertIn("pg_dump", self.promote)
        self.assertIn("before_counts", self.promote)
        self.assertIn('[[ "$after_counts" == "$before_counts" ]]', self.promote)
        self.assertIn("HHY_CI_AUTOMATION_ENABLED", self.promote)
        self.assertNotIn("down -v", self.promote)
        self.assertNotIn("DROP DATABASE", self.promote.upper())
        self.assertNotIn("TRUNCATE", self.promote.upper())


if __name__ == "__main__":
    unittest.main()
