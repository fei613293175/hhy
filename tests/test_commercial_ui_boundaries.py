from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from check_commercial_ui_boundaries import production_sources, violations  # noqa: E402

class CommercialUiBoundariesTest(unittest.TestCase):
    def test_android_instrumentation_sources_are_not_production_ui(self) -> None:
        self.assertFalse(any("androidTest" in path.parts for path in production_sources()))

    def test_production_ui_does_not_render_technical_diagnostics(self) -> None:
        self.assertEqual(violations(), [])

if __name__ == "__main__":
    unittest.main()
