# R22 test checklist

| Test area | Required test/evidence | Environment | Result | Evidence |
| --- | --- | --- | --- | --- |
| Contract | R22 contract suite, 6 tests | CI/backend test runner | PASS | R22 contract report / commit e10d705 |
| Backend | Backend integration and migration tests | GitHub Actions | PASS | backend workflow artifact |
| Web products | Web/API product checks | GitHub Actions | PASS | web-products workflow artifact |
| Android build/unit | compile, unit and signed staging APK | GitHub Actions / coordinator | PASS | APK manifest |
| Emulator journey | 46 tests, 0 failures | GitHub Actions Pixel emulator | PASS | emulator workflow artifact |
| Public path | status, invite, registration, identity, list/detail, session, heartbeat, claim, cancel, my claims | `https://api.orbexa.cc` owner-test persistent DB | PASS | `public-validation.json` |
| Server timer | client elapsed ignored; accumulated exactly 20 seconds | Public owner-test | PASS | `public-validation.json` server_timer |
| Idempotency | session/claim/cancel replay and start/claim conflicts | Public owner-test | PASS | `public-validation.json` |
| Database facts | Flyway 055, stock/session/claim facts | Public PostgreSQL | PASS | `public-validation.json` database_facts |
| Visual | four bound page screenshots compared | GitHub Actions Pixel 7 API 35 | PASS | `visual/SCREENSHOT_COMPARISON.md` and PNGs |
| Physical device | install/launch and owner acceptance | Owner physical device | PASS | `OWNER_DEVICE_ACCEPTANCE.md`; owner attestation dated 2026-08-06 |
