#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.governance.gov50.hard import run_hard_protection

def main()->int:
 p=argparse.ArgumentParser(); p.add_argument('--task',required=True); p.add_argument('--profile',default='task'); a=p.parse_args()
 result=run_hard_protection(ROOT,a.task,a.profile)
 print(json.dumps(result,ensure_ascii=False,indent=2))
 return 0 if result['status']=='PASS' else 1
if __name__=='__main__': raise SystemExit(main())
