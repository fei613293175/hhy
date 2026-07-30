#!/usr/bin/env python3
from __future__ import annotations
import json
from common import active_task,emit,input_json,repo_root,worker_mode

data=input_json(); repo=repo_root(data)
if not worker_mode(repo): emit({'continue':True}); raise SystemExit(0)
result=repo/'governance/runtime/worker-result.json'
allowed={'CANDIDATE_READY','ATTEMPT_FAILED','EXTERNAL_BLOCKED','INFRASTRUCTURE_BLOCKED'}
try:
    payload=json.loads(result.read_text(encoding='utf-8'))
    valid=payload.get('status') in allowed and payload.get('task_id')==(active_task(repo) or {}).get('task_id')
except Exception: valid=False
if valid: emit({'continue':True})
else: emit({'continue':False,'stopReason':'WORKER_RESULT_MISSING_OR_INVALID','systemMessage':'Worker 未生成绑定当前 Task 的合规 worker-result.json；结束本次 Attempt，不自动续写或重新思考。'})
