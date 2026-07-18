from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from check_commercial_ui_boundaries import violations  # noqa: E402

class CommercialUiBoundariesTest(unittest.TestCase):
    def test_production_ui_does_not_render_technical_diagnostics(self) -> None:
        self.assertEqual(violations(), [])

if __name__ == "__main__":
    unittest.main()
