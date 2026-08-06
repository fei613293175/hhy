# R22 runtime visual comparison

Environment: GitHub Actions Android emulator, Pixel 7 profile, API 35, 1080x2400. Each image was generated from the running R22 APK and manually compared with the exact source panel and frozen supplement listed in the UI binding matrix.

| Page | Runtime evidence | Comparison result |
| --- | --- | --- |
| SCR-RP-001 | `SCR-RP-001.png` | PASS: hierarchy, list density, amount masking and navigation shell align; no overlap |
| SCR-RP-002 | `SCR-RP-002.png` | PASS: eligibility states and fixed primary action align; no overlap |
| SCR-RP-003 | `SCR-RP-003.png` | PASS: server timer/floating indicator, detail hierarchy and fixed action align; no overlap |
| SHEET-RP-001 | `SHEET-RP-001.png` | PASS: bottom-sheet focus containment, monetary summary and action placement align; no overlap |

H5 and Admin have no R22 pages in the registered story backlog and are N/A.
