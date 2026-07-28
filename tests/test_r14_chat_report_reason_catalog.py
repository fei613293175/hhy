from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def load_script(name: str):
    path = ROOT / "scripts" / f"{name}.py"
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


catalog = load_script("generate_chat_report_reason_catalog")
contracts = load_script("generate_contracts")


class R14ChatReportReasonCatalogTest(unittest.TestCase):
    def test_openapi_options_are_the_only_source_and_match_generated_enum(self) -> None:
        options = catalog.load_options(ROOT)
        self.assertEqual(
            [
                {"code": "HARASSMENT", "label": "骚扰", "enabled": True, "order": 10},
                {"code": "DUPLICATE_BULK_MESSAGE", "label": "相同文案批量发送", "enabled": True, "order": 20},
                {"code": "DANGEROUS_LINK", "label": "危险链接", "enabled": True, "order": 30},
            ],
            options,
        )
        source = yaml.safe_load((ROOT / catalog.SOURCE).read_text(encoding="utf-8"))
        reason = source["components"]["schemas"]["ChatPostConversationsByIdReportRequest"]["properties"]["reasonCode"]
        self.assertEqual([item["code"] for item in options], reason["enum"])
        self.assertNotIn("x-hhy-options", source["components"]["schemas"]["ReportResource"]["properties"]["reasonCode"])

    def test_runtime_types_and_generated_catalogs_are_in_sync(self) -> None:
        self.assertEqual(
            (ROOT / "contracts/openapi.yaml").read_bytes(),
            (ROOT / "services/backend/boot/src/main/resources/contracts/openapi.yaml").read_bytes(),
        )
        types = (ROOT / "packages/api-client/src/client.generated.ts").read_text(encoding="utf-8")
        self.assertIn('reasonCode: "HARASSMENT" | "DUPLICATE_BULK_MESSAGE" | "DANGEROUS_LINK";', types)
        self.assertEqual([], catalog.generate(ROOT, check=True))
        detail = (ROOT / "apps/android/feature/chat/src/main/java/cc/orbexa/hhy/chat/R14ChatDetailScreen.kt").read_text(encoding="utf-8")
        self.assertIn("reasons = R14_CHAT_REPORT_REASONS", detail)
        self.assertNotIn("reasons = emptyList()", detail)

    def test_rendering_is_deterministic_and_disabled_options_are_not_exposed_to_android(self) -> None:
        options = catalog.load_options(ROOT)
        self.assertEqual(catalog.render_java(options), catalog.render_java(options))
        disabled = options + [{"code": "DISABLED_FIXTURE", "label": "停用夹具", "enabled": False, "order": 40}]
        kotlin = catalog.render_kotlin(disabled)
        java = catalog.render_java(disabled)
        self.assertNotIn("DISABLED_FIXTURE", kotlin)
        self.assertIn('new Option("DISABLED_FIXTURE", "停用夹具", false, 40)', java)

    def test_check_fails_when_enum_or_generated_output_drifts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in (catalog.SOURCE, catalog.JAVA_OUTPUT, catalog.KOTLIN_OUTPUT):
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT / relative).read_bytes())
            document = yaml.safe_load((root / catalog.SOURCE).read_text(encoding="utf-8"))
            document["components"]["schemas"]["ChatPostConversationsByIdReportRequest"]["properties"]["reasonCode"]["enum"] = ["HARASSMENT"]
            (root / catalog.SOURCE).write_text(yaml.safe_dump(document, allow_unicode=True, sort_keys=False), encoding="utf-8")
            with self.assertRaises(catalog.CatalogError):
                catalog.load_options(root)

            (root / catalog.SOURCE).write_bytes((ROOT / catalog.SOURCE).read_bytes())
            (root / catalog.KOTLIN_OUTPUT).write_text("drift\n", encoding="utf-8")
            self.assertEqual(
                [str(catalog.KOTLIN_OUTPUT).replace("\\", "/")],
                catalog.generate(root, check=True),
            )

    def test_targeted_contract_renderer_contains_only_enabled_codes_in_order(self) -> None:
        options = catalog.load_options(ROOT) + [
            {"code": "DISABLED_FIXTURE", "label": "停用夹具", "enabled": False, "order": 40}
        ]
        rendered = contracts._r14_report_reason_property(options)
        enum_section = rendered.split("          x-hhy-options:", 1)[0]
        self.assertNotIn("DISABLED_FIXTURE", enum_section)
        self.assertLess(enum_section.index("HARASSMENT"), enum_section.index("DANGEROUS_LINK"))
        self.assertIn("DISABLED_FIXTURE", rendered)


if __name__ == "__main__":
    unittest.main()
