import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "run_r13_staging_acceptance.sh"
COMPOSE = ROOT / "infra" / "staging" / "r13-smoke" / "docker-compose.yml"


class R13StagingAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.runner = RUNNER.read_text(encoding="utf-8")
        cls.compose = COMPOSE.read_text(encoding="utf-8")

    def test_empty_project_guard_precedes_compose_start(self) -> None:
        start = self.runner.index("start_isolated_stack() {")
        end = self.runner.index("\n}\n", start)
        function = self.runner[start:end]

        self.assertLess(
            function.index("R13_STAGING_PROJECT_NOT_EMPTY"),
            function.index("${COMPOSE[@]} up -d --build"),
        )
        self.assertIn("existing=$(${COMPOSE[@]} ps -aq)", function)

    def test_entrypoint_binds_exact_commit_before_start(self) -> None:
        commit_check = self.runner.rindex(
            'test "$(git rev-parse HEAD)" = "$FROZEN_COMMIT"'
        )
        stack_start = self.runner.index("\nstart_isolated_stack\n", commit_check)
        baseline = self.runner.index("\ncapture_baseline\n", stack_start)
        self.assertLess(commit_check, stack_start)
        self.assertLess(stack_start, baseline)

    def test_isolated_network_and_ports_are_consistent(self) -> None:
        for text in (self.compose, self.runner):
            self.assertIn("172.31.239.0/24", text)
        for suffix in ("10", "20", "40"):
            self.assertIn(f"172.31.239.{suffix}", self.compose)
        self.assertIn("HHY_R13_SMOKE_HTTP_PORT:-38113", self.compose)
        self.assertIn("HHY_R13_PROMETHEUS_PORT:-39616", self.compose)
        self.assertIn("HHY_R13_ALERTMANAGER_PORT:-39617", self.compose)

    def test_rollback_preserves_v042_activity_facts(self) -> None:
        self.assertIn("hhy-backend-r13-baseline:c9741759", self.runner)
        self.assertIn('test "$(sed -n \'1p\' "$EVIDENCE/database.txt")" = "042"', self.runner)
        self.assertIn("record_activity_facts", self.runner)
        self.assertIn("activity-facts-before.txt", self.runner)
        self.assertIn("activity-facts-after.txt", self.runner)
        self.assertIn("activity_facts_preserved=PASS", self.runner)
        self.assertNotIn("record_publishing_facts", self.runner)

    def test_alert_probe_uses_non_content_aggregate_and_legal_state_machine(self) -> None:
        self.assertIn("'R13_ACTIVITY'", self.runner)
        self.assertIn("'content.r13.stage.alert.v1'", self.runner)
        self.assertIn("status='PUBLISHING', attempts=attempts + 1", self.runner)
        self.assertIn("status='PUBLISHED', published_at=CURRENT_TIMESTAMP", self.runner)


if __name__ == "__main__":
    unittest.main()
