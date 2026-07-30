from __future__ import annotations

from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from select_execution_profile import load_policy, select_profile  # noqa: E402


class ModelRoutingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = load_policy()

    def test_complex_and_medium_route_to_required_models(self) -> None:
        self.assertEqual("Sol", select_profile("complex", {"sol", "terra"}, self.policy)["selected_model"])
        self.assertEqual("Terra", select_profile("medium", {"sol", "terra"}, self.policy)["selected_model"])

    def test_light_uses_luna_when_runtime_advertises_it(self) -> None:
        result = select_profile("light", {"sol", "terra", "luna"}, self.policy)
        self.assertEqual("Luna", result["selected_model"])
        self.assertEqual("ROUTED", result["audit_status"])

    def test_unavailable_luna_is_explicit_terra_low_fallback(self) -> None:
        result = select_profile("light", {"sol", "terra"}, self.policy)
        self.assertEqual("Luna", result["requested_model"])
        self.assertEqual("Terra", result["selected_model"])
        self.assertEqual("low", result["reasoning_effort"])
        self.assertEqual("LUNA_UNAVAILABLE_FALLBACK", result["audit_status"])
        self.assertNotEqual(result["requested_model"], result["selected_model"])


if __name__ == "__main__":
    unittest.main()
