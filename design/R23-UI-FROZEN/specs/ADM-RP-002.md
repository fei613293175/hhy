# ADM-RP-002 红包活动详情

- Visual source: existing admin design tokens plus B03 P01 detail hierarchy; the source image is a structural reference only.
- Target viewport: desktop 1440x900; screenshot evidence: `artifacts/reports/R23/ui/ADM-RP-002.png`.
- Preserve: filterable table/detail split, status chip, masked owner, ledger/session/claim sections, and a fixed action rail.
- Actions map only to generated operationIds: list, detail, review, pause, resume, terminate, sessions, claims.
- States: loading, empty, local section failure, forbidden, 404, offline read-only, version conflict, and confirmed mutation with audit notice.
