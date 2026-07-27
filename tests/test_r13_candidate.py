from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/prepare_r13_ci_fixture.sh"
JOURNEY = ROOT / "apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt"
VISUAL_MANIFEST = ROOT / "tests/android/visual-manifests/R13.yaml"
BUILD = ROOT / "apps/android/app/build.gradle.kts"
RELEASE_POLICY = ROOT / "apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt"
VERSION_TEST = ROOT / "apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt"
REQUEST = ROOT / "config/android-candidate-request.yaml"
ACTIVITY_SCREEN = ROOT / "apps/android/feature/activity/src/main/java/cc/orbexa/hhy/activity/R13ActivityScreens.kt"
PROJECT_SCREEN = ROOT / "apps/android/feature/project/src/main/java/cc/orbexa/hhy/project/R08ProjectScreens.kt"


class R13CandidateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = FIXTURE.read_text(encoding="utf-8")
        self.journey = JOURNEY.read_text(encoding="utf-8")

    def test_fixture_is_isolated_idempotent_and_v042_only(self) -> None:
        self.assertIn("HHY_R13_CI_FIXTURE_CONFIRM", self.fixture)
        self.assertIn("hhy-r13-ci-candidate-*", self.fixture)
        self.assertIn("hhy-r13-staging-postgres-1", self.fixture)
        self.assertIn("hhy-r13-staging-*-postgres-1", self.fixture)
        self.assertIn("SPRING_PROFILES_ACTIVE", self.fixture)
        self.assertIn("HHY_CI_AUTOMATION_ENABLED", self.fixture)
        self.assertIn("version='042'", self.fixture)
        self.assertIn("pg_advisory_xact_lock(709013)", self.fixture)
        self.assertIn("ON CONFLICT (phone) DO NOTHING", self.fixture)
        self.assertNotIn("ON CONFLICT (phone) DO UPDATE", self.fixture)
        self.assertNotIn("environment='PROD'", self.fixture)
        self.assertNotIn("'official','PROD'", self.fixture)
        self.assertNotIn('echo "$phone"', self.fixture)

    def test_fixture_uses_only_legal_versioned_content_transitions(self) -> None:
        for edge in (
            "'DRAFT','PENDING_REVIEW'",
            "'PENDING_REVIEW','REVIEWING'",
            "'REVIEWING','APPROVED'",
            "'APPROVED','ONLINE'",
        ):
            self.assertIn(edge, self.fixture)
        self.assertIn("snapshot_version_id,command_id", self.fixture)
        self.assertIn("transition_version", self.fixture)
        self.assertIn("'DRAFT',NULL,0,0", self.fixture)
        self.assertNotIn("'ONLINE','APPROVED',0,0", self.fixture)

    def test_fixture_builds_real_activity_and_startup_facts_without_secrets(self) -> None:
        for fact in (
            "R13候选协作项目",
            "content_favorites",
            "content_view_logs",
            "traffic_type='ORGANIC_TRAFFIC'",
            "content_contacts",
            "channel='LINK'",
            "content_media",
            "media_objects",
            "status='READY'",
            "'official','STAGING'",
            "'1.2.2',10222,'NONE','PUBLISHED'",
            "timestamptz '2026-07-26 00:00:00+00'",
            "published_at > clock_timestamp()",
            "release.published_at<=clock_timestamp()",
            "R13_CI_FIXTURE_OK",
            "count(DISTINCT content.id)=3",
            "count(DISTINCT favorite.id)=3",
            "count(DISTINCT history.id)=3",
        ):
            self.assertIn(fact, self.fixture)
        self.assertNotIn("value_cipher||", self.fixture)
        self.assertNotIn("candidate.phone||", self.fixture)

    def test_journey_captures_exactly_four_r13_surfaces(self) -> None:
        captures = (
            'captureStable("01-favorites.png")',
            'captureStable("02-history.png")',
            'captureStable("03-share-sheet.png")',
            'captureStable("04-invalid-feedback-sheet.png")',
        )
        self.assertEqual(4, self.journey.count('captureStable("'))
        for capture in captures:
            self.assertIn(capture, self.journey)
        for marker in ("hhy.sheet.r13.share", "hhy.sheet.r13.invalid-feedback"):
            self.assertIn(marker, self.journey)
        self.assertIn('resource = "mine.favorites"', self.journey)
        self.assertIn('resource = "mine.history"', self.journey)
        self.assertIn('mode = "favorites"', self.journey)
        self.assertIn('mode = "history"', self.journey)
        self.assertEqual(2, self.journey.count("navigateToR13Content(\n            resource"))
        self.assertIn('clickExactText(activityTargetTitle)', self.journey)
        self.assertIn('clickResource("r13.action.project.share")', self.journey)
        self.assertIn('clickResource("r13.action.project.invalid-feedback")', self.journey)
        self.assertIn("generateSequence(textNode) { current -> current.parent }", self.journey)
        self.assertIn("generateSequence(resourceNode) { current -> current.parent }", self.journey)
        self.assertIn(".firstOrNull { it.isClickable && it.isEnabled }", self.journey)
        resource_helper = self.journey[
            self.journey.index("private fun clickResource"):
            self.journey.index("private fun scrollUntilResource")
        ]
        self.assertIn('error("Cannot find clickable UI ancestor for resource: $value")', resource_helper)
        self.assertNotIn("resourceNode.click()", resource_helper)
        navigation_helper = self.journey[
            self.journey.index("private fun navigateToR13Content"):
            self.journey.index("private fun scrollUntilResource")
        ]
        self.assertIn("SystemClock.uptimeMillis() + 30_000", navigation_helper)
        self.assertEqual(2, navigation_helper.count("minOf(3_000, remaining(deadline))"))
        self.assertIn('phase in setOf("loading", "refreshing", "appending")', navigation_helper)
        self.assertIn('phase != "content"', navigation_helper)
        self.assertIn('device.hasObject(By.res("hhy.screen.r13.$mode.$phase"))', navigation_helper)
        self.assertIn('"loading", "content", "empty"', navigation_helper)
        self.assertIn('sourceVisible=${device.hasObject(By.res(source))}', navigation_helper)
        self.assertIn('resourceVisible=${device.hasObject(By.res(resource))}', navigation_helper)
        self.assertIn('diagnostics=${clickDiagnostics.joinToString(" | ")}', navigation_helper)
        self.assertIn('"partial_error"', navigation_helper)
        self.assertIn('"offline"', navigation_helper)
        self.assertIn('"not_found"', navigation_helper)
        self.assertNotIn("SystemClock.sleep", navigation_helper)
        self.assertIn('clickExactText("再检查一下")', self.journey)
        self.assertNotIn("authenticatedR12PagesProduceBoundVisualEvidence", self.journey)

    def test_project_detail_exposes_stable_resources_for_candidate_actions(self) -> None:
        source = PROJECT_SCREEN.read_text(encoding="utf-8")
        self.assertIn('Modifier.testTag("r13.action.project.share")', source)
        self.assertIn('Modifier.testTag("r13.action.project.invalid-feedback")', source)

    def test_favorites_waits_for_all_real_media_before_visual_capture(self) -> None:
        source = ACTIVITY_SCREEN.read_text(encoding="utf-8")
        self.assertIn('val mediaWidthPx = with(density)', source)
        self.assertIn('val mediaHeightPx = with(density)', source)
        self.assertIn('ImageRequest.Builder(context)', source)
        self.assertIn('.size(Size(mediaWidthPx, mediaHeightPx))', source)
        self.assertIn('val mediaRequest = remember(mediaUrl, context, mediaWidthPx, mediaHeightPx)', source)
        self.assertIn('val mediaPainter = rememberAsyncImagePainter(model = mediaRequest)', source)
        self.assertIn('mediaPainter.state is AsyncImagePainter.State.Success -> "loaded"', source)
        self.assertIn('mediaPainter.state is AsyncImagePainter.State.Error -> "error"', source)
        self.assertIn('if (mediaRequest != null)', source)
        self.assertIn('painter = mediaPainter', source)
        self.assertIn('testTag("r13.media.${item.id}.$mediaState")', source)
        self.assertIn('Modifier.fillMaxSize().testTag("r13.${mode.tag}.list")', source)
        self.assertNotIn('onSuccess = { mediaState = "loaded" }', source)
        self.assertNotIn('onError = { mediaState = "error" }', source)
        self.assertNotIn('var mediaState by remember(mediaUrl)', source)
        for description in (
            '"loaded" -> "内容图片：$mediaDescription"',
            '"error" -> "内容图片加载失败：${item.title}"',
            'else -> "内容图片加载中：${item.title}"',
        ):
            self.assertIn(description, source)
        wait = 'prepareLoadedMediaForCapture(expectedCount = 3)'
        capture = 'captureStable("01-favorites.png")'
        self.assertIn(wait, self.journey)
        self.assertIn('By.desc(Pattern.compile("内容图片：.+"))', self.journey)
        self.assertIn('By.desc(Pattern.compile("内容图片加载失败：.+"))', self.journey)
        self.assertIn('By.desc(Pattern.compile("内容图片加载中：.+"))', self.journey)
        self.assertIn('assertTrue("Candidate media failed to load: errors=$failedCount", failedCount == 0)', self.journey)
        self.assertIn('val list = device.findObject(By.res("r13.favorites.list"))', self.journey)
        self.assertIn('val bounds = list.visibleBounds', self.journey)
        self.assertIn('val verticalInset = maxOf(24, bounds.height() / 8)', self.journey)
        self.assertIn('assertTrue("R13 favorites list has no safe swipe area: $bounds", bottomY > topY)', self.journey)
        self.assertIn('val activatedDescriptions = mutableSetOf<String>()', self.journey)
        self.assertIn('activatedDescriptions += device.findObjects(loaded).mapNotNull { it.contentDescription }', self.journey)
        self.assertIn('device.swipe(centerX, bottomY, centerX, topY, 24)', self.journey)
        self.assertIn('device.swipe(centerX, topY, centerX, bottomY, 24)', self.journey)
        self.assertIn('"Candidate media activation did not finish: expected=$expectedCount "', self.journey)
        self.assertIn('"Candidate media was not ready at list start: expected=$expectedCount "', self.journey)
        self.assertIn('assertTrue("Candidate media failed after returning to list start: errors=$failedCount"', self.journey)
        self.assertIn('if (loadedCount == expectedCount && loadingCount == 0)', self.journey)
        self.assertIn('"observedSuccess=${activatedDescriptions.size} "', self.journey)
        self.assertIn('"visibleSuccess=${device.findObjects(loaded).size} "', self.journey)
        self.assertIn('"success=${device.findObjects(loaded).size} "', self.journey)
        self.assertIn('"errors=${device.findObjects(failed).size} "', self.journey)
        self.assertIn('"loading=${device.findObjects(loading).size}"', self.journey)
        self.assertIn('val deadline = SystemClock.uptimeMillis() + 30_000', self.journey)
        self.assertEqual(2, self.journey.count('val deadline = SystemClock.uptimeMillis() + 30_000'))
        self.assertNotIn('prepareLoadedMediaForCapture(expectedCount = 2)', self.journey)
        self.assertNotIn('SystemClock.sleep(250)', self.journey)
        media_helper = self.journey[self.journey.index('private fun prepareLoadedMediaForCapture'):]
        self.assertEqual(1, media_helper.count('val deadline = SystemClock.uptimeMillis() + 30_000'))
        self.assertNotIn('By.scrollable(true)', media_helper)
        self.assertNotIn('By.res(Pattern.compile("${Pattern.quote(prefix)}', self.journey)
        self.assertLess(self.journey.index(wait), self.journey.index(capture))

    def test_visual_manifest_matches_the_four_frozen_r13_surfaces(self) -> None:
        manifest = yaml.safe_load(VISUAL_MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual("R13", manifest["release"])
        self.assertEqual("AI_IMPLEMENTATION_AGENT", manifest["review_authority"])
        self.assertEqual(4, len(manifest["screens"]))
        self.assertEqual(
            {"SCR-FAV-001", "SCR-HIS-001", "SHEET-SHARE-001", "SHEET-CONTENT-INVALID-001"},
            {row["screen_id"] for row in manifest["screens"]},
        )
        self.assertEqual(
            {capture.split('"')[1] for capture in (
                'captureStable("01-favorites.png")',
                'captureStable("02-history.png")',
                'captureStable("03-share-sheet.png")',
                'captureStable("04-invalid-feedback-sheet.png")',
            )},
            {row["file"] for row in manifest["screens"]},
        )
        for row in manifest["screens"]:
            self.assertIn("请求编号", row["forbidden_text"])
            self.assertIn("TraceId", row["forbidden_text"])
            self.assertIn("PROJECT", row["forbidden_text"])
            self.assertNotIn("APP", row["forbidden_text"])

    def test_candidate_identity_and_request_are_new_and_monotonic(self) -> None:
        self.assertIn("versionCode = 10222", BUILD.read_text(encoding="utf-8"))
        self.assertIn("VERSION_CODE: Int = 10222", RELEASE_POLICY.read_text(encoding="utf-8"))
        self.assertIn(
            '"R13 test APK versionCode must remain monotonic", 10222',
            VERSION_TEST.read_text(encoding="utf-8"),
        )
        request = yaml.safe_load(REQUEST.read_text(encoding="utf-8"))
        self.assertEqual("R13", request["release"])
        self.assertTrue(request["candidate"])
        self.assertEqual(12, request["remediation_attempt"])
        self.assertEqual("R13-CANDIDATE-20260727-012", request["request_id"])
        self.assertEqual("CR-0392", request["attempt_exception_id"])
        self.assertEqual("10c904b02750641892e35616f162319867e7f0c9", request["required_fix_commit"])

    def test_tabs_and_sheet_markers_follow_the_frozen_visual_contract(self) -> None:
        source = ACTIVITY_SCREEN.read_text(encoding="utf-8")
        tabs = source[source.index("private fun R13CategoryTabs"):source.index("private fun R13ActivityRow")]
        sheet = source[source.index("fun R13ContentActionSheet"):source.index("private data class R13ShareChannel")]
        self.assertNotIn("FilterChip(", tabs)
        self.assertIn("HhyColors.BrandPrimary", tabs)
        self.assertIn("HhyColors.BrandPrimary.copy(alpha = 0f)", tabs)
        self.assertNotIn("Color.Transparent", source)
        self.assertNotIn("androidx.compose.ui.graphics.Color", source)
        self.assertIn("FontWeight.Bold", tabs)
        self.assertIn("testTagsAsResourceId = true", sheet)
        self.assertIn("hhy.sheet.r13.", sheet)


if __name__ == "__main__":
    unittest.main()
