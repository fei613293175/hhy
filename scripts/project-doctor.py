#!/usr/bin/env python3
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parents[1]
raise SystemExit(subprocess.call([sys.executable,str(root/'scripts/check_v123_continuity.py'),'--strict',*sys.argv[1:]],cwd=root))
