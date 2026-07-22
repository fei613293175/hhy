from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCREEN = (
    ROOT
    / "apps/android/feature/app-promotion/src/main/java/cc/orbexa/hhy/apppromotion/R09AppScreens.kt"
)
STATE = (
    ROOT
    / "apps/android/feature/app-promotion/src/main/java/cc/orbexa/hhy/apppromotion/R09AppState.kt"
)


class R09AndroidUiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = SCREEN.read_text(encoding="utf-8")
        self.state = STATE.read_text(encoding="utf-8")

    def test_editor_uses_business_choice_controls_not_raw_code_inputs(self) -> None:
        self.assertIn('"TOOLS" to "实用工具"', self.source)
        self.assertIn('"ANDROID" to "Android"', self.source)
        self.assertIn("options = appCategories", self.source)
        self.assertIn("options = appPlatforms", self.source)
        self.assertIn("unknownLabel = ::appCategoryLabel", self.source)
        self.assertNotIn('FormField("App分类", form.categoryCode', self.source)
        self.assertNotIn('FormField("支持平台", form.platform', self.source)

    def test_unknown_codes_are_never_used_as_visible_labels(self) -> None:
        self.assertIn('else -> value.takeIf { raw -> raw.any', self.state)
        self.assertIn('?: "其他应用"', self.state)
        self.assertIn('?: "其他平台"', self.state)


if __name__ == "__main__":
    unittest.main()
