# SCR-RP-004 我的红包

- Visual source: `design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`, panels P01 (wallet overview) and P08 (red-packet balance/detail).
- Target viewport: Android 412x915 logical px; screenshot evidence: `artifacts/reports/R23/ui/SCR-RP-004.png`.
- Preserve: white Material surface, blue summary block, compact balance metrics, segmented history filter, dense ledger rows, fixed bottom navigation.
- Product mapping: only registered Pro reward account and red-packet claims; no merchant coins, fictitious identities, or withdrawal actions not owned by R23.
- States: loading, empty, partial refresh failure, unauthorized, offline read-only cache, stale-version conflict, and success navigation to the registered detail route.
- Actions: `redPacketGetMeRedPacketClaims` and `rewardGetMeRewardAccount`; all labels and fields come from the catalog and generated API types.
