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

    def test_real_app_media_is_rendered_across_list_detail_and_editor(self) -> None:
        self.assertIn("implementation(libs.coil.compose)", (
            ROOT / "apps/android/feature/app-promotion/build.gradle.kts"
        ).read_text(encoding="utf-8"))
        self.assertIn("coil.compose.AsyncImage", self.source)
        self.assertIn("media = item.media.firstOrNull()", self.source)
        self.assertIn("if (item.media.isNotEmpty()) item { AppMediaGallery(item) }", self.source)
        self.assertIn('testTag("r09.app.media.detail")', self.source)
        self.assertIn('testTag("r09.app.media.editor")', self.source)
        self.assertIn("mediaPreviews = result.data.media.map(AppMediaPreview::from)", self.source)
        self.assertIn("mediaPreviews = selections.map(AppMediaPreview::from)", self.source)
        self.assertIn("form = form.copy(mediaIds = selections.map { it.mediaId })", self.source)

    def test_editor_choices_wrap_without_horizontal_truncation(self) -> None:
        self.assertIn("FlowRow(", self.source)
        self.assertIn("maxItemsInEachRow = 3", self.source)


if __name__ == "__main__":
    unittest.main()
