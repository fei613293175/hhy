# SHEET-RP-001 红包领取面板视觉施工规格

## Binding

- Exact structural panel: B03/P06, the standard bottom-sheet composition with drag handle, title, explanation, field rows, and fixed primary action.
- Exact monetary density: B09/P08, limited to the registered reward summary fields.
- Sources: `design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`, `design/effect-previews/B03/HHY_B03_MANIFEST.json`, `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`, `design/effect-previews/B09/HHY_B09_MANIFEST.json`.
- Product facts: `docs/02-ui/page-specs/android/SHEET-RP-001_红包领取面板.md`, `catalogs/ui_page_fields.csv`, `catalogs/ui_page_states.csv`, `catalogs/ui_action_matrix.csv`, `releases/R22/STORIES.yaml`.

## Structure

1. Use a bottom sheet with a visible drag handle, title, short impact explanation, and a vertically scrollable content region.
2. Keep the primary claim action fixed above the safe-area inset and provide a clearly separate cancel/close action. The scrim cannot confirm the action.
3. Show the server-confirmed amount and reward-account summary with B09/P08 hierarchy, but do not copy example values or wallet navigation.
4. Preserve the same sheet height and action dimensions for OPEN, EDITING, SUBMITTING, SUCCESS, ERROR, INELIGIBLE, READY, RUNNING, RESERVED, COMPLETED, EXPIRED, RISK_BLOCKED, and OFFLINE.

## Product filtering

Only `id`, idempotency key handling, `clientNonce`, `finalHeartbeatSequence`, and the registered reward-account response fields may affect this sheet. Idempotency keys are transport behavior, not user-visible content. Clear one-time sensitive input after completion.

## Evidence

Target runtime screenshot: `artifacts/reports/R22/visual/SHEET-RP-001.png` at Pixel 7 / API 35 / 1080x2400. Runtime screenshot comparison must verify focus containment, back handling, fixed action placement, and no overlap.
