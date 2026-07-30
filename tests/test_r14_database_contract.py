from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "database/migrations/V043__r14_chat_invariants.sql"
RUNTIME = (
    ROOT
    / "services/backend/boot/src/main/resources/db/migration"
    / "V043__r14_chat_invariants.sql"
)
ROLLBACK = ROOT / "database/rollback/U043__r14_chat_invariants.sql"
INVARIANTS = ROOT / "database/tests/r14_chat_invariants.sql"
RUNNER = ROOT / "scripts/run_r14_database_invariants.sh"
SMOKE = ROOT / "scripts/run_postgres_migration_smoke.sh"


class R14DatabaseContractTest(unittest.TestCase):
    def test_runtime_migration_is_an_exact_source_copy(self) -> None:
        self.assertTrue(SOURCE.is_file(), SOURCE)
        self.assertTrue(RUNTIME.is_file(), RUNTIME)
        self.assertEqual(SOURCE.read_bytes(), RUNTIME.read_bytes())

    def test_v043_closes_identity_payload_evidence_and_history(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "R14_DIRTY_UPGRADE_DIRECT_MEMBERS_MISSING",
            "R14_DIRTY_UPGRADE_DIRECT_MEMBERS_EXCESS",
            "R14_DIRTY_UPGRADE_DIRECT_SELF_CHAT",
            "R14_DIRTY_UPGRADE_DUPLICATE_DIRECT_PAIR",
            "R14_DIRTY_UPGRADE_INVALID_CLIENT_MESSAGE_ID",
            "R14_DIRTY_UPGRADE_REPORT_CONTRACT_MISSING",
            "uq_r14_conversation_direct_pair",
            "guard_r14_conversation_member",
            "r14_chat_payload_valid",
            "assert_r14_attachment_media",
            "assert_r14_report_evidence",
            "trg_r14_chat_message_status_history",
            "trg_r14_chat_report_status_history",
            "record_platform_status_history",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertGreaterEqual(sql.count("DEFERRABLE INITIALLY DEFERRED"), 7)
        self.assertNotRegex(sql, r"(?i)CREATE\s+TABLE")

    def test_payload_validation_requires_every_discriminator_field(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        for expression in (
            "payload ? 'text'",
            "NOT payload ? 'mediaId'",
            "payload ?& ARRAY['contentId','contentType','title']",
            "NOT payload ? 'fields'",
            "field ?& ARRAY['type','value']",
        ):
            with self.subTest(expression=expression):
                self.assertIn(expression, sql)
        self.assertIn("RETURN COALESCE", sql)

    def test_contact_payload_requires_versioned_ciphertext_capacity(self) -> None:
        sql = SOURCE.read_text(encoding="utf-8")
        self.assertIn("char_length(field->>'value') NOT BETWEEN 1 AND 2048", sql)
        self.assertIn("^hhy-contact-v1\\.[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]{23,}$", sql)
        matrix = INVARIANTS.read_text(encoding="utf-8")
        self.assertIn("R14_CONTACT_PLAINTEXT_WAS_ACCEPTED", matrix)
        self.assertIn("R14_CONTACT_OVERSIZED_ENVELOPE_WAS_ACCEPTED", matrix)

    def test_u043_is_empty_database_only_and_removes_every_r14_object(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        rollback = ROLLBACK.read_text(encoding="utf-8")
        self.assertIn("R14_U043_BUSINESS_FACTS_PRESENT", rollback)
        self.assertNotRegex(
            rollback,
            r"(?im)^\s*(DELETE|TRUNCATE)\s+(FROM\s+)?hhy\.",
        )
        created = {
            "constraint": set(re.findall(r"(?i)ADD\s+CONSTRAINT\s+(\w*r14\w*)", source)),
            "index": set(re.findall(r"(?i)CREATE\s+(?:UNIQUE\s+)?INDEX\s+(\w*r14\w*)", source)),
            "trigger": set(re.findall(r"(?i)CREATE\s+(?:CONSTRAINT\s+)?TRIGGER\s+(\w*r14\w*)", source)),
            "function": set(re.findall(r"(?i)CREATE\s+OR\s+REPLACE\s+FUNCTION\s+hhy\.(\w*r14\w*)", source)),
        }
        for object_type, names in created.items():
            self.assertTrue(names, object_type)
        for name in created["constraint"]:
            self.assertRegex(rollback, rf"(?i)DROP\s+CONSTRAINT\s+IF\s+EXISTS\s+{re.escape(name)}\b")
        for name in created["index"]:
            self.assertRegex(rollback, rf"(?i)DROP\s+INDEX\s+IF\s+EXISTS\s+hhy\.{re.escape(name)}\b")
        for name in created["trigger"]:
            self.assertRegex(rollback, rf"(?i)DROP\s+TRIGGER\s+IF\s+EXISTS\s+{re.escape(name)}\b")
        for name in created["function"]:
            self.assertRegex(rollback, rf"(?i)DROP\s+FUNCTION\s+IF\s+EXISTS\s+hhy\.{re.escape(name)}\b")

    def test_executable_matrix_and_runners_are_wired(self) -> None:
        sql = INVARIANTS.read_text(encoding="utf-8")
        for marker in (
            "R14_DUPLICATE_CLIENT_MESSAGE_ID_WAS_ACCEPTED",
            "R14_MISSING_REQUIRED_PAYLOAD_FIELD_WAS_ACCEPTED",
            "R14_MISSING_IMAGE_MEDIA_ID_WAS_ACCEPTED",
            "R14_CONTACT_PLAINTEXT_WAS_ACCEPTED",
            "R14_CONTACT_OVERSIZED_ENVELOPE_WAS_ACCEPTED",
            "R14_ATTACHMENT_MUTATION_WAS_ACCEPTED",
            "R14_ATTACHED_MEDIA_INVALIDATION_WAS_ACCEPTED",
            "R14_FOREIGN_REPORT_MESSAGE_WAS_ACCEPTED",
            "R14_PHYSICAL_MEMBER_DELETE_WAS_ACCEPTED",
            "R14_MEMBER_IDENTITY_MUTATION_WAS_ACCEPTED",
            "R14_CHAT_INVARIANT_PROPERTY_MATRIX PASS",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))

        runner = RUNNER.read_text(encoding="utf-8")
        for marker in (
            "server_version >= 170000",
            "R14_EMPTY_DATABASE_MIGRATION PASS",
            "R14_V042_UPGRADE PASS",
            "R14_DIRTY_UPGRADE_ATOMIC_MATRIX PASS",
            "R14_U043_ROLLBACK_WITH_FACTS_REJECTED_ATOMICALLY PASS",
            "R14_U043_ROLLBACK_V043_REPLAY PASS",
            "R14_DATABASE_INVARIANTS PASS",
        ):
            with self.subTest(runner_marker=marker):
                self.assertIn(marker, runner)
        self.assertIn("run_r14_database_invariants.sh", SMOKE.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
