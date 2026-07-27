#!/usr/bin/env python3
"""Fail when the R14 chat implementation entry contracts drift."""
from __future__ import annotations

from argparse import ArgumentParser
import csv
from pathlib import Path
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
PAGES = {
    "SCR-CHAT-001": "B07/P01",
    "SCR-CHAT-002": "SPEC:design/R14-UI-FROZEN/specs/SCR-CHAT-002.md",
    "SHEET-CHAT-001": "SPEC:design/R14-UI-FROZEN/specs/SHEET-CHAT-001.md",
    "SHEET-CHAT-002": "SPEC:design/R14-UI-FROZEN/specs/SHEET-CHAT-002.md",
    "DIALOG-CHAT-BLOCK-001": "SPEC:design/R14-UI-FROZEN/specs/DIALOG-CHAT-BLOCK-001.md",
    "DIALOG-CHAT-DELETE-001": "SPEC:design/R14-UI-FROZEN/specs/DIALOG-CHAT-DELETE-001.md",
}
B07_SHA256 = "1262b25c4d3f40ba8fc89cce1890b8203911e608b82900a223c4474b2a877c08"
BANNED_FIELD_KEYS = {
    "id", "page", "pageSize", "cursor", "status", "sort",
    "X-Idempotency-Key", "xIdempotencyKey", "clientMessageId",
    "lastReadMessageId", "expectedVersion", "resourceId", "businessNo",
    "version", "acceptedAt",
}
BANNED_USER_TEXT = ("requestId", "clientMessageId", "稳定错误码")


