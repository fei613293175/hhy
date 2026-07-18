from __future__ import annotations

from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]


class ContextPackParallelPolicyTest(unittest.TestCase):
    def test_context_pack_carries_canonical_parallel_policy(self) -> None:
        policy = yaml.safe_load((ROOT / ".continuity/CONTINUITY_POLICY.yaml").read_text(encoding="utf-8"))
        context = yaml.safe_load((ROOT / "artifacts/context/CURRENT_CONTEXT_PACK.yaml").read_text(encoding="utf-8"))
        self.assertEqual(policy["parallel_development"], context["parallel_development_policy"])

    def test_export_templates_match_root_entrypoints(self) -> None:
        self.assertEqual((ROOT / "AGENTS.md").read_bytes(), (ROOT / "templates/AGENTS.md").read_bytes())
        self.assertEqual((ROOT / "START_HERE.md").read_bytes(), (ROOT / "templates/START_HERE.md").read_bytes())

    def test_context_sources_include_parallel_plans(self) -> None:
        context = yaml.safe_load((ROOT / "artifacts/context/CURRENT_CONTEXT_PACK.yaml").read_text(encoding="utf-8"))
        sources = {row["path"] for row in context["source_manifest"]}
        self.assertIn("releases/PROGRAM_EXECUTION_PLAN.yaml", sources)
        release = context["active_session"]["release"]
        self.assertIn(f"releases/{release}/PARALLEL_EXECUTION_PLAN.yaml", sources)

    def test_context_carries_runtime_and_transport_policy(self) -> None:
        context = yaml.safe_load((ROOT / "artifacts/context/CURRENT_CONTEXT_PACK.yaml").read_text(encoding="utf-8"))
        runtime = yaml.safe_load((ROOT / "config/DEVELOPMENT_RUNTIME.yaml").read_text(encoding="utf-8"))
        transport = yaml.safe_load((ROOT / "config/REPOSITORY_TRANSPORT.yaml").read_text(encoding="utf-8"))
        self.assertEqual(runtime, context["development_runtime"])
        self.assertEqual(transport, context["repository_transport"])
        self.assertEqual(runtime["model_routing"], context["execution_routing_policy"])
        self.assertEqual(
            "CODEX_ALREADY_CONNECTED_UNLESS_USER_DECLARES_DISCONNECTED",
            context["development_runtime"]["cloud_environment"]["default_assumption"],
        )

    def test_context_sources_include_runtime_and_transport_descriptors(self) -> None:
        context = yaml.safe_load((ROOT / "artifacts/context/CURRENT_CONTEXT_PACK.yaml").read_text(encoding="utf-8"))
        sources = {row["path"] for row in context["source_manifest"]}
        self.assertIn("config/DEVELOPMENT_RUNTIME.yaml", sources)
        self.assertIn("config/REPOSITORY_TRANSPORT.yaml", sources)


if __name__ == "__main__":
    unittest.main()
