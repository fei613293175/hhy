import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "run_r09_staging_acceptance.sh"
COMPOSE = ROOT / "infra" / "staging" / "r09-smoke" / "docker-compose.yml"


class R09StagingAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.runner = RUNNER.read_text(encoding="utf-8")
        cls.compose = COMPOSE.read_text(encoding="utf-8")

    def test_empty_project_guard_precedes_compose_start(self) -> None:
        function_start = self.runner.index("start_isolated_stack() {")
        function_end = self.runner.index("\n}\n", function_start)
        function = self.runner[function_start:function_end]

        guard = function.index("R09_STAGING_PROJECT_NOT_EMPTY")
        compose_start = function.index("${COMPOSE[@]} up -d --build")
        self.assertLess(guard, compose_start)
        self.assertIn("existing=$(${COMPOSE[@]} ps -aq)", function)
        self.assertIn("R09_STAGING_BUILD_AND_START", function)
        self.assertIn("R09_STAGING_CONTAINERS_STARTED", function)

    def test_entrypoint_starts_stack_before_capturing_baseline(self) -> None:
        frozen_commit_check = self.runner.rindex(
            'test "$(git rev-parse HEAD)" = "$FROZEN_COMMIT"'
        )
        stack_start = self.runner.index("\nstart_isolated_stack\n", frozen_commit_check)
        baseline = self.runner.index("\ncapture_baseline\n", stack_start)
        self.assertLess(frozen_commit_check, stack_start)
        self.assertLess(stack_start, baseline)

    def test_default_subnet_and_static_addresses_share_r09_range(self) -> None:
        self.assertIn("HHY_R09_SMOKE_SUBNET:-172.31.243.0/24", self.compose)
        for suffix in ("10", "20", "40"):
            self.assertIn(f"172.31.243.{suffix}", self.compose)
        self.assertIn("HHY_R09_SMOKE_SUBNET:-172.31.243.0/24", self.runner)
        self.assertNotIn("172.31.249.", self.compose)
        self.assertNotIn("172.31.249.", self.runner)

    def test_prometheus_port_uses_verified_free_host_binding(self) -> None:
        self.assertIn("HHY_R09_PROMETHEUS_PORT:-39597", self.compose)
        self.assertIn("HHY_R09_PROMETHEUS_PORT:-39597", self.runner)
        self.assertNotIn("HHY_R09_PROMETHEUS_PORT:-39593", self.compose)
        self.assertNotIn("HHY_R09_PROMETHEUS_PORT:-39593", self.runner)

    def test_baseline_waits_for_first_successful_prometheus_scrape(self) -> None:
        wait_function = self.runner.index("wait_prometheus_target() {")
        target_diagnostic = self.runner.index("R09_PROMETHEUS_TARGET_NOT_UP", wait_function)
        capture = self.runner.index("capture_baseline() {")
        target_wait = self.runner.index("\n  wait_prometheus_target\n", capture)
        target_capture = self.runner.index('prometheus-targets.json"', target_wait)
        self.assertLess(wait_function, target_diagnostic)
        self.assertLess(target_wait, target_capture)
        self.assertIn("R09_STAGE_CAPTURE_BASELINE_OK", self.runner)


if __name__ == "__main__":
    unittest.main()
