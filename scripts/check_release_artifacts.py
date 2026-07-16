#!/usr/bin/env python3
from pathlib import Path
import csv,yaml,sys
R=Path(__file__).resolve().parents[1]
with (R/'catalogs/release_plan.csv').open(encoding='utf-8-sig') as f:rows=list(csv.DictReader(f))
for x in rows:
 d=R/'releases'/x['版本']
 for n in ['RELEASE_MANIFEST.yaml','TASKS.yaml','ACCEPTANCE_MATRIX.csv']:
  if not (d/n).exists():print('RELEASE_ARTIFACT_MISSING',x['版本'],n);sys.exit(1)
 m=yaml.safe_load((d/'RELEASE_MANIFEST.yaml').read_text(encoding='utf-8'))
 if not m.get('requirements'):print('RELEASE_REQUIREMENTS_EMPTY',x['版本']);sys.exit(1)
 if (x['Android测试APK']=='YES') != bool(m.get('android_test_apk_required')):print('APK_POLICY_MISMATCH',x['版本']);sys.exit(1)
print('RELEASE_ARTIFACTS_OK',len(rows))
