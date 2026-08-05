# R21 Traceability Matrix

| Area | Contract/source | Implementation/evidence |
| --- | --- | --- |
| Payment activation | `REQ-RP-002`; R21 stories and operation map | `R20RedPacketService`; payment quote/order flow; Backend CI PASS |
| Increase remaining unit price | `REQ-RP-005`; amount/version rules | `R20RedPacketPostgresStore`; `red_packet_campaign_versions`, `red_packet_quotes`, `red_packet_orders`; Backend CI PASS |
| Pause/resume/close | R21 lifecycle state and idempotency contract | `R20RedPacketService` and `R20RedPacketPostgresStore`; Android action mapping; emulator PASS |
| Analytics and detail | `SCR-RP-ADV-005`; campaign analytics contract | `R21RedPacketScreens.kt`; runtime screenshot and Android emulator evidence |
| Increase amount page | `SCR-RP-ADV-006`; quote/order contract | `R21RedPacketScreens.kt`; runtime screenshot and Android emulator evidence |
| Database/invariants | R21 migration and release database table list | Backend/PostgreSQL job in Run 31037022921 |
| Observability | R21 observability checks and outbox events | Backend job in Run 31037022921; `test-evidence.md` |
| APK identity | fixed `obx-test` toolchain and signing contract | `build-evidence.json`, `apk-evidence.md`, `.scratch/R21-obx-final/evidence/` |
| UI visual contract | `design/R21-UI-FROZEN/specs/*`; `catalogs/ui_visual_acceptance.csv` | `visual-evidence.md`; `visual/SCR-RP-ADV-005.png`; `visual/SCR-RP-ADV-006.png` |
