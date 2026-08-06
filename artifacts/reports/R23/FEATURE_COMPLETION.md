# R23 功能完成对比

| 原始项目 | 状态 | 证据/原因 |
|---|---|---|
| REQ-RP-006 账本与反作弊 | PARTIALLY IMPLEMENTED | R20/R22 red-packet services and immutable ledger schema exist; R23 risk data, recovery tests and evidence remain unverified |
| REQ-ANALYTICS-001 统一指标 | PARTIALLY IMPLEMENTED | analytics_events/daily_kpis schema and traffic-type traceability exist; no R23 aggregation/service test evidence yet |
| STORY-R23-001 ADM-RP-002 | PARTIALLY IMPLEMENTED | generated API service and route binding exist; detail/session/claim UI and screenshots remain unverified |
| STORY-R23-002 ADM-RP-004 | PARTIALLY IMPLEMENTED | risk/review route binding and existing review UI exist; risk-event data workflow and screenshots remain unverified |
| STORY-R23-003 SCR-RP-004 | BLOCKED | APIs exist and visual contract is frozen, but the Android wallet screen and screenshot are absent |
| STORY-R23-004 工程治理与交接 | PARTIALLY IMPLEMENTED | feature list, checklist and UI binding matrix added; remote evidence and final handoff are pending |
| Android APK | BLOCKED UNTIL REMOTE BUILD | Must be produced by GitHub Actions/obx-test coordinator; no local Android build allowed |
