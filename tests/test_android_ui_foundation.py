import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "check_android_ui_foundation.py"
SPEC = importlib.util.spec_from_file_location("check_android_ui_foundation", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class AndroidUiFoundationGateTest(unittest.TestCase):
    def test_repository_passes_foundation_gate(self):
        self.assertEqual([], MODULE.find_violations(ROOT))

    def test_text_icon_and_route_state_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "apps/android/feature/sample/src/main/Sample.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                'val icon = "相"\nval route = mutableStateOf<SampleRoute>(SampleRoute.Home)\n',
                encoding="utf-8",
            )
            (root / "apps/android/gradle").mkdir(parents=True)
            (root / "apps/android/gradle/libs.versions.toml").write_text('navigation = "2.9.8"\n', encoding="utf-8")
            violations = MODULE.find_violations(root)
            self.assertTrue(any("forbidden text/Unicode icon" in value for value in violations))
            self.assertTrue(any("Jetpack Navigation back stack" in value for value in violations))

    def test_auth_route_state_and_directional_peer_motion_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "apps/android/feature/auth/src/main/java/cc/orbexa/hhy/auth/AuthScreen.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "var route by remember { mutableStateOf(AuthRoute.PASSWORD) }\n"
                "val motion = HhyMotion.forwardContent()\n",
                encoding="utf-8",
            )
            (root / "apps/android/gradle").mkdir(parents=True)
            (root / "apps/android/gradle/libs.versions.toml").write_text('navigation = "2.9.8"\n', encoding="utf-8")
            violations = MODULE.find_violations(root)
            self.assertTrue(any("auth destinations must use" in value for value in violations))
            self.assertTrue(any("peer login tabs must not use" in value for value in violations))


if __name__ == "__main__":
    unittest.main()
