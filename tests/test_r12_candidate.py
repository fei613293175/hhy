from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/prepare_r12_ci_fixture.sh"
JOURNEY = ROOT / "apps/android/app/src/androidTest/java/cc/orbexa/hhy/ReleaseCandidateSmokeTest.kt"
VISUAL_MANIFEST = ROOT / "tests/android/visual-manifests/R12.yaml"
BUILD = ROOT / "apps/android/app/build.gradle.kts"
RELEASE_POLICY = ROOT / "apps/android/app/src/main/java/cc/orbexa/hhy/ReleasePolicy.kt"
VERSION_TEST = ROOT / "apps/android/app/src/test/java/cc/orbexa/hhy/VersionMetadataTest.kt"
REQUEST = ROOT / "config/android-candidate-request.yaml"
WORKFLOW = ROOT / ".github/workflows/android-quality-gate.yml"
CI_SERVICE = ROOT / "services/backend/access/src/main/java/cc/orbexa/hhy/access/user/CiAutomationService.java"
CI_FIXTURE_STORE = ROOT / "services/backend/access/src/main/java/cc/orbexa/hhy/access/user/CiAutomationFixtureStore.java"
CI_CONTROLLER = ROOT / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/CiAutomationController.java"


class R12CandidateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = FIXTURE.read_text(encoding="utf-8")
        self.journey = JOURNEY.read_text(encoding="utf-8")

    def test_fixture_is_isolated_to_r12_v041_candidate_staging(self) -> None:
        self.assertIn("hhy-r12-ci-candidate-*", self.fixture)
        self.assertIn("hhy-r12-staging-*-postgres-1", self.fixture)
        self.assertIn("SPRING_PROFILES_ACTIVE", self.fixture)
        self.assertIn("HHY_CI_AUTOMATION_ENABLED", self.fixture)
        self.assertIn("version='041'", self.fixture)
        self.assertIn("psql -qAt", self.fixture)
        self.assertIn("INSERT INTO hhy.users(phone,status,invite_code)", self.fixture)
        self.assertIn("ON CONFLICT (phone) DO NOTHING", self.fixture)
        self.assertNotIn("ON CONFLICT (phone) DO UPDATE", self.fixture)
        self.assertNotIn('echo "$phone"', self.fixture)
        self.assertNotIn("hhy-r11-ci-candidate-*", self.fixture)

    def test_fixture_uses_only_legal_r12_content_edges_and_review_bindings(self) -> None:
        for edge in (
            "'DRAFT','PENDING_REVIEW'",
            "'PENDING_REVIEW','REVIEWING'",
            "'REVIEWING','REJECTED'",
            "'REVIEWING','APPROVED'",
            "'APPROVED','ONLINE'",
        ):
            self.assertIn(edge, self.fixture)
        self.assertIn("snapshot_version_id,command_id", self.fixture)
        self.assertIn("transition_version", self.fixture)
        self.assertIn("status='DRAFT' AND version=0", self.fixture)
        self.assertNotIn("VALUES (\n+    p_user_id,'PROJECT',p_title,p_summary,'ONLINE'", self.fixture)
        self.assertNotIn("SET status='DRAFT'", self.fixture)
        self.assertIn("ON CONFLICT (user_id) DO UPDATE", self.fixture)
        self.assertIn("R12_CI_FIXTURE_OK", self.fixture)

    def test_fixture_contains_every_candidate_owner_fact(self) -> None:
        for fact in (
            "R12候选发布者",
            "R12候选发布预览项目",
            "R12候选内容策略草稿",
            "R12候选审核中的品牌合作",
            "R12候选未通过的渠道方案",
            "R12候选已上线的联合增长项目",
            "专业协作会员",
            "reward_accounts",
            "identity.status='VERIFIED'",
            "target_snapshot=",
            "target_media=",
        ):
            self.assertIn(fact, self.fixture)
        self.assertIn("count(DISTINCT draft.id)>=2", self.fixture)
        self.assertIn("reviewing.status='REVIEWING'", self.fixture)
        self.assertIn("rejected.status='REJECTED'", self.fixture)
        self.assertIn("online.status='ONLINE'", self.fixture)

    def test_fixture_contains_idempotent_official_staging_startup_release(self) -> None:
        for fact in (
            "INSERT INTO hhy.app_build_profiles(",
            "INSERT INTO hhy.app_build_jobs(",
            "INSERT INTO hhy.app_build_artifacts(",
            "INSERT INTO hhy.app_release_channels(",
            "INSERT INTO hhy.app_release_records(",
            "'official','STAGING'",
            "'1.2.2',10221,'NONE','PUBLISHED'",
            "min_supported_version_code IS DISTINCT FROM 10221",
            "R12 startup build job is duplicated",
            "R12 startup release record is duplicated",
            "R12 startup release record drifted",
            "|release_version=",
            "|release_update=",
        ):
            self.assertIn(fact, self.fixture)
        self.assertNotIn("'official','PROD'", self.fixture)
        self.assertNotIn("environment='PROD'", self.fixture)
        self.assertEqual(1, self.fixture.count("'official','STAGING'"))
        self.assertIn("count(DISTINCT release.id)=1", self.fixture)

    def test_oidc_bootstrap_rebuilds_consumable_r12_target_before_issuing_code(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        service = CI_SERVICE.read_text(encoding="utf-8")
        fixture_store = CI_FIXTURE_STORE.read_text(encoding="utf-8")
        controller = CI_CONTROLLER.read_text(encoding="utf-8")

        self.assertIn('"release":os.environ["ANDROID_RELEASE"]', workflow)
        self.assertIn("body.release()", controller)
        self.assertIn("prepareR12SubmitTarget(user.id())", service)
        self.assertLess(service.index("prepareR12SubmitTarget(user.id())"), service.index("store.create("))
        self.assertIn("pg_advisory_xact_lock", fixture_store)
        self.assertIn("R12候选发布预览项目", fixture_store)
        self.assertIn("'DRAFT',NULL,0,0", fixture_store)
        self.assertIn("version_no,snapshot_json,created_by", fixture_store)
        self.assertIn("content_stats", fixture_store)
        self.assertIn("content_media", fixture_store)
        self.assertIn("media.status='READY'", fixture_store)
        self.assertIn("duplicate active submit targets", fixture_store)
        self.assertIn("Banned R12 candidate submit target", fixture_store)
        self.assertNotIn("INSERT INTO hhy.content_review_records", fixture_store)
        self.assertIn('if ("R12".equals(release))', service)

    def test_journey_captures_the_exact_ten_r12_android_pages(self) -> None:
        expected = (
            'captureStable("01-publish-center.png")',
            'captureStable("02-me-home.png")',
            'captureStable("03-profile.png")',
            'captureStable("04-drafts.png")',
            'captureStable("05-content-management-detail.png")',
            'captureStable("06-publish-preview.png")',
            'captureStable("07-submit-result.png")',
            'captureStable("08-my-contents.png")',
            'captureStable("09-content-reviews.png")',
            'captureStable("10-content-analytics.png")',
        )
        for capture in expected:
            self.assertIn(capture, self.journey)
        self.assertEqual(10, self.journey.count('captureStable("'))
        for marker in (
            "hhy.screen.r12.publish.center.content",
            "hhy.screen.r12.me",
            "hhy.screen.r12.profile.content",
            "hhy.screen.r12.drafts",
            "hhy.screen.r12.content_management.detail.content",
            "hhy.screen.r12.publish.preview.content",
            "hhy.screen.r12.publish.result.success",
            "hhy.screen.r12.my-contents",
            "hhy.screen.r12.content-reviews",
            "hhy.screen.r12.content-analytics",
        ):
            self.assertIn(marker, self.journey)
        self.assertIn('clickLastExactText("确认提交")', self.journey)
        self.assertIn('clickExactText("查看我的发布")', self.journey)
        self.assertIn('scrollUntilResource("mine.my-drafts")', self.journey)
        self.assertIn('scrollUntilText("审核记录")', self.journey)
        self.assertIn('scrollUntilText("内容数据")', self.journey)
        self.assertNotIn("authenticatedR11Pages", self.journey)

    def test_visual_manifest_covers_the_exact_r12_journey(self) -> None:
        manifest = yaml.safe_load(VISUAL_MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual("R12", manifest["release"])
        self.assertEqual("AI_IMPLEMENTATION_AGENT", manifest["review_authority"])
        self.assertEqual(10, len(manifest["screens"]))
        self.assertEqual(
            {
                "SCR-PUB-001", "SCR-PUB-006", "SCR-PUB-007",
                "SCR-MYC-001", "SCR-MYC-002", "SCR-MYC-003",
                "SCR-MYC-004", "SCR-MYC-005", "SCR-ME-001", "SCR-ME-002",
            },
            {row["screen_id"] for row in manifest["screens"]},
        )
        self.assertEqual(
            {capture.split('"')[1] for capture in (
                'captureStable("01-publish-center.png")',
                'captureStable("02-me-home.png")',
                'captureStable("03-profile.png")',
                'captureStable("04-drafts.png")',
                'captureStable("05-content-management-detail.png")',
                'captureStable("06-publish-preview.png")',
                'captureStable("07-submit-result.png")',
                'captureStable("08-my-contents.png")',
                'captureStable("09-content-reviews.png")',
                'captureStable("10-content-analytics.png")',
            )},
            {row["file"] for row in manifest["screens"]},
        )
        for row in manifest["screens"]:
            self.assertIn("请求编号", row["forbidden_text"])
            self.assertIn("TraceId", row["forbidden_text"])
            self.assertIn("PROJECT", row["forbidden_text"])

    def test_candidate_identity_is_monotonic_and_unique(self) -> None:
        build = BUILD.read_text(encoding="utf-8")
        release_policy = RELEASE_POLICY.read_text(encoding="utf-8")
        version_test = VERSION_TEST.read_text(encoding="utf-8")
        request = yaml.safe_load(REQUEST.read_text(encoding="utf-8"))
        self.assertIn("versionCode = 10221", build)
        self.assertIn("VERSION_CODE: Int = 10221", release_policy)
        self.assertIn('"R12 test APK versionCode must remain monotonic", 10221', version_test)
        self.assertNotIn("10220", build + release_policy + version_test)
        self.assertEqual("R12", request["release"])
        self.assertTrue(request["candidate"])
        self.assertEqual(6, request["remediation_attempt"])
        self.assertEqual("R12-CANDIDATE-20260726-006", request["request_id"])
        self.assertEqual("CR-0355", request["attempt_exception_id"])
        self.assertEqual(
            "f11901ea1d3c61ccbcc59ec4cab3e77e24b1f1b0",
            request["required_fix_commit"],
        )


if __name__ == "__main__":
    unittest.main()
