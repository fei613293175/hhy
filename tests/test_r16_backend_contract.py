#!/usr/bin/env python3
"""Strict static guard for the R16 product/SKU/order backend vertical slice."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
COMMERCE = ROOT / "services/backend/commerce/src/main/java/cc/orbexa/hhy/commerce"
ADMIN = (
    ROOT
    / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/admin"
    / "R16CommerceAdminController.java"
)
USER = (
    ROOT
    / "services/backend/boot/src/main/java/cc/orbexa/hhy/boot/user"
    / "R16OrderController.java"
)


def require(condition: bool, code: str) -> None:
    if not condition:
        raise SystemExit(f"R16_BACKEND_CONTRACT_FAIL {code}")


def main() -> int:
    expected = {
        "orderGetMeOrders",
        "orderGetMeOrdersByOrderno",
        "adminProductsGetProducts",
        "adminProductsPostProducts",
        "adminProductsPatchProductsById",
        "adminProductsGetSkus",
        "adminProductsPostSkus",
        "adminProductsPatchSkusById",
        "adminOrdersGetOrders",
        "adminOrdersGetOrdersByOrderno",
    }
    sources = [path.read_text(encoding="utf-8") for path in (ADMIN, USER)]
    joined = "\n".join(sources)
    for operation_id in expected:
        require(
            len(re.findall(rf"\b{re.escape(operation_id)}\s*\(", joined)) == 1,
            f"OPERATION_ID_{operation_id}",
        )

    require("hasAuthority('product.read')" in sources[0], "PRODUCT_READ_AUTHORITY")
    require("hasAuthority('product.write')" in sources[0], "PRODUCT_WRITE_AUTHORITY")
    require("hasAuthority('order.read')" in sources[0], "ORDER_READ_AUTHORITY")
    require('@RequestHeader("X-Idempotency-Key")' in sources[0], "IDEMPOTENCY_HEADER")

    service = (COMMERCE / "R16CommerceService.java").read_text(encoding="utf-8")
    store = (COMMERCE / "R16CommercePostgresStore.java").read_text(encoding="utf-8")
    require('"r16adm:" + encoded' in service, "FIXED_SCOPE_PREFIX")
    require("writeInt(encoded.length)" in service, "LENGTH_PREFIX_SCOPE")
    require('canonical.put("adminId", actor.adminId())' in service, "REQUEST_HASH_ADMIN")
    require('canonical.put("operationId", actor.operationId())' in service, "REQUEST_HASH_OPERATION")
    require('required(benefit.name(), 255, "权益名称")' in service, "BENEFIT_NAME_255")
    require('optional(benefit.unit(), 64, "权益单位")' in service, "BENEFIT_UNIT_64")
    require("durationDays != null && durationDays < 0" in service, "DURATION_ZERO_ALLOWED")
    require("FOR UPDATE" in store, "ROW_LOCK")
    require("owner.user_id=?" in store or "orders.user_id=?" in store, "ORDER_OWNER_SQL")
    require("CONTRACT_LIMIT" in store and ">= CONTRACT_LIMIT" in store, "SKU_LIMIT")
    require("response_payload_ciphertext" in store, "ENCRYPTED_REPLAY")
    require("admin_operation_logs" in store, "AUDIT")
    require("outbox_events" in store, "OUTBOX")

    for path in COMMERCE.glob("R16Commerce*.java"):
        text = path.read_text(encoding="utf-8")
        require("cc.orbexa.hhy.access" not in text, f"MODULE_ACCESS_{path.name}")
        require("cc.orbexa.hhy.boot" not in text, f"MODULE_BOOT_{path.name}")

    source_migration = (
        ROOT / "database/migrations/V046__r16_product_permission_alignment.sql"
    ).read_bytes()
    runtime_migration = (
        ROOT
        / "services/backend/boot/src/main/resources/db/migration"
        / "V046__r16_product_permission_alignment.sql"
    ).read_bytes()
    require(source_migration == runtime_migration, "V046_RUNTIME_HASH")
    migration_text = source_migration.decode("utf-8")
    for permission in ("product.read", "product.write", "product.manage", "SUPER_ADMIN"):
        require(permission in migration_text, f"V046_{permission}")

    v047_source = (
        ROOT / "database/migrations/V047__r16_commerce_contract_alignment.sql"
    ).read_bytes()
    v047_runtime = (
        ROOT
        / "services/backend/boot/src/main/resources/db/migration"
        / "V047__r16_commerce_contract_alignment.sql"
    ).read_bytes()
    require(v047_source == v047_runtime, "V047_RUNTIME_HASH")
    v047 = v047_source.decode("utf-8")
    for marker in ("NOT BETWEEN 1 AND 255", "> 64", "duration_days >= 0"):
        require(marker in v047, f"V047_{marker}")

    print("R16_BACKEND_CONTRACT_OK operationIds=10 ownerIsolation=SQL moduleBoundary=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
