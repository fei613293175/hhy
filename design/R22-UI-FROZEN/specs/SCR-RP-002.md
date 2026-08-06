# SCR-RP-002 红包任务资格视觉施工规格

## Binding

- Exact panels: B03/P01 for the detail header, media/content hierarchy, and fixed primary action; B09/P08 for the high-contrast amount and rule-summary block.
- Sources: `design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`, `design/effect-previews/B03/HHY_B03_MANIFEST.json`, `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`, `design/effect-previews/B09/HHY_B09_MANIFEST.json`.
- Product facts: `docs/02-ui/page-specs/android/SCR-RP-002_红包任务资格.md`, `catalogs/ui_page_fields.csv`, `catalogs/ui_page_states.csv`, `catalogs/ui_action_matrix.csv`, `releases/R22/STORIES.yaml`.

## Structure

1. Use a standard back/title row followed by a single activity context block; keep the B03/P01 top spacing and media-to-title transition.
2. Present eligibility, identity gate, inventory, required seconds, and current server version as clearly separated sections. The server status controls all actions.
3. Keep one fixed primary action and a secondary return action at the bottom. Disabled and checking states retain the same dimensions.
4. Use the B09/P08 amount treatment only for registered monetary fields; never copy its example wallet balance or reward rows.

## Product filtering

Show only fields in the page specification and generated API models. Do not expose internal risk rules, database keys, request IDs, or effect-image sample identities. Real-name requirements use user-facing reason text from the contract.

## State coverage

CHECKING, INELIGIBLE, READY, RUNNING, PAUSED, RESERVED, COMPLETED, EXPIRED, RISK_BLOCKED, ERROR, and OFFLINE retain stable geometry and explicit recovery actions from the state catalog.

## Evidence

Target runtime screenshot: `artifacts/reports/R22/visual/SCR-RP-002.png` at Pixel 7 / API 35 / 1080x2400. Compare the running page to the bound panels and this supplement before marking visual acceptance PASS.
