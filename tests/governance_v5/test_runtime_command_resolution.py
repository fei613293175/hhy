from __future__ import annotations

import unittest

from tools.governance.gov50.util import resolve_runtime_command


class RuntimeCommandResolutionTest(unittest.TestCase):
    def test_bare_python3_prefers_hhy_python(self) -> None:
        env = {"HHY_PYTHON": "C:/verified/python.exe", "PYTHON": "C:/other/python.exe"}

        self.assertEqual(
            ["C:/verified/python.exe", "scripts/check_api_contract.py"],
            resolve_runtime_command(["python3", "scripts/check_api_contract.py"], env),
        )

    def test_bare_python_uses_python_fallback(self) -> None:
        self.assertEqual(
            ["C:/fallback/python.exe", "-m", "unittest"],
            resolve_runtime_command(
                ["python", "-m", "unittest"],
                {"PYTHON": "C:/fallback/python.exe"},
            ),
        )

    def test_explicit_interpreter_and_other_commands_are_unchanged(self) -> None:
        env = {"HHY_PYTHON": "C:/verified/python.exe"}

        self.assertEqual(
            ["C:/explicit/python.exe", "script.py"],
            resolve_runtime_command(["C:/explicit/python.exe", "script.py"], env),
        )
        self.assertEqual(
            ["git", "status", "--short"],
            resolve_runtime_command(["git", "status", "--short"], env),
        )


if __name__ == "__main__":
    unittest.main()
