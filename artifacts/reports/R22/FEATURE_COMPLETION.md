# R22 feature completion comparison

Source scope: `releases/R22/STORIES.yaml`, `releases/R22/TASKS.yaml`, and requirement `REQ-RP-003`.

| Feature/story | Original requirement | Status | Evidence |
| --- | --- | --- | --- |
| STORY-R22-001 / SCR-RP-001 | Red-packet home list and four-category aggregation | IMPLEMENTED | R22 contract tests, CI Web/Android reports, `visual/SCR-RP-001.png`, public validation campaign list |
| STORY-R22-002 / SHEET-RP-001 | Claim panel with success/pending/error and reward account path | IMPLEMENTED | CI emulator tests, `visual/SHEET-RP-001.png`, public validation claim/idempotency and my-claims |
| STORY-R22-003 / SCR-RP-002, SCR-RP-003 | Identity gate, reservation, server-authoritative 20-second browsing, claim/cancel | IMPLEMENTED | `public-validation.json` (20 seconds, rejection, claim, cancel, stock/session facts), CI emulator report, runtime screenshots |
| STORY-R22-004 | Contract, migration, config, testing, observability and handoff | IMPLEMENTED | Flyway v055 deployment evidence, local 6/6 contract tests, CI backend/web/android/emulator PASS, reports in this directory |
| TASK-R22-005 | Duplicate/concurrency/timeout/message-duplicate/provider fault tests | IMPLEMENTED | CI backend and emulator suites; public idempotency conflict and replay evidence |
| TASK-R22-006 | Staging observability, trace/request IDs, metrics and rollback | IMPLEMENTED | public request IDs, migration/deployment and rollback report |
| TASK-R22-007 | Signed APK, identity and install/launch evidence | BLOCKED — physical-device acceptance is external | APK identity and CI emulator evidence exist in `APK_MANIFEST.md`; install/launch evidence on an owner device is absent |
| TASK-R22-008 | Version closure and stateless handoff | BLOCKED — depends on TASK-R22-007 | This evidence package is ready, but version closure cannot be claimed while the required physical-device gate remains blocked |

No R22 item is deferred. Physical-device verification is explicitly an external owner check and is not represented as PASS; therefore R22 itself remains open.
