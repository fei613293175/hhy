#!/usr/bin/env python3
from __future__ import annotations
import json
from pathlib import Path
from common import active_task, emit, input_json, repo_root

data=input_json(); repo=repo_root(data); task=active_task(repo)
if not task:
    emit({'continue':True,'hookSpecificOutput':{'hookEventName':'SessionStart','additionalContext':'Governance V5.0 authority mode: use governance/STATE.yaml and the Orchestrator; do not start a long /goal.'}})
    raise SystemExit(0)
source=str(data.get('source') or 'startup')
runtime=repo/'governance/runtime'; count_path=runtime/'compaction-count.json'
count=0
if count_path.is_file():
    try: count=int(json.loads(count_path.read_text(encoding='utf-8')).get('count',0))
    except Exception: count=0
if source=='compact':
    if count>=1:
        emit({'continue':False,'stopReason':'SECOND_COMPACTION_FORBIDDEN','systemMessage':'当前单次 Attempt 已发生第二次上下文压缩，必须终止并由 Orchestrator 记录有限失败，不得继续长思考。'})
        raise SystemExit(0)
    count=1; count_path.write_text(json.dumps({'count':count},ensure_ascii=False),encoding='utf-8')
context=(
 f"Governance V5.0 Worker context reloaded after {source}. "
 f"Only task {task.get('task_id')} release {task.get('release')} attempt {task.get('attempt')}/3. "
 f"Objective: {task.get('objective')}. Allowed paths: {', '.join(task.get('allowed_paths') or [])}. "
 "Do not select another task, edit governance/CI/Gate, run Git authority commands, or self-declare PASS. "
 "Only CANDIDATE_READY, ATTEMPT_FAILED, EXTERNAL_BLOCKED, or INFRASTRUCTURE_BLOCKED may end this attempt."
)
emit({'continue':True,'hookSpecificOutput':{'hookEventName':'SessionStart','additionalContext':context}})