def read_csv(root: Path, relative: str) -> list[dict[str, str]]:
    with (root / relative).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def load_yaml(root: Path, relative: str) -> dict:
    return yaml.safe_load((root / relative).read_text(encoding="utf-8"))


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    visual_rows = [
        row for row in read_csv(root, "catalogs/ui_visual_acceptance.csv")
        if row.get("计划版本") == "R14"
    ]
    by_page: dict[str, list[dict[str, str]]] = {}
    for row in visual_rows:
        by_page.setdefault(row.get("页面ID", ""), []).append(row)
    if set(by_page) != set(PAGES):
        errors.append(f"R14_VISUAL_PAGE_SET expected={sorted(PAGES)} actual={sorted(by_page)}")
    for page, source in PAGES.items():
        rows = by_page.get(page, [])
        if len(rows) != 1:
            errors.append(f"R14_VISUAL_UNIQUE {page} count={len(rows)}")
            continue
        row = rows[0]
        if row.get("视觉来源") != source:
            errors.append(f"R14_VISUAL_SOURCE {page} {row.get('视觉来源')}")
        expected_coverage = "EXACT" if page == "SCR-CHAT-001" else "APPROVED_SUPPLEMENT"
        if row.get("覆盖状态") != expected_coverage:
            errors.append(f"R14_VISUAL_COVERAGE {page} {row.get('覆盖状态')}")
        if row.get("验收状态") != "IN_REVIEW":
            errors.append(f"R14_VISUAL_PREMATURE_PASS {page} {row.get('验收状态')}")
        if "P05" in row.get("参考证据", "") or "P06" in row.get("参考证据", ""):
            errors.append(f"R14_VISUAL_R15_PANEL {page}")

    page_specs = {
        row["页面ID"]: row for row in read_csv(root, "catalogs/ui_page_specifications.csv")
        if row.get("页面ID") in PAGES
    }
    for page, source in PAGES.items():
        if page_specs.get(page, {}).get("UI参考") != source:
            errors.append(f"R14_PAGE_SPEC_VISUAL {page}")

    bindings = {
        row["Screen_ID"]: row for row in read_csv(root, "catalogs/screen_visual_binding.csv")
        if row.get("Screen_ID") in PAGES
    }
    if set(bindings) != set(PAGES):
        errors.append(f"R14_BINDING_PAGE_SET actual={sorted(bindings)}")
    for page, row in bindings.items():
        combined = "/".join((row.get("主参考批次", ""), row.get("参考面板", "")))
        if "P01-P08" in combined or "TOKENS" in combined:
            errors.append(f"R14_BINDING_RANGE {page} {combined}")

    for source in PAGES.values():
        if not source.startswith("SPEC:"):
            continue
        path = root / source.removeprefix("SPEC:")
        if not path.is_file():
            errors.append(f"R14_VISUAL_SPEC_MISSING {path.relative_to(root)}")
            continue
        if B07_SHA256 not in path.read_text(encoding="utf-8"):
            errors.append(f"R14_VISUAL_SPEC_HASH {path.relative_to(root)}")

    fields = [
        row for row in read_csv(root, "catalogs/ui_page_fields.csv")
        if row.get("页面ID") in PAGES
    ]
    for row in fields:
        if row.get("字段键") in BANNED_FIELD_KEYS:
            errors.append(f"R14_VISIBLE_TECH_FIELD {row['页面ID']} {row['字段键']}")

    states = [
        row for row in read_csv(root, "catalogs/ui_page_states.csv")
        if row.get("页面ID") in PAGES
    ]
    for row in states:
        visible = " ".join(row.get(key, "") for key in ("展示行为", "允许操作", "恢复策略"))
        for token in BANNED_USER_TEXT:
            if token in visible:
                errors.append(f"R14_VISIBLE_TECH_STATE {row['页面ID']} {row['状态码']} {token}")
    report_states = {row.get("状态码") for row in states if row.get("页面ID") == "SHEET-CHAT-002"}
    forbidden_report_states = {"QUEUE", "ASSIGNED", "REVIEWING", "DECIDED", "REJECTED"}
    if report_states & forbidden_report_states:
        errors.append(f"R14_REPORT_ADMIN_STATES {sorted(report_states & forbidden_report_states)}")

    actions = [
        row for row in read_csv(root, "catalogs/ui_action_matrix.csv")
        if row.get("UI_ID") in PAGES
    ]
    for row in actions:
        visible = " ".join(
            row.get(key, "")
            for key in ("成功状态", "失败状态", "入口组件", "二次确认", "成功后导航")
        )
        for token in BANNED_USER_TEXT:
            if token in visible:
                errors.append(f"R14_VISIBLE_TECH_ACTION {row['UI_ID']} {row['动作ID']} {token}")
        if any(token in visible for token in ("支付/认证/发布", "审批要求", "审批单")):
            errors.append(f"R14_GENERIC_ACTION_DRIFT {row['UI_ID']} {row['动作ID']}")

    openapi = load_yaml(root, "contracts/openapi.yaml")
    schemas = openapi["components"]["schemas"]
    items = schemas["ChatGetConversationsByIdMessagesResponse"]["properties"]["data"]["properties"]["items"]["items"]
    if items.get("$ref") != "#/components/schemas/ChatMessageResource":
        errors.append("R14_MESSAGE_PAGE_RESOURCE")
    expected_payloads = {
        "ChatTextPayload", "ChatImagePayload", "ChatContentCardPayload", "ChatContactCardPayload"
    }
    payload_refs = {
        item.get("$ref", "").rsplit("/", 1)[-1]
        for item in schemas.get("ChatMessagePayload", {}).get("oneOf", [])
    }
    if payload_refs != expected_payloads:
        errors.append(f"R14_MESSAGE_PAYLOAD_SET {sorted(payload_refs)}")
    request_refs = {
        item.get("$ref", "").rsplit("/", 1)[-1]
        for item in schemas.get("ChatPostConversationsByIdMessagesRequest", {}).get("oneOf", [])
    }
    if request_refs != {
        "ChatTextMessageRequest", "ChatImageMessageRequest",
        "ChatContentCardMessageRequest", "ChatContactCardMessageRequest",
    }:
        errors.append(f"R14_MESSAGE_REQUEST_SET {sorted(request_refs)}")
    for schema_name in (
        "ChatMessageResource", "ChatTextMessageRequest", "ChatImageMessageRequest",
        "ChatContentCardMessageRequest", "ChatContactCardMessageRequest",
    ):
        client_message_id = schemas.get(schema_name, {}).get("properties", {}).get(
            "clientMessageId", {}
        )
        if client_message_id.get("type") != "string" \
                or client_message_id.get("minLength") != 1 \
                or client_message_id.get("maxLength") != 64:
            errors.append(f"R14_CLIENT_MESSAGE_ID_CONTRACT {schema_name}")
    contact = schemas.get("ChatContactCardPayload", {})
    if set(contact.get("properties", {})) != {"fields", "note"}:
        errors.append("R14_CONTACT_CARD_FIELDS")
    field_types = set(
        schemas.get("ChatContactField", {}).get("properties", {}).get("type", {}).get("enum", [])
    )
    if field_types != {"PHONE", "WECHAT", "QQ", "EMAIL", "OTHER"}:
        errors.append(f"R14_CONTACT_TYPES {sorted(field_types)}")

    websocket = load_yaml(root, "contracts/websocket-events.yaml")
    ws_defs = websocket.get("definitions", {})
    ws_payload_refs = {
        item.get("$ref", "").rsplit("/", 1)[-1]
        for item in ws_defs.get("ChatMessagePayload", {}).get("oneOf", [])
    }
    if ws_payload_refs != expected_payloads:
        errors.append(f"R14_WEBSOCKET_PAYLOAD_SET {sorted(ws_payload_refs)}")
    if set(ws_defs.get("ChatContactCardPayload", {}).get("properties", {})) != {"fields", "note"}:
        errors.append("R14_WEBSOCKET_CONTACT_CARD_FIELDS")

    manifest = load_yaml(root, "releases/R14/RELEASE_MANIFEST.yaml")
    if manifest.get("status") != "DEVELOPMENT_READY":
        errors.append(f"R14_MANIFEST_STATUS {manifest.get('status')}")
    baseline = manifest.get("entry_baseline", {})
    if baseline.get("change_request") != "CR-0417":
        errors.append("R14_MANIFEST_ENTRY_BASELINE")
    if not (root / "releases/R14/PARALLEL_EXECUTION_PLAN.yaml").is_file():
        errors.append("R14_EXECUTION_PLAN_MISSING")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args(argv)
    errors = validate(args.root.resolve())
    if errors:
        print("R14_ENTRY_CONTRACT_FAILED", len(errors))
        for error in errors:
            print(error)
        return 1
    print("R14_ENTRY_CONTRACT_OK pages=6 payloads=4")
    return 0


if __name__ == "__main__":
    sys.exit(main())
