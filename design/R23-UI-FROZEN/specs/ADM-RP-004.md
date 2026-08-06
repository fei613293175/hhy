# ADM-RP-004 红包风险事件

- Visual source: existing admin security tokens plus B03 P08 invalid-state hierarchy; the source image is a structural reference only.
- Target viewport: desktop 1440x900; screenshot evidence: `artifacts/reports/R23/ui/ADM-RP-004.png`.
- Preserve: risk status banner, evidence/session/claim facts, masked sensitive data, and destructive-action confirmation modal.
- Actions map only to generated operationIds and require `redpacket.read` for reads plus the server-declared management permission for writes.
- States: loading, empty, partial failure, forbidden, 404, offline read-only, stale version, and successful audited resolution.
