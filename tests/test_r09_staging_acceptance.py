import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "run_r09_staging_acceptance.sh"


class R09StagingAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.runner = RUNNER.read_text(encoding="utf-8")

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


if __name__ == "__main__":
    unittest.main()
