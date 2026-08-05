# R20 Traceability Matrix

| Area | Contract/source | Implementation/evidence |
| --- | --- | --- |
| Android 红包广告主流程 | `SCR-RP-ADV-001..004`; R20 page/action/state catalogs | `R20RedPacketScreens.kt`; Pixel 7 emulator interaction and visual evidence |
| Admin 红包运营 | `ADM-RP-001`; `ADM-RP-003`; admin operation specs | `AdminRedPacketListPage.vue`; `AdminRedPacketReviewPage.vue`; 1440x900 visual evidence |
| API / data / state | R20 OpenAPI、迁移、状态机和 Release Manifest | Backend/PostgreSQL/Maven CI job passed |
| 配置 / 可观测性 | `config_registry.csv`; `infra/staging/r20-alerts.yml`; `check_r20_observability.py` | R20 observability gate passed in CI |
| UI 视觉合同 | `design/R20-UI-FROZEN/VISUAL_COVERAGE_AUDIT.md` | `check_ui_visual_acceptance.py --release R20` returns `pages=6` |
| Android 测试包 | `build-evidence.json`; `apk-evidence.md` | Stable V2 signed APK with matching remote/local SHA-256 |
