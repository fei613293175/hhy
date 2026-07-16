#!/usr/bin/env python3
from pathlib import Path
import os, subprocess, sys
root=Path(__file__).resolve().parents[1]
if not (root/'.git').exists():
    print('Git尚未初始化；先运行 continuity.py bootstrap --init-git',file=sys.stderr); raise SystemExit(2)
for hook in (root/'.githooks').glob('*'):
    if hook.is_file(): os.chmod(hook,0o755)
subprocess.run(['git','config','core.hooksPath','.githooks'],cwd=root,check=True)
subprocess.run(['git','config','commit.template','.gitmessage'],cwd=root,check=True)
print('Git hooks installed: .githooks')
