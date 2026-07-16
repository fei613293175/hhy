#!/usr/bin/env python3
from pathlib import Path
import json, hashlib, sys
root=Path(__file__).resolve().parent.parent
errors=[]
for i in range(1,13):
    b=root/f"design/effect-previews/B{i:02d}"
    m=b/f"HHY_B{i:02d}_MANIFEST.json"
    if not m.exists(): errors.append(f"missing {m}"); continue
    data=json.loads(m.read_text(encoding='utf-8'))
    img=b/data['file']
    if not img.exists(): errors.append(f"missing {img}"); continue
    actual=hashlib.sha256(img.read_bytes()).hexdigest()
    if actual!=data['sha256']: errors.append(f"hash mismatch {img}")
if errors:
    print("\n".join(errors)); sys.exit(1)
print("12 UI reference batches: OK")
