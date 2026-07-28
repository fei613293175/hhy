#!/usr/bin/env python3
"""Strict regression tests for the CR-0429 WebSocket contract."""
from __future__ import annotations

from contextlib import redirect_stdout
import csv
import hashlib
import io
from pathlib import Path
import re
import shutil
import sys
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import generate_contracts  # noqa: E402


def load_yaml(path: Path) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def read_registry(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_registry(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class R14WebSocketContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.contract = load_yaml(ROOT / "contracts/websocket-events.yaml")
        self.events = {event["code"]: event for event in self.contract["events"]}

    def test_handshake_authentication_is_exact_and_log_safe(self) -> None:
        auth = self.contract["authentication"]
        request = auth["request_subprotocols"]
        self.assertEqual(
            ["hhy.v1", "hhy.access.<compact-JWT>"],
            request["required_exactly_once"],
        )
        self.assertTrue(request["reject_duplicate_or_missing"])
        self.assertFalse(request["padding_allowed"])
        self.assertFalse(request["whitespace_allowed"])
        self.assertEqual("hhy.v1", auth["selected_subprotocol"])
        self.assertEqual(
            ["jwt_signature", "jwt_expiry", "session_active"],
            auth["validate_before_upgrade"],
        )
        self.assertEqual("RESERVED_UNAVAILABLE", auth["ws_ticket"]["status"])
        self.assertEqual(
            {"edge", "reverse_proxy", "handshake_error", "server", "application"},
            set(auth["log_redaction"]["required_scopes"]),
        )
        self.assertEqual("Sec-WebSocket-Protocol", auth["log_redaction"]["header"])
        self.assertEqual(
            {
                "token_in_url_forbidden": True,
                "token_in_response_forbidden": True,
                "token_in_error_text_forbidden": True,
            },
            auth["credential_transport"],
        )

        pattern = re.compile(request["compact_jwt_pattern"])
        self.assertIsNotNone(pattern.fullmatch("eyJhbGciOiJIUzI1NiJ9.e30.signature_1"))
        for invalid in (
            "eyJhbGciOiJIUzI1NiJ9=.e30.signature",
            "eyJhbGciOiJIUzI1NiJ9.e30=.signature",
            "eyJhbGciOiJIUzI1NiJ9.e30.signature ",
            "eyJhbGciOiJIUzI1NiJ9.e30",
        ):
            self.assertIsNone(pattern.fullmatch(invalid), invalid)

    def test_ack_semantics_and_event_are_frozen(self) -> None:
        ack = self.contract["delivery"]["ack"]
        self.assertEqual("system.delivery.ack", ack["s2c_confirmation_event"])
        self.assertEqual(["eventId", "serverSequence"], ack["match_fields"])
        self.assertEqual("IDEMPOTENT_SUCCESS", ack["duplicate_result"])
        self.assertEqual("REJECT", ack["unknown_or_mismatched_result"])
        self.assertTrue(ack["stop_redelivery_only_after_valid_ack"])
        self.assertFalse(ack["ack_event_ack_required"])
        self.assertEqual(
            {
                "event": "chat.message.ack",
                "match": ["conversationId", "clientMessageId"],
            },
            ack["c2s_confirmations"]["chat.message.send"],
        )
        self.assertEqual(
            {
                "event": "chat.read.updated",
                "match": [
                    "conversationId", "authenticatedUserId=userId", "lastReadMessageId",
                ],
            },
            ack["c2s_confirmations"]["chat.message.read"],
        )
        event = self.events["system.delivery.ack"]
        self.assertEqual("C2S", event["direction"])
        self.assertFalse(event["ack_required"])
        self.assertEqual(
            ["eventId", "serverSequence"], event["payload"]["required"]
        )
        self.assertEqual("uuid", event["payload"]["properties"]["eventId"]["format"])
        self.assertEqual(1, event["payload"]["properties"]["serverSequence"]["minimum"])

    def test_resume_watermark_and_gap_scopes_are_unambiguous(self) -> None:
        resume = self.contract["delivery"]["resume"]
        query = resume["query_parameter"]
        self.assertEqual("lastServerSequence", query["name"])
        self.assertEqual(0, query["minimum"])
        self.assertTrue(query["reject_above_server_high_watermark"])
        self.assertIn("highest contiguous processed sequence", query["positive_means"])
        self.assertEqual([], resume["modes"]["REPLAY_COMPLETE"]["affected_scopes"])
        self.assertEqual(
            ["CHAT", "NOTIFICATIONS"],
            resume["modes"]["REST_GAP_FILL"]["allowed_affected_scopes"],
        )
        gap = resume["gap_fill"]
        self.assertEqual(
            ["chatGetConversations", "chatGetConversationsByIdMessages"],
            gap["CHAT"]["operations"],
        )
        self.assertEqual(
            ["notificationGetNotifications"], gap["NOTIFICATIONS"]["operations"]
        )
        self.assertEqual(
            "system.resume.payload.resumeFromServerSequence",
            gap["reconnect_sequence_source"],
        )
        self.assertTrue(gap["infer_sequence_from_rest_forbidden"])

        event = self.events["system.resume"]
        self.assertEqual("S2C", event["direction"])
        self.assertFalse(event["ack_required"])
        self.assertEqual(
            {
                "mode", "requestedLastServerSequence", "serverHighWatermark",
                "resumeFromServerSequence", "affectedScopes",
            },
            set(event["payload"]["required"]),
        )
        self.assertEqual(
            ["REPLAY_COMPLETE", "REST_GAP_FILL"],
            event["payload"]["properties"]["mode"]["enum"],
        )
        for field in (
            "requestedLastServerSequence", "serverHighWatermark",
            "resumeFromServerSequence",
        ):
            self.assertEqual(0, event["payload"]["properties"][field]["minimum"])

    def test_event_set_is_exactly_twelve(self) -> None:
        self.assertEqual(
            list(generate_contracts.LEGACY_WEBSOCKET_EVENT_CODES)
            + list(generate_contracts.NEW_WEBSOCKET_EVENT_CODES),
            [event["code"] for event in self.contract["events"]],
        )

    def test_targeted_sync_is_isolated_and_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary)
            (target / "contracts").mkdir(parents=True)
            runtime = target / "services/backend/boot/src/main/resources/contracts"
            runtime.mkdir(parents=True)
            for name in (
                "openapi.yaml", "admin-openapi.yaml", "websocket-events.yaml",
                "contract_status.csv",
            ):
                shutil.copy2(ROOT / "contracts" / name, target / "contracts" / name)

            contract_path = target / "contracts/websocket-events.yaml"
            seed = load_yaml(contract_path)
            seed["authentication"] = {"method": "legacy ambiguous authentication"}
            seed["delivery"] = {
                "server_sequence_scope": "authenticatedUserId",
                "resume": "legacy ambiguous resume",
            }
            seed["events"] = [
                event for event in seed["events"]
                if event["code"] in generate_contracts.LEGACY_WEBSOCKET_EVENT_CODES
            ]
            contract_path.write_text(
                yaml.safe_dump(seed, allow_unicode=True, sort_keys=False),
                encoding="utf-8",
            )
            registry_path = target / "contracts/contract_status.csv"
            write_registry(
                registry_path,
                [
                    row for row in read_registry(registry_path)
                    if row["事实源"] != "contracts/websocket-events.yaml"
                    or row["契约标识"] in generate_contracts.LEGACY_WEBSOCKET_EVENT_CODES
                ],
            )

            openapi_before = {
                name: sha256(target / "contracts" / name)
                for name in ("openapi.yaml", "admin-openapi.yaml")
            }
            registry_before = read_registry(registry_path)
            non_ws_before = [
                row for row in registry_before
                if row["事实源"] != "contracts/websocket-events.yaml"
            ]
            source_before = load_yaml(contract_path)
            legacy_before = {
                event["code"]: event for event in source_before["events"]
                if event["code"] in generate_contracts.LEGACY_WEBSOCKET_EVENT_CODES
            }
            definitions_before = source_before["definitions"]

            original_root = generate_contracts.ROOT
            try:
                generate_contracts.ROOT = target
                with redirect_stdout(io.StringIO()):
                    self.assertEqual(0, generate_contracts.synchronize_websocket_contract())
                first_contract_bytes = (target / "contracts/websocket-events.yaml").read_bytes()
                first_registry_bytes = (target / "contracts/contract_status.csv").read_bytes()
                with redirect_stdout(io.StringIO()):
                    self.assertEqual(0, generate_contracts.synchronize_websocket_contract())
                self.assertEqual(
                    first_contract_bytes,
                    (target / "contracts/websocket-events.yaml").read_bytes(),
                )
                self.assertEqual(
                    first_registry_bytes,
                    (target / "contracts/contract_status.csv").read_bytes(),
                )
            finally:
                generate_contracts.ROOT = original_root

            for name, expected in openapi_before.items():
                self.assertEqual(expected, sha256(target / "contracts" / name))
            generated = load_yaml(target / "contracts/websocket-events.yaml")
            generated_by_code = {event["code"]: event for event in generated["events"]}
            self.assertEqual(legacy_before, {
                code: generated_by_code[code]
                for code in generate_contracts.LEGACY_WEBSOCKET_EVENT_CODES
            })
            self.assertEqual(definitions_before, generated["definitions"])
            self.assertEqual(
                non_ws_before,
                [
                    row for row in read_registry(target / "contracts/contract_status.csv")
                    if row["事实源"] != "contracts/websocket-events.yaml"
                ],
            )
            ws_rows = [
                row for row in read_registry(target / "contracts/contract_status.csv")
                if row["事实源"] == "contracts/websocket-events.yaml"
            ]
            self.assertEqual(12, len(ws_rows))
            self.assertEqual(
                (target / "contracts/websocket-events.yaml").read_bytes(),
                (runtime / "websocket-events.yaml").read_bytes(),
            )


if __name__ == "__main__":
    unittest.main()
