from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]


class R16EntryContractTest(unittest.TestCase):
    def test_current_client_and_admin_contracts_pass(self) -> None:
        result = subprocess.run(
            [sys.executable, "scripts/check_r16_entry_contract.py"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            encoding="utf-8",
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('"status": "PASS"', result.stdout)


if __name__ == "__main__":
    unittest.main()
