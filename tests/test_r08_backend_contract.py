from pathlib import Path
import unittest
import yaml


ROOT = Path(__file__).resolve().parents[1]
OPENAPI = ROOT / "contracts/openapi.yaml"
RUNTIME = ROOT / "services/backend/boot/src/main/resources/contracts/openapi.yaml"
CLIENT = ROOT / "packages/api-client/src/client.generated.ts"


class R08BackendContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.contract = yaml.safe_load(OPENAPI.read_text(encoding="utf-8"))
        cls.schemas = cls.contract["components"]["schemas"]

    def test_runtime_contract_is_exact_source_copy(self) -> None:
        self.assertEqual(OPENAPI.read_bytes(), RUNTIME.read_bytes())

    def test_contacts_are_structured_and_favorite_version_is_required(self) -> None:
        contact = self.schemas["ContactInputResource"]
        self.assertEqual(["channel", "value"], contact["required"])
        self.assertTrue(contact["properties"]["value"]["x-sensitive"])
        for request_name in ("ContentPostContentsRequest", "ContentPatchContentsByIdRequest"):
            contacts = self.schemas[request_name]["properties"]["contacts"]
            self.assertEqual("array", contacts["type"])
            self.assertEqual(20, contacts["maxItems"])
            self.assertEqual("#/components/schemas/ContactInputResource", contacts["items"]["$ref"])
        favorite = self.schemas["ContentPostContentsByIdFavoriteRequest"]
        self.assertIn("expectedVersion", favorite["required"])

    def test_share_returns_https_result_and_public_release_ownership_is_preserved(self) -> None:
        share = self.schemas["ShareResultResource"]
        self.assertEqual("^https://", share["properties"]["url"]["pattern"])
        response = self.schemas["ContentPostContentsByIdShareResponse"]["properties"]["data"]
        self.assertEqual("#/components/schemas/ShareResultResource", response["$ref"])
        operation = self.contract["paths"]["/public-api/v1/share/contents/{id}"]["get"]
        self.assertEqual("R28", operation["x-release"])

    def test_generated_client_contains_new_contract_shapes(self) -> None:
        generated = CLIENT.read_text(encoding="utf-8")
        for marker in ("ContactInputResource:", "ShareResultResource:", "expectedVersion: number"):
            self.assertIn(marker, generated)


if __name__ == "__main__":
    unittest.main()
