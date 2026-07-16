#!/usr/bin/env python3
from pathlib import Path
import json,csv,sys
R=Path(__file__).resolve().parents[1];t=json.loads((R/'design/tokens/hhy_design_tokens_v1.2.2.json').read_text(encoding='utf-8'))
for k in ['borderWidthDp','elevationDp','shadow','opacity','motionMs','componentDp','imageRatio','breakpointsPx','adminWeb']:
 if k not in t:print('TOKEN_MISSING',k);sys.exit(1)
with (R/'design/component-catalog.csv').open(encoding='utf-8-sig') as f:c=list(csv.DictReader(f))
if len(c)<30:print('COMPONENT_CATALOG_TOO_SMALL');sys.exit(1)
print('UI_TOKENS_OK',len(c))
