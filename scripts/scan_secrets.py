#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.governance.gov50.secrets import scan_secrets

def main() -> int:
    p=argparse.ArgumentParser(description='HHY Governance V5 secret scanner')
    p.add_argument('--repo','--root',dest='repo',default=str(ROOT))
    p.add_argument('--full',action='store_true')
    p.add_argument('--include-untracked',action='store_true')
    p.add_argument('--json-out')
    a=p.parse_args()
    result=scan_secrets(Path(a.repo).resolve(),full=a.full,include_untracked=a.include_untracked)
    text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
    if a.json_out:
        path=Path(a.json_out); path.parent.mkdir(parents=True,exist_ok=True); path.write_text(text,encoding='utf-8')
    print(text,end='')
    return 0 if result['status']=='PASS' else 1
if __name__=='__main__': raise SystemExit(main())
