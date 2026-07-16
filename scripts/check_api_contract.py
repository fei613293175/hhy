#!/usr/bin/env python3
from pathlib import Path
import csv,yaml,sys
R=Path(__file__).resolve().parents[1]
def rows(p):
 with p.open(encoding='utf-8-sig') as f:return list(csv.DictReader(f))
for csvn,yml in [('catalogs/api_endpoints.csv','contracts/openapi.yaml'),('catalogs/admin_api_endpoints.csv','contracts/admin-openapi.yaml')]:
 rr=rows(R/csvn);doc=yaml.safe_load((R/yml).read_text(encoding='utf-8'));missing=[]
 for x in rr:
  if x['路径'] not in doc['paths'] or x['方法'].lower() not in doc['paths'][x['路径']]:missing.append(x['方法']+' '+x['路径'])
 if missing: print('MISSING',csvn,missing[:20]);sys.exit(1)
print('API_CONTRACT_OK')
