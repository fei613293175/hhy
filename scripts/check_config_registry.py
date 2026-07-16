#!/usr/bin/env python3
from pathlib import Path
import yaml,sys
R=Path(__file__).resolve().parents[1];d=yaml.safe_load((R/'config/CONFIG_REGISTRY.yaml').read_text(encoding='utf-8'));items=d['items'];keys=[x['key'] for x in items]
if len(keys)!=len(set(keys)):print('DUP_CONFIG_KEYS');sys.exit(1)
for x in items:
 if x.get('secret') and x.get('default') not in ('',None,'PROTECTED_CI_SECRET','GENERATE_AT_P00'):print('SECRET_DEFAULT_FORBIDDEN',x['key']);sys.exit(1)
print('CONFIG_OK',len(items))
