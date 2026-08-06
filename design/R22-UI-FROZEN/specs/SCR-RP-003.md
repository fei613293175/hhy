# SCR-RP-003 红包浏览任务视觉施工规格

## Binding

- Exact panels: B03/P01 for the detail/media hierarchy; B09/P08 for monetary/detail hierarchy and the fixed bottom action.
- Sources: `design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`, `design/effect-previews/B03/HHY_B03_MANIFEST.json`, `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`, `design/effect-previews/B09/HHY_B09_MANIFEST.json`.
- Product facts: `docs/02-ui/page-specs/android/SCR-RP-003_红包浏览任务.md`, `catalogs/ui_page_fields.csv`, `catalogs/ui_page_states.csv`, `catalogs/ui_action_matrix.csv`, `releases/R22/STORIES.yaml`.

## Structure

1. Keep the title/back row and content context at the top, with a stable task summary block below.
2. Make server time, remaining seconds, visibility/foreground status, heartbeat health, and the current task state visually dominant and easy to scan.
3. Use a compact countdown indicator or floating timer only as a presentation of server progress. Local time must never decide eligibility or reward.
4. Keep the fixed primary action area stable across start, heartbeat, reservation, claim, cancel, error, and paused states.

## Product filtering

Use only fields and actions registered for `SCR-RP-003`; filter B03/B09 sample people, images, amounts, labels, and unrelated controls. Never reveal anti-cheat thresholds or internal event payloads.

## State coverage

CHECKING, INELIGIBLE, READY, RUNNING, PAUSED, RESERVED, COMPLETED, EXPIRED, RISK_BLOCKED, ERROR, and OFFLINE must each have a visible, recoverable representation. Background/visibility changes pause or invalidate the server session according to the API response.

## Evidence

Target runtime screenshot: `artifacts/reports/R22/visual/SCR-RP-003.png` at Pixel 7 / API 35 / 1080x2400. Visual acceptance requires runtime screenshot comparison and no overlap in the timer or fixed action area.
