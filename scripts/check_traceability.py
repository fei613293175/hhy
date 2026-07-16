#!/usr/bin/env python3
from pathlib import Path
import csv,sys
R=Path(__file__).resolve().parents[1]
def rd(p):
 with p.open(encoding='utf-8-sig') as f:return list(csv.DictReader(f))
req={x['需求ID'] for x in rd(R/'catalogs/requirements_catalog.csv')};tr=rd(R/'catalogs/TRACEABILITY_MATRIX.csv');covered={x['需求ID'] for x in tr}
if req-covered:print('TRACE_MISSING_REQUIREMENTS',req-covered);sys.exit(1)
for x in tr:
 for k in ['版本','配置组','测试用例','任务清单']:
  if not x[k]:print('TRACE_EMPTY',x['需求ID'],k);sys.exit(1)
print('TRACE_OK',len(tr))
