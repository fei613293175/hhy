from __future__ import annotations

from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]


class R14StagingReleaseScopeTest(unittest.TestCase):
    def test_compose_pins_flyway_before_the_application_starts(self) -> None:
        compose = yaml.safe_load(
            (ROOT / "infra/staging/r14-smoke/docker-compose.yml").read_text(encoding="utf-8")
        )
        environment = compose["services"]["api"]["environment"]
        self.assertEqual("${HHY_R14_FLYWAY_TARGET:-44}", environment["SPRING_FLYWAY_TARGET"])

    def test_acceptance_requires_r14_schema_even_when_future_migrations_exist(self) -> None:
        script = (ROOT / "scripts/run_r14_staging_acceptance.sh").read_text(encoding="utf-8")
        self.assertIn("EXPECTED_FLYWAY_VERSION=044", script)
        self.assertIn("export HHY_R14_FLYWAY_TARGET=${HHY_R14_FLYWAY_TARGET:-44}", script)
        self.assertIn('test "${HHY_R14_FLYWAY_TARGET:-44}" = 44', script)
        self.assertIn('= "$EXPECTED_FLYWAY_VERSION"', script)
        self.assertNotIn("EXPECTED_FLYWAY_VERSION=047", script)


if __name__ == "__main__":
    unittest.main()
