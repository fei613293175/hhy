#!/usr/bin/env python3
from pathlib import Path
import csv,sys
R=Path(__file__).resolve().parents[1]
def rd(p):
 with p.open(encoding='utf-8-sig') as f:return list(csv.DictReader(f))
expected={('ANDROID',x['ID']) for x in rd(R/'catalogs/android_screens.csv')}|{('H5',x['ID']) for x in rd(R/'catalogs/h5_screens.csv')}|{('ADMIN',x['ID']) for x in rd(R/'catalogs/admin_pages.csv')}
rows=rd(R/'catalogs/frontend_backend_matrix.csv');got={(x['平台'],x['UI_ID']) for x in rows}
if expected-got:print('FRONT_BACK_MISSING',sorted(expected-got)[:20]);sys.exit(1)
for x in rows:
 for k in ['需求ID','API契约','数据表','配置组','测试ID']:
  if not x[k]:print('FRONT_BACK_EMPTY',x['UI_ID'],k);sys.exit(1)
print('FRONT_BACK_OK',len(rows))
