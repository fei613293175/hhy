#!/usr/bin/env python3
"""Verify the corrected R16 product/order and visual construction baseline."""
from __future__ import annotations

from argparse import ArgumentParser
from datetime import datetime, timezone
from pathlib import Path
import csv
import hashlib
import json
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
ORDER_STATUS = {
    "PENDING_PAYMENT", "PAYMENT_PROCESSING", "PAID", "FULFILLING",
    "COMPLETED", "PAYMENT_FAILED", "CLOSED", "CHANNEL_REVERSAL",
}


def read_csv(relative: str) -> list[dict[str, str]]:
    with (ROOT / relative).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def sha256(relative: str) -> str:
    return hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()


def ref(schema: dict) -> str:
    return str(schema.get("$ref") or "")


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument("--write-evidence", action="store_true")
    args = parser.parse_args()
    errors: list[str] = []
    specs = {
        "client": yaml.safe_load((ROOT / "contracts/openapi.yaml").read_text(encoding="utf-8")),
        "admin": yaml.safe_load((ROOT / "contracts/admin-openapi.yaml").read_text(encoding="utf-8")),
    }

    required_shapes = {
        "BenefitResource": (
            {"benefitCode", "name", "value", "unit"},
            {"benefitCode", "name", "value"},
        ),
        "ProductSkuResource": (
            {
                "id", "productId", "skuCode", "name", "priceCent",
                "memberPriceCent", "durationDays", "benefits",
                "commissionEnabled", "level1Bps", "level2Bps",
                "saleStartsAt", "saleEndsAt", "status", "version",
            },
            {
                "id", "productId", "skuCode", "name", "priceCent",
                "benefits", "commissionEnabled", "status", "version",
            },
        ),
        "ProductResource": (
            {
                "id", "productCode", "name", "productType", "description",
                "displayOrder", "status", "skus", "version",
            },
            {"id", "productCode", "name", "productType", "status", "skus", "version"},
        ),
        "OrderItemResource": (
            {"skuId", "itemName", "quantity", "unitPriceCent", "subtotalAmountCent"},
            {"itemName", "quantity", "unitPriceCent", "subtotalAmountCent"},
        ),
        "OrderPriceSnapshotResource": (
            {
                "originalAmountCent", "discountAmountCent", "serviceFeeCent",
                "payableAmountCent", "ruleVersions",
            },
            {
                "originalAmountCent", "discountAmountCent", "serviceFeeCent",
                "payableAmountCent", "ruleVersions",
            },
        ),
        "NoRefundEvidenceResource": (
            {"confirmed", "agreementVersion", "confirmedAt"},
            {"confirmed", "agreementVersion"},
        ),
        "OrderResource": (
            {
                "orderNo", "userId", "orderType", "status", "currency",
                "items", "priceSnapshot", "noRefundEvidence",
                "paidAmountCent", "createdAt", "paidAt", "version",
            },
            {
                "orderNo", "userId", "orderType", "status", "currency",
                "items", "priceSnapshot", "noRefundEvidence",
                "createdAt", "version",
            },
        ),
    }
    required_shapes_by_spec = {
        "client": set(required_shapes) - {"ProductResource"},
        "admin": set(required_shapes),
    }
    for spec_name, spec in specs.items():
        schemas = spec["components"]["schemas"]
        for name in required_shapes_by_spec[spec_name]:
            properties, required = required_shapes[name]
            schema = schemas.get(name)
            if not schema:
                errors.append(f"{spec_name}:{name}:missing")
                continue
            if schema.get("additionalProperties") is not False:
                errors.append(f"{spec_name}:{name}:not-closed")
            if set(schema.get("properties", {})) != properties:
                errors.append(f"{spec_name}:{name}:properties")
            if set(schema.get("required", [])) != required:
                errors.append(f"{spec_name}:{name}:required")
        order_enum = set(
            schemas["OrderResource"]["properties"]["status"].get("enum", [])
        )
        if order_enum != ORDER_STATUS:
            errors.append(f"{spec_name}:OrderResource:ORDER_STATUS")
        if "ProductResource" in schemas and ref(schemas["ProductResource"]["properties"]["skus"]["items"]) != "#/components/schemas/ProductSkuResource":
            errors.append(f"{spec_name}:ProductResource:skus-ref")
        if ref(schemas["ProductSkuResource"]["properties"]["benefits"]["items"]) != "#/components/schemas/BenefitResource":
            errors.append(f"{spec_name}:ProductSkuResource:benefits-ref")

    responses = {
        "client": {
            "OrderGetMeOrdersResponse": ("list", "OrderResource"),
            "OrderGetMeOrdersByOrdernoResponse": ("detail", "OrderResource"),
        },
        "admin": {
            "AdminProductsGetProductsResponse": ("list", "ProductResource"),
            "AdminProductsPostProductsResponse": ("detail", "ProductResource"),
            "AdminProductsPatchProductsByIdResponse": ("detail", "ProductResource"),
            "AdminProductsGetSkusResponse": ("list", "ProductSkuResource"),
            "AdminProductsPostSkusResponse": ("detail", "ProductSkuResource"),
            "AdminProductsPatchSkusByIdResponse": ("detail", "ProductSkuResource"),
            "AdminOrdersGetOrdersResponse": ("list", "OrderResource"),
            "AdminOrdersGetOrdersByOrdernoResponse": ("detail", "OrderResource"),
        },
    }
    for spec_name, expected in responses.items():
        schemas = specs[spec_name]["components"]["schemas"]
        for response_name, (kind, resource) in expected.items():
            data = schemas[response_name]["properties"]["data"]
            if "oneOf" in data:
                errors.append(f"{spec_name}:{response_name}:oneOf")
            target = data["properties"]["items"]["items"] if kind == "list" else data
            if ref(target) != f"#/components/schemas/{resource}":
                errors.append(f"{spec_name}:{response_name}:resource")

    fields = read_csv("catalogs/ui_page_fields.csv")
    for row in fields:
        if row["页面ID"] == "ADM-ORDER-001" and "CommandResultResource" in row["数据来源"]:
            errors.append("ADM-ORDER-001:CommandResultResource")
        if row["页面ID"] == "ADM-PROD-001" and row["字段键"].startswith("sku.") and "ProductSkuResource" not in row["数据来源"]:
            errors.append(f"ADM-PROD-001:{row['字段键']}:resource")
    required_order_fields = {
        "items", "priceSnapshot.payableAmountCent",
        "noRefundEvidence.confirmed",
    }
    for page in ("SCR-ORDER-001", "SCR-ORDER-002", "ADM-ORDER-001"):
        present = {row["字段键"] for row in fields if row["页面ID"] == page}
        missing = required_order_fields - present
        if missing:
            errors.append(f"{page}:fields:{sorted(missing)}")

    visuals = {
        row["页面ID"]: row
        for row in read_csv("catalogs/ui_visual_acceptance.csv")
        if row["计划版本"] == "R16"
    }
    expected_visuals = {
        "SCR-ORDER-001": ("B08/P04", "EXACT"),
        "SCR-ORDER-002": (
            "SPEC:design/R16-UI-FROZEN/specs/SCR-ORDER-002.md",
            "APPROVED_SUPPLEMENT",
        ),
        "ADM-ORDER-001": (
            "SPEC:design/R16-UI-FROZEN/specs/ADM-ORDER-001.md",
            "APPROVED_SUPPLEMENT",
        ),
    }
    for page, expected in expected_visuals.items():
        row = visuals.get(page)
        if not row or (row["视觉来源"], row["覆盖状态"]) != expected:
            errors.append(f"{page}:visual")
        elif row["验收状态"] != "IN_REVIEW":
            errors.append(f"{page}:visual-premature-pass")
        if row and not all(token in row["业务过滤说明"] for token in ("示例",) if page != "ADM-ORDER-001"):
            errors.append(f"{page}:example-filter")

    for relative in (
        "design/R16-UI-FROZEN/VISUAL_COVERAGE_AUDIT.md",
        "design/R16-UI-FROZEN/specs/SCR-ORDER-001.md",
        "design/R16-UI-FROZEN/specs/SCR-ORDER-002.md",
        "design/R16-UI-FROZEN/specs/ADM-ORDER-001.md",
    ):
        if not (ROOT / relative).is_file():
            errors.append(f"{relative}:missing")

    payload = {
        "status": "PASS" if not errors else "FAIL",
        "release": "R16",
        "cr": "CR-0440",
        "verified_at": datetime.now(timezone.utc).isoformat(),
        "contracts": {
            "client_sha256": sha256("contracts/openapi.yaml"),
            "admin_sha256": sha256("contracts/admin-openapi.yaml"),
            "explicit_resources": sorted(required_shapes),
            "explicit_responses": sum(len(items) for items in responses.values()),
        },
        "visual_contracts": expected_visuals,
        "errors": errors,
    }
    if args.write_evidence:
        target = ROOT / "artifacts/validation/r16-entry/entry-contract.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )
    print("R16_ENTRY_OK " + json.dumps(payload, ensure_ascii=False, sort_keys=True))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
