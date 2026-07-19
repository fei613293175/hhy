import hashlib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/04-vendors/identity/source/输出内容(1).txt"
CONTRACT = ROOT / "docs/04-vendors/identity/实名认证供应商接口契约_R05.md"
CLIENT = ROOT / (
    "services/backend/access/src/main/java/cc/orbexa/hhy/access/identity/"
    "AliyunMarketIdentityProviderClient.java"
)


class R05IdentityVendorContractTest(unittest.TestCase):
    def test_frozen_contract_tracks_the_original_user_supplied_evidence(self) -> None:
        source = SOURCE.read_bytes()
        contract = CONTRACT.read_text(encoding="utf-8")
        self.assertEqual(12889, len(source))
        self.assertEqual(
            "94fe8a4c6cb43c729e60b29dcd5716761ff63b8bce007a25cc3f8d00122d2abf",
            hashlib.sha256(source).hexdigest(),
        )
        for token in (
            "/api/liveness/h5/result",
            "orderNo",
            "faceImageUrl",
            "resultCode=1001",
            "resultCode=1002",
            "resultCode=1003",
            "resultCode=1004",
            "private_kyc",
        ):
            self.assertIn(token, contract)

    def test_adapter_uses_exact_result_fields_and_never_submits_public_face_url(self) -> None:
        client = CLIENT.read_text(encoding="utf-8")
        self.assertIn('requiredInt(data, "result")', client)
        self.assertIn('firstText(data, "faceImageUrl")', client)
        self.assertIn('requiredInt(data, "resultCode")', client)
        self.assertIn('"&image="', client)
        self.assertNotIn('"&url="', client)
        self.assertIn("MAX_FACE_IMAGE_BYTES = 100 * 1024", client)


if __name__ == "__main__":
    unittest.main()
