#!/usr/bin/env python3
from pathlib import Path
import re, sys
import yaml
from continuity_lib import current_session, git_changed_files, latest_checkpoint, load_session, expected_commit_trailers, root_from_script
ROOT=root_from_script(__file__)

def choose_session():
    active=current_session(ROOT)
    if active: return active
    candidates=[]
    for rel in git_changed_files(ROOT,staged=True):
        m=re.fullmatch(r"\.continuity/sessions/(SES-[^/]+)\.yaml",rel)
        if m and (ROOT/rel).is_file():
            row=yaml.safe_load((ROOT/rel).read_text(encoding='utf-8')) or {}
            candidates.append(row)
    candidates.sort(key=lambda x:x.get('updated_at') or x.get('started_at') or '')
    return candidates[-1] if candidates else None

def main():
    if len(sys.argv)<2: return 0
    path=Path(sys.argv[1]); source=sys.argv[2] if len(sys.argv)>2 else ''
    if source in {'merge','squash','commit'}: return 0
    session=choose_session()
    if not session: return 0
    cp=latest_checkpoint(ROOT,session)
    if not cp: return 0
    text=path.read_text(encoding='utf-8')
    anchor=session.get('story_id') or session.get('task_id')
    lines=text.splitlines()
    for index,line in enumerate(lines):
        if line.strip() and not line.lstrip().startswith('#'):
            if anchor and not line.startswith(f'[{anchor}] '):
                lines[index]=f'[{anchor}] {line}'
            break
    text='\n'.join(lines) + ('\n' if text.endswith('\n') else '')
    trailers=expected_commit_trailers(session,cp)
    existing={m.group(1).lower() for m in re.finditer(r'^([A-Za-z][A-Za-z0-9-]+):\s*',text,re.M)}
    lines=[]
    for key,value in trailers.items():
        if key.lower() not in existing: lines.append(f'{key}: {value}')
    if lines:
        path.write_text(text.rstrip()+'\n\n'+'\n'.join(lines)+'\n',encoding='utf-8')
    return 0
if __name__=='__main__': raise SystemExit(main())
