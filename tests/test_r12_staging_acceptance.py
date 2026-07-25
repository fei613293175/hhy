import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "run_r12_staging_acceptance.sh"
COMPOSE = ROOT / "infra" / "staging" / "r12-smoke" / "docker-compose.yml"


class R12StagingAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.runner = RUNNER.read_text(encoding="utf-8")
        cls.compose = COMPOSE.read_text(encoding="utf-8")

    def test_empty_project_guard_precedes_compose_start(self) -> None:
        start = self.runner.index("start_isolated_stack() {")
        end = self.runner.index("\n}\n", start)
        function = self.runner[start:end]

        self.assertLess(
            function.index("R12_STAGING_PROJECT_NOT_EMPTY"),
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
            self.assertIn("172.31.240.0/24", text)
        for suffix in ("10", "20", "40"):
            self.assertIn(f"172.31.240.{suffix}", self.compose)
        self.assertIn("HHY_R12_SMOKE_HTTP_PORT:-38112", self.compose)
        self.assertIn("HHY_R12_PROMETHEUS_PORT:-39614", self.compose)
        self.assertIn("HHY_R12_ALERTMANAGER_PORT:-39615", self.compose)

    def test_rollback_preserves_v041_publishing_facts(self) -> None:
        self.assertIn("hhy-backend-r12-baseline:5419d682", self.runner)
        self.assertIn('test "$(sed -n \'1p\' "$EVIDENCE/database.txt")" = "41"', self.runner)
        self.assertIn("record_publishing_facts", self.runner)
        self.assertIn("publishing-facts-before.txt", self.runner)
        self.assertIn("publishing-facts-after.txt", self.runner)
        self.assertIn("publishing_facts_preserved=PASS", self.runner)
        self.assertNotIn("record_team_leader_facts", self.runner)

    def test_alert_probe_uses_non_content_aggregate_and_legal_state_machine(self) -> None:
        self.assertIn("'R12_PUBLISHING'", self.runner)
        self.assertIn("'content.r12.stage.alert.v1'", self.runner)
        self.assertIn("status='PUBLISHING', attempts=attempts + 1", self.runner)
        self.assertIn("status='PUBLISHED', published_at=CURRENT_TIMESTAMP", self.runner)


if __name__ == "__main__":
    unittest.main()
