from __future__ import annotations
import json, os, subprocess, sys
from pathlib import Path
from typing import Any

def input_json() -> dict[str, Any]:
    try: return json.load(sys.stdin)
    except Exception: return {}

def repo_root(data: dict[str, Any] | None = None) -> Path:
    cwd = Path((data or {}).get('cwd') or os.getcwd()).resolve()
    proc = subprocess.run(['git','rev-parse','--show-toplevel'],cwd=cwd,text=True,capture_output=True)
    return Path(proc.stdout.strip()).resolve() if proc.returncode==0 else cwd

def active_task(repo: Path) -> dict[str, Any] | None:
    path=repo/'governance/runtime/ACTIVE_TASK.json'
    if not path.is_file(): return None
    try: return json.loads(path.read_text(encoding='utf-8'))
    except Exception: return None

def worker_mode(repo: Path) -> bool:
    return os.environ.get('HHY_GOVERNANCE_ROLE')=='WORKER' or active_task(repo) is not None

def emit(value: dict[str, Any]) -> None:
    print(json.dumps(value,ensure_ascii=False))
