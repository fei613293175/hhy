from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
OPENAPI = ROOT / "contracts/openapi.yaml"
RUNTIME_OPENAPI = ROOT / "services/backend/boot/src/main/resources/contracts/openapi.yaml"
CLIENT = ROOT / "packages/api-client/src/client.generated.ts"
SERVICE = ROOT / "services/backend/content/src/main/java/cc/orbexa/hhy/content/R10Service.java"
CONTACT_SERVICE = ROOT / "services/backend/content/src/main/java/cc/orbexa/hhy/content/R07Service.java"
CONTROLLER = ROOT / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user/R08Controller.java"


class R10GroupContractTest(unittest.TestCase):
    def test_openapi_runtime_and_generated_client_expose_join_password(self) -> None:
        source = OPENAPI.read_bytes()
        self.assertEqual(source, RUNTIME_OPENAPI.read_bytes())
        openapi = source.decode("utf-8")
        client = CLIENT.read_text(encoding="utf-8")
        self.assertGreaterEqual(openapi.count("- JOIN_PASSWORD"), 4)
        self.assertGreaterEqual(client.count('"JOIN_PASSWORD"'), 4)
        self.assertRegex(
            openapi,
            r"(?s)/api/v1/contents/\{id\}/contacts/\{channel\}/access:.*?"
            r"name: channel.*?enum:.*?- JOIN_PASSWORD",
        )

    def test_group_dispatch_and_sensitive_channel_boundaries_are_explicit(self) -> None:
        controller = CONTROLLER.read_text(encoding="utf-8")
        service = SERVICE.read_text(encoding="utf-8")
        contact_service = CONTACT_SERVICE.read_text(encoding="utf-8")
        self.assertIn('case "GROUP_CHAT" -> groupService.create', controller)
        self.assertIn("groupService.isGroup(id)", controller)
        self.assertIn('Set.of("WECHAT", "PHONE", "QQ", "EMAIL", "JOIN_PASSWORD")', service)
        self.assertIn('case "JOIN_PASSWORD" -> "口令***"', service)
        self.assertIn('Set.of("WECHAT", "PHONE", "QQ", "EMAIL", "LINK", "QR_CODE", "JOIN_PASSWORD")',
                      contact_service)
        self.assertNotRegex(service, re.compile(r"putOrRemove\([^\n]+JOIN_PASSWORD", re.IGNORECASE))

    def test_public_share_does_not_project_entry_secrets(self) -> None:
        service = SERVICE.read_text(encoding="utf-8")
        share = service[service.index("public PublicPage publicShare"):service.index("private GroupAttributes")]
        for forbidden in ("groupLink()", "groupNo()", "JOIN_PASSWORD", "contacts"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, share)
        self.assertIn('"群平台：" + group.platform()', share)


if __name__ == "__main__":
    unittest.main()
