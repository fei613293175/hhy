from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def load_script(name: str):
    path = ROOT / "scripts" / f"{name}.py"
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


release_slice = load_script("generate_release_slice")
generated_assets = load_script("check_generated_assets")


class ReleaseSliceTests(unittest.TestCase):
    def test_r02_is_deterministic_and_has_expected_scope_counts(self) -> None:
        first = release_slice.build_release_slice("R02")
        second = release_slice.build_release_slice("R02")
        self.assertEqual(first, second)
        self.assertEqual(20, first["counts"]["catalog_release_exact_operations"])
        self.assertEqual(27, first["counts"]["story_unique_operations"])
        self.assertEqual(9, first["counts"]["stories"])
        self.assertEqual(14, first["counts"]["story_unique_pages"])
        self.assertEqual(
            27, len({item["operation_id"] for item in first["story_unique_operation_details"]})
        )

    def test_json_and_yaml_rendering_are_deterministic(self) -> None:
        payload = release_slice.build_release_slice("R02")
        json_first = release_slice.render_release_slice(payload, ".json")
        json_second = release_slice.render_release_slice(payload, ".json")
        yaml_first = release_slice.render_release_slice(payload, ".yaml")
        yaml_second = release_slice.render_release_slice(payload, ".yaml")
        self.assertEqual(json_first, json_second)
        self.assertEqual(yaml_first, yaml_second)
        self.assertEqual("R02", json.loads(json_first)["release"])

    def test_check_mode_detects_release_slice_drift(self) -> None:
        payload = release_slice.build_release_slice("R02")
        content = release_slice.render_release_slice(payload, ".json")
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "r02-slice.json"
            output.write_text(content, encoding="utf-8")
            self.assertTrue(release_slice.write_or_check(output, content, check=True))
            output.write_text("{}\n", encoding="utf-8")
            self.assertFalse(release_slice.write_or_check(output, content, check=True))


class GeneratedAssetTests(unittest.TestCase):
    def test_compare_file_detects_drift_and_missing_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expected = root / "expected.txt"
            actual = root / "actual.txt"
            expected.write_text("expected\n", encoding="utf-8")
            self.assertIn("missing", generated_assets.compare_file(expected, actual, "fixture") or "")
            actual.write_text("actual\n", encoding="utf-8")
            self.assertIn("drift", generated_assets.compare_file(expected, actual, "fixture") or "")
            actual.write_text("expected\n", encoding="utf-8")
            self.assertIsNone(generated_assets.compare_file(expected, actual, "fixture"))


if __name__ == "__main__":
    unittest.main()
