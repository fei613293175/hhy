#!/usr/bin/env python3
from pathlib import Path
import yaml,argparse,socket
P=argparse.ArgumentParser();P.add_argument('--verify',action='store_true');a=P.parse_args();R=Path(__file__).resolve().parents[1];d=yaml.safe_load((R/'config/DOMAIN_PLAN.yaml').read_text(encoding='utf-8'));lines=['# orbexa.cc DNS待办','']
for x in d['records']:
 status=x['status'];resolved=''
 if a.verify:
  try:resolved=socket.gethostbyname(x['host']);status='RESOLVED'
  except Exception:status='PENDING_USER_DNS'
 lines.append(f"- [{ 'x' if status=='RESOLVED' else ' ' }] {x['host']} | {x['record_type']} | 目标：{x['target']} | {x['purpose']}"+(f' | {resolved}' if resolved else ''))
(R/'PENDING_USER_ACTIONS_DNS.md').write_text('\n'.join(lines)+'\n',encoding='utf-8');print(R/'PENDING_USER_ACTIONS_DNS.md')
