#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,re,subprocess
from common import active_task,emit,input_json,repo_root,worker_mode

def digest(text:str)->str: return hashlib.sha256(text.encode('utf-8',errors='replace')).hexdigest()
def normalize(value:str)->str:
    value=re.sub(r'\b\d{4}-\d{2}-\d{2}[T ][0-9:.+Z-]+\b','<TIME>',value)
    value=re.sub(r'/tmp/[A-Za-z0-9_./-]+','<TMP>',value)
    value=re.sub(r'[A-Fa-f0-9]{40}','<COMMIT>',value)
    return value[-20000:]

data=input_json(); repo=repo_root(data)
if not worker_mode(repo): raise SystemExit(0)
task=active_task(repo) or {}; runtime=repo/'governance/runtime'; runtime.mkdir(parents=True,exist_ok=True)
ledger=runtime/'command-ledger.jsonl'
tool_input=data.get('tool_input') or {}; tool_response=data.get('tool_response')
command=json.dumps({'tool':data.get('tool_name'),'input':tool_input},ensure_ascii=False,sort_keys=True)
response=normalize(json.dumps(tool_response,ensure_ascii=False,sort_keys=True,default=str))
proc=subprocess.run(['git','diff','--no-ext-diff','--binary'],cwd=repo,text=True,capture_output=True)
diff_hash=digest(proc.stdout)
row={'command_hash':digest(command),'output_hash':digest(response),'diff_hash':diff_hash,'tool_use_id':data.get('tool_use_id')}
previous=[]
if ledger.is_file():
    for line in ledger.read_text(encoding='utf-8',errors='replace').splitlines():
        try: previous.append(json.loads(line))
        except Exception: pass
count=sum(1 for old in previous if all(old.get(k)==row[k] for k in ('command_hash','output_hash','diff_hash')))
with ledger.open('a',encoding='utf-8') as f: f.write(json.dumps(row,ensure_ascii=False)+'\n')
max_calls=int((task.get('limits') or {}).get('max_tool_calls',100))
if len(previous)+1>=max_calls:
    emit({'continue':False,'stopReason':'WORKER_TOOL_CALL_BUDGET_EXHAUSTED','systemMessage':'工具调用预算已耗尽；终止本次 Attempt，由 Orchestrator 记录 ATTEMPT_FAILED。'})
elif count>=1:
    emit({'continue':False,'stopReason':'REPEATED_COMMAND_OUTPUT_DIFF','systemMessage':'检测到相同工具输入、相同输出和相同 Git Diff 的第二次重复；终止本次 Attempt，禁止原地循环。'})
