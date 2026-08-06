# SCR-RP-001 红包首页视觉施工规格

## Binding

- Exact panels: B03/P01 for the top detail/media hierarchy; B09/P01 for the blue account-summary and compact function density; B09/P08 for the orange red-packet amount block and transaction rows.
- Sources: `design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`, `design/effect-previews/B03/HHY_B03_MANIFEST.json`, `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`, `design/effect-previews/B09/HHY_B09_MANIFEST.json`.
- Product facts: `docs/02-ui/page-specs/android/SCR-RP-001_红包首页.md`, `catalogs/ui_page_fields.csv`, `catalogs/ui_page_states.csv`, `catalogs/ui_action_matrix.csv`, `releases/R22/STORIES.yaml`.

## Structure

1. Keep the app top bar, compact page title, and a clear list/filter region in the upper viewport.
2. Use a prominent but compact red-packet summary block with amount, remaining count, required seconds, and status. Keep the amount hierarchy from B09/P08 without copying its example amounts.
3. Render campaigns as dense, scannable rows with status, count, amount, and time. Use B03/P01 section separators and fixed-width action alignment.
4. Keep the primary entry action in the registered fixed bottom action area; loading and partial-failure layouts reserve the same space.
5. Use a five-item app navigation shell only when the existing shell route supplies it. Do not add a new navigation item for R22.

## Product filtering

Only fields and actions registered for `SCR-RP-001` may be shown. Replace all effect-image names, avatars, amounts, IDs, timestamps, badges, and unregistered buttons with live API fields or omit them. Technical identifiers, request IDs, and feature flags stay out of the normal content view.

## State coverage

The implementation must preserve the same geometry for LOADING, CONTENT, PARTIAL_CONTENT, REFRESHING, ERROR, INELIGIBLE, READY, RUNNING, RESERVED, COMPLETED, EXPIRED, RISK_BLOCKED, and OFFLINE. State-specific copy and controls come from `ui_page_states.csv`; no state may be represented by a blank page.

## Evidence

Target runtime screenshot: `artifacts/reports/R22/visual/SCR-RP-001.png` at Pixel 7 / API 35 / 1080x2400. Visual acceptance requires a real running screenshot comparison for hierarchy, density, business-field mapping, state coverage, and overlap.
