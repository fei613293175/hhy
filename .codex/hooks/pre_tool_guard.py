#!/usr/bin/env python3
from __future__ import annotations
import json,re
from common import emit,input_json,repo_root,worker_mode

data=input_json(); repo=repo_root(data)
if not worker_mode(repo): raise SystemExit(0)
tool=str(data.get('tool_name') or '')
tool_input=data.get('tool_input') or {}
raw=json.dumps(tool_input,ensure_ascii=False) if not isinstance(tool_input,str) else tool_input
command=str(tool_input.get('command') or raw) if isinstance(tool_input,dict) else raw
low=command.lower().replace('\\','/')
reasons=[]
# Git authority mutations are Orchestrator-only.
if re.search(r'(^|[;&|\s])git\s+(?:add|commit|push|pull|merge|rebase|reset|tag|checkout|switch|worktree|cherry-pick|revert|clean|stash|branch\s+-[dD])\b',low):
    reasons.append('Worker may not execute Git authority mutations')
# Privileged governance transitions are never Worker commands.
if 'hhy_governance.py' in low and re.search(r'\b(?:activate-migration|run-once|run-loop|candidate-authorize|candidate-report|machine-close|owner-result|formal-release|unblock)\b',low):
    reasons.append('Worker may not invoke privileged governance transitions')
protected=(
 'agents.md','governance/','tools/governance/','.codex/','.github/workflows/','.githooks-v5/',
 'tests/governance_v5/','current_status.yaml','next_task.yaml','.continuity/','releases/',
 'design/effect-previews/'
)
# File-edit tools carry patch text in tool_input.command. Bash writes/redirections are also checked.
write_like=tool.lower() in {'apply_patch','edit','write'} or any(x in low for x in [' > ','>>','tee ','sed -i','rm ','mv ','cp '])
if write_like:
    for path in protected:
        if path in low:
            # releases/*/TASKS.yaml is protected; ordinary release docs remain editable.
            if path=='releases/' and 'tasks.yaml' not in low: continue
            reasons.append(f'protected path write: {path}')
if re.search(r'^\+.*(?:@disabled|@ignore|pytest\.mark\.skip|\.skip\s*\(|\bxit\s*\(|\bxdescribe\s*\()',command,re.I|re.M):
    reasons.append('test skip/disable additions are forbidden')
if reasons:
    emit({'hookSpecificOutput':{'hookEventName':'PreToolUse','permissionDecision':'deny','permissionDecisionReason':'; '.join(sorted(set(reasons)))}})
