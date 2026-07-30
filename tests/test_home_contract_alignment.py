from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8-sig")


class HomeContractAlignmentTest(unittest.TestCase):
    def test_canonical_document_keeps_complete_home_information_architecture(self) -> None:
        source = read("合伙云Pro_完整项目开发文档_V1.2.2_页面与运营规格冻结版.md")
        for required in (
            "## 4.3 首页",
            "平台 Logo/名称、全局搜索、消息未读入口、可选运营公告",
            "四个一级入口：项目、App、群聊、团队长",
            "运营模块：轮播、红包专区、热门推荐、最新发布、精选项目、精选 App、热门群聊、推荐团队长、头条、置顶、活动专题、新手指南",
            "首页模块由后台配置开关、顺序、标题、副标题、数量、数据源和展示时段",
        ):
            self.assertIn(required, source)

    def test_home_visual_contract_cannot_replace_documented_functions(self) -> None:
        spec = read("design/R06-UI-FROZEN/specs/SCR-HOME-001.md")
        self.assertNotIn("已实现真实能力三入口", spec)
        self.assertNotIn("真实搜索功能品牌主卡", spec)
        for required in (
            "横向 Banner",
            "项目/App/群聊/团队长四个一级入口",
            "B02/P01",
            "B02/P02",
            "B02/P04",
            "moduleType",
            "coverUrl",
            "moreTarget",
        ):
            self.assertIn(required, spec)

    def test_page_catalog_uses_exact_home_and_group_panels(self) -> None:
        with (ROOT / "catalogs/ui_page_specifications.csv").open(encoding="utf-8-sig", newline="") as handle:
            rows = {row["页面ID"]: row for row in csv.DictReader(handle)}
        self.assertEqual("B02/P01;B02/P02;B02/P04", rows["SCR-HOME-001"]["UI参考"])
        self.assertEqual("B02/P07", rows["SCR-LIST-003"]["UI参考"])
        self.assertEqual("B03/P03", rows["SCR-DETAIL-003"]["UI参考"])
        self.assertEqual("B04/P03", rows["SCR-PUB-004"]["UI参考"])

    def test_corrected_home_visual_pass_is_bound_to_r10_candidate(self) -> None:
        with (ROOT / "catalogs/ui_visual_acceptance.csv").open(encoding="utf-8-sig", newline="") as handle:
            rows = {row["页面ID"]: row for row in csv.DictReader(handle)}
        home = rows["SCR-HOME-001"]
        self.assertEqual("PASS", home["验收状态"])
        self.assertEqual("B02/P01;B02/P02;B02/P04", home["视觉来源"])
        self.assertEqual(
            "tests/android/visual-baselines/R10/01-home.png",
            home["实现截图证据"],
        )
        for marker in (
            "肉眼丰富度=PASS",
            "信息层级=PASS",
            "组件精致度=PASS",
            "真实业务映射=PASS",
            "状态完整性=PASS",
            "AI对照结论=PASS",
        ):
            self.assertIn(marker, home["说明"])

        screenshot = ROOT / home["实现截图证据"]
        actual_sha256 = hashlib.sha256(screenshot.read_bytes()).hexdigest()
        approval = yaml.safe_load(
            read("tests/android/visual-baselines/R10/APPROVAL.yaml")
        )
        report = json.loads(
            read("artifacts/validation/r10-task007-android/candidate-report.json")
        )
        approval_home = next(
            screen for screen in approval["screens"] if screen["file"] == "01-home.png"
        )
        report_home = next(
            screen
            for screen in report["baseline_approval"]["screens"]
            if screen["file"] == "01-home.png"
        )
        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual("AI_IMPLEMENTATION_AGENT", approval["authority"])
        self.assertEqual("30013677033", approval["source"]["github_run_id"])
        self.assertEqual(
            "1e35a97fb0541644d85a264a84229435ead378bf",
            approval["source"]["commit"],
        )
        self.assertEqual("PASS", report["status"])
        self.assertEqual("30015180600", report["github_run_id"])
        self.assertEqual(approval_home["sha256"], actual_sha256)
        self.assertEqual(report_home["sha256"], actual_sha256)

    def test_android_home_preserves_and_renders_rich_contract(self) -> None:
        api = read("apps/android/core/network/src/main/java/cc/orbexa/hhy/network/ExperienceApi.kt")
        shell = read("apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt")
        self.assertNotIn("items: List<String>", api)
        for field in (
            "itemType",
            "coverUrl",
            "badges",
            "layoutType",
            "moreTarget",
            "featureFlags",
            "trackingContext",
        ):
            self.assertIn(field, api)
        for visible_contract in (
            "搜索项目 / App / 群聊 / 团队长",
            'HomeCategory("项目"',
            'HomeCategory("App"',
            'HomeCategory("群聊"',
            'HomeCategory("团队长"',
            'it.type == "BANNER"',
            'it.type == "NOTICE"',
            '"HORIZONTAL_LIST"',
            '"VERTICAL_LIST"',
            "AsyncImage",
        ):
            self.assertIn(visible_contract, shell)
        self.assertNotIn("发现真实合作机会", shell)
        self.assertNotIn("发布项目", shell)
        self.assertNotIn("推广App", shell)

    def test_each_content_release_has_explicit_home_return_in_acceptance(self) -> None:
        expected = {
            "R08": "首页“项目”一级入口",
            "R09": "首页“App”一级入口",
            "R10": "首页“群聊”一级入口",
            "R11": "首页“团队长”一级入口",
        }
        for release, marker in expected.items():
            stories = read(f"releases/{release}/STORIES.yaml")
            self.assertIn(marker, stories)
            self.assertIn("SCR-HOME-001", stories)

    def test_home_runtime_uses_real_sources_deduplication_and_controlled_staging_data(self) -> None:
        service = read("services/backend/content/src/main/java/cc/orbexa/hhy/content/ContentService.java")
        store = read("services/backend/content/src/main/java/cc/orbexa/hhy/content/ContentPostgresStore.java")
        fixture = read("scripts/prepare_home_cms_staging_fixture.sh")
        shell = read("apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt")
        for marker in (
            'case "LATEST_PROJECTS"',
            'case "LATEST_APPS"',
            "seenContentIds.add",
            'config.path("moreTarget")',
        ):
            self.assertIn(marker, service)
        self.assertIn("p.status='ONLINE'", store)
        self.assertIn('HHY_HOME_CMS_ENVIRONMENT" != "STAGING"', fixture)
        self.assertIn("ON CONFLICT (code) DO UPDATE", fixture)
        self.assertIn("home_latest_projects", fixture)
        self.assertIn("home_recommended_apps", fixture)
        self.assertIn('Text("查看更多")', shell)

    def test_global_boundary_prevents_future_function_drift(self) -> None:
        boundary = read("docs/00-baseline/正式商业系统全局硬性开发边界.md")
        self.assertIn("实现、补充规格、效果图解释和验收记录必须与其逐项一一对应", boundary)
        self.assertIn("聚合页面必须承担跨版本回接责任", boundary)
        self.assertIn("仅以实现自身生成的基线自证", boundary)


if __name__ == "__main__":
    unittest.main()
