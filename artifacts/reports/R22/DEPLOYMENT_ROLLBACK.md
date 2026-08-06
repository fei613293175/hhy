# R22 deployment and rollback evidence

## Deployment

- Owner-test database backup before migration: `/root/hhy-owner-test/backups/pre-R22-migration-20260806T091842Z.dump` (mode 600).
- Flyway target changed from 052 to 055; migrations 053, 054 and 055 applied successfully.
- New API container: `hhy-owner-test-api-e10d705b055`, `127.0.0.1:28157`, healthy, restart count 0, source label `e10d705bebd5bfdae9fa8ef2c9b1bff3fc764508`.
- Nginx backup: `/www/server/panel/vhost/nginx/api.orbexa.cc.conf.pre-R22-migration-20260806T092100Z`; public upstream switched to 28157.
- Public status endpoint returned 200 with `redPacket:true`; full business path is in `public-validation.json`.

## Rollback

- Previous API container `hhy-owner-test-api-e10d705b` remains on port 28156 as rollback candidate.
- Restore Nginx from the dated backup, switch upstream to 28156, stop the new container only after health and public-path verification, and restore the PostgreSQL dump only if data rollback is explicitly required.
- No destructive cleanup of unrelated data or services was performed.
