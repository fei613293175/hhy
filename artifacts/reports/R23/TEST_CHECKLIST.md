# R23 测试清单

| Test | Result | Environment/evidence |
|---|---|---|
| TST-ANALYTICS_001-HAPPY | PASS | R23 analytics contract + Backend job 31106841391 |
| TST-ANALYTICS_001-IDEMPOTENT | PASS | Idempotency contract + PostgreSQL Maven suite, Run 31106841391 |
| TST-ANALYTICS_001-REJECT | PASS | Validation/rejection assertions, Run 31106841391 |
| TST-RP_006-CONCURRENCY | PASS | PostgreSQL-backed Maven suite, 543 tests, Run 31106841391 |
| TST-RP_006-HAPPY | PASS | Session/claim/ledger happy path, Run 31106841391 |
| TST-RP_006-IDEMPOTENT | PASS | Claim and analytics idempotency, Run 31106841391 |
| TST-RP_006-RECOVERY | PASS | Recovery/rollback assertions, Run 31106841391 |
| TST-RP_006-REJECT | PASS | Permission/version/invalid-state rejection, Run 31106841391 |
| TST-V122-009 | PASS | Web visual capture + Android API 36 emulator, Run 31106841391 |
| AC-R23-001..006 | PASS | Acceptance matrix closed against CI, screenshots, APK metadata and desktop package |
