from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import sys
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from continuity_lib import portable_source_record, tree_fingerprint


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
        active_session = context.get("active_session") or {}
        release = (
            active_session.get("release")
            or (context.get("current_status") or {}).get("active_release")
            or (context.get("next_task") or {}).get("release")
        )
        self.assertTrue(release, "Context Pack必须能在活跃或关闭状态解析当前Release")
        parallel_plan = ROOT / "releases" / release / "PARALLEL_EXECUTION_PLAN.yaml"
        parallel_source = f"releases/{release}/PARALLEL_EXECUTION_PLAN.yaml"
        if parallel_plan.exists():
            self.assertIn(parallel_source, sources)
        else:
            self.assertNotIn(parallel_source, sources)

    def test_repository_fingerprint_ignores_local_apk_binaries(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "source.txt").write_text("tracked project content\n", encoding="utf-8")
            before = tree_fingerprint(root)
            apk = root / "artifacts/apk/R05/local-debug.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"local apk binary must not affect repository context")
            after = tree_fingerprint(root)
        self.assertEqual(before, after)

    def test_closed_repository_fingerprint_ignores_untracked_runner_files(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "ci@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "CI"], cwd=root, check=True)
            (root / "source.txt").write_text("tracked\n", encoding="utf-8")
            subprocess.run(["git", "add", "source.txt"], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "baseline"], cwd=root, check=True)
            before = tree_fingerprint(root)
            runner_log = root / "artifacts/validation/ci/continuity-gate.log"
            runner_log.parent.mkdir(parents=True)
            runner_log.write_text("runner-local diagnostics\n", encoding="utf-8")
            after = tree_fingerprint(root)
        self.assertEqual(before, after)

    def test_context_source_record_is_portable_across_git_line_endings(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "releases/R06/ACCEPTANCE_MATRIX.csv"
            source.parent.mkdir(parents=True)
            source.write_bytes(b"id,status\nR06,READY\n")
            lf_record = portable_source_record(root, source)
            source.write_bytes(b"id,status\r\nR06,READY\r\n")
            crlf_record = portable_source_record(root, source)
        self.assertEqual(lf_record, crlf_record)

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
