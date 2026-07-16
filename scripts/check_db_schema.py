#!/usr/bin/env python3
from pathlib import Path
import csv,sys
R=Path(__file__).resolve().parents[1]
def rd(p):
 with p.open(encoding='utf-8-sig') as f:return list(csv.DictReader(f))
t={x['表名'] for x in rd(R/'catalogs/data_tables.csv')};s={x['表名'] for x in rd(R/'database/schema_dictionary.csv')};m=t-s
if m:print('DB_SCHEMA_MISSING',sorted(m));sys.exit(1)
print('DB_SCHEMA_OK',len(t))
