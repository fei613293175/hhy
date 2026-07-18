#!/usr/bin/env python3
"""真实 Git/Handoff/Context 无对话重建演练。

该脚本调用完整生命周期演练，再从生成的 Handoff ZIP 克隆 Git Bundle、
应用 WIP Patch、恢复安全未跟踪文件、重算项目指纹，并验证 Manifest/Event 篡改可被拒绝。
"""
from __future__ import annotations
from argparse import ArgumentParser
from pathlib import Path
import hashlib, json, os, shutil, subprocess, sys, tarfile, tempfile, zipfile
sys.dont_write_bytecode = True
import yaml

ROOT=Path(__file__).resolve().parents[1]
DEFAULT_REPORT=ROOT/'artifacts/validation/continuity-integration-v1.2.3.json'
DEFAULT_LOG=ROOT/'artifacts/validation/continuity-integration-v1.2.3.log'

def sha(path:Path)->str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()

def source_manifest():
    names=['.continuity/CONTINUITY_POLICY.yaml','config/REPOSITORY_TRANSPORT.yaml','config/DEVELOPMENT_RUNTIME.yaml','scripts/continuity_lib.py','scripts/continuity.py','scripts/continuity_gate.py','scripts/restore_git_transport.py','scripts/select_execution_profile.py','scripts/verify_cloud_environment.py','scripts/prepare_commit_message.py','scripts/run_continuity_self_test.py','scripts/test_continuity_protocol.py','.githooks/pre-commit','.githooks/prepare-commit-msg','.githooks/commit-msg','.githooks/pre-push']
    return [{'path':n,'sha256':sha(ROOT/n)} for n in names if (ROOT/n).is_file()]

def run(args,cwd,env,timeout=1200):
    return subprocess.run(args,cwd=cwd,env=env,text=True,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)

def verify_manifest(directory:Path):
    errors=[]; count=0
    manifest=directory/'MANIFEST_SHA256.txt'
    if not manifest.is_file():return False,0,['manifest missing']
    for line in manifest.read_text(encoding='utf-8').splitlines():
        if not line.strip():continue
        count+=1
        try:digest,rel=line.split('  ',1)
        except ValueError:errors.append('bad line:'+line);continue
        p=directory/rel
        if not p.is_file():errors.append('missing:'+rel)
        elif sha(p)!=digest:errors.append('hash:'+rel)
    return not errors,count,errors

def main():
    ap=ArgumentParser(); ap.add_argument('--report',default=str(DEFAULT_REPORT)); ap.add_argument('--log',default=str(DEFAULT_LOG)); ap.add_argument('--workdir'); ap.add_argument('--keep-temp',action='store_true'); args=ap.parse_args()
    report=Path(args.report).resolve(); log=Path(args.log).resolve()
    base=Path(args.workdir).resolve() if args.workdir else Path(tempfile.mkdtemp(prefix='hhy-v123-reconstruct-'))
    created=not args.workdir; life=base/'lifecycle'; env=dict(os.environ); env.update({'PYTHONDONTWRITEBYTECODE':'1','LC_ALL':'C.UTF-8','LANG':'C.UTF-8','GIT_TERMINAL_PROMPT':'0'})
    checks=[]; commands=[]
    try:
        life_report=ROOT/'artifacts/validation/continuity-lifecycle-integration-v1.2.3.json'
        life_log=ROOT/'artifacts/validation/continuity-lifecycle-integration-v1.2.3.log'
        proc=run([sys.executable,'scripts/test_continuity_protocol.py','--report',str(life_report),'--log',str(life_log),'--workdir',str(life),'--keep-temp'],ROOT,env,1800)
        commands.append({'name':'lifecycle','returncode':proc.returncode})
        if proc.returncode!=0:raise RuntimeError('lifecycle failed\n'+proc.stdout[-3000:]+'\n'+proc.stderr[-3000:])
        lifecycle=json.loads(life_report.read_text(encoding='utf-8'))
        if lifecycle.get('status')!='PASS':raise RuntimeError('lifecycle report not PASS')
        life_checks={x.get('check') for x in lifecycle.get('checks',[])}
        mapping={
          'double_claim_rejected':'unique_active_session_and_claim',
          'stale_checkpoint_rejected':'stale_checkpoint_blocked',
          'real_git_commit_with_hooks':'checkpoint_pointer_and_commit_trailers',
          'pre_push_per_commit_validation':'per_commit_prepush_and_ci',
          'takeover_without_conversation':'wip_handoff_and_takeover',
          'event_tamper_rejected':'event_hash_chain_tamper_detected',
        }
        for name,source in mapping.items():
            if source not in life_checks:raise RuntimeError(f'missing lifecycle check {source}')
            checks.append({'name':name,'status':'PASS','evidence':source})
        outputs=life/'outputs'; handoff_zip=outputs/'wip-handoff.zip'
        if not handoff_zip.is_file():raise RuntimeError('handoff zip missing')
        extract=base/'handoff'; extract.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(handoff_zip) as z:z.extractall(extract)
        hdir=next(p for p in extract.iterdir() if p.is_dir())
        valid,count,errors=verify_manifest(hdir)
        if not valid:raise RuntimeError('handoff manifest invalid:'+str(errors))
        handoff=yaml.safe_load((hdir/'HANDOFF.yaml').read_text(encoding='utf-8')) or {}
        context=yaml.safe_load((hdir/'snapshot/artifacts/context/CURRENT_CONTEXT_PACK.yaml').read_text(encoding='utf-8')) or {}
        if context.get('conversation_dependency')!='PROHIBITED' or context.get('source_of_truth')!='REPOSITORY_ONLY':raise RuntimeError('context is not repository-only')
        parallel=context.get('parallel_development_policy') or {}
        expected_parallel={
          'default_delegation_mode':'AUTO_WHEN_SAFE_PARALLEL_WORK_EXISTS',
          'per_task_user_confirmation_required':False,
          'non_delegation_requires_checkpoint_reason':True,
          'capability_fallback':'RECORD_LIMITATION_AND_DO_NOT_FABRICATE_PARALLEL_EVIDENCE',
          'authoritative_active_sessions':1,
          'max_delegated_workers':3,
        }
        if any(parallel.get(key)!=value for key,value in expected_parallel.items()):raise RuntimeError('context lost standing parallel authorization')
        runtime=context.get('development_runtime') or {}
        transport=context.get('repository_transport') or {}
        if runtime.get('cloud_environment',{}).get('default_assumption')!='CODEX_ALREADY_CONNECTED_UNLESS_USER_DECLARES_DISCONNECTED':raise RuntimeError('context lost cloud default assumption')
        if runtime.get('cloud_environment',{}).get('android',{}).get('image')!='hhy-android-toolchain:r01-46fb273':raise RuntimeError('context lost existing Android image')
        if runtime.get('model_routing',{}).get('complex_or_high_risk',{}).get('model')!='Sol':raise RuntimeError('context lost model routing')
        checks.append({'name':'repository_only_context','status':'PASS','evidence':'snapshot/artifacts/context/CURRENT_CONTEXT_PACK.yaml'})
        checks.append({'name':'parallel_authorization_reconstructed','status':'PASS','evidence':'parallel_development_policy'})
        checks.append({'name':'runtime_and_git_transport_reconstructed','status':'PASS','evidence':'development_runtime+repository_transport'})
        clone=base/'reconstructed'
        proc=run(['git','clone',str(hdir/'repository.bundle'),str(clone)],base,env,300); commands.append({'name':'clone_bundle','returncode':proc.returncode})
        if proc.returncode!=0:raise RuntimeError('bundle clone failed:'+proc.stderr)
        proc=run(['git','apply',str(hdir/'working-tree.patch')],clone,env,120); commands.append({'name':'apply_wip','returncode':proc.returncode})
        if proc.returncode!=0:raise RuntimeError('working patch failed:'+proc.stderr)
        snapshot=hdir/'untracked-snapshot.tar.gz'
        if snapshot.is_file() and snapshot.stat().st_size:
            with tarfile.open(snapshot,'r:gz') as tf:tf.extractall(clone,filter='data')
        tracked_transport=yaml.safe_load((clone/'config/REPOSITORY_TRANSPORT.yaml').read_text(encoding='utf-8')) or {}
        if transport!=tracked_transport:raise RuntimeError('context Git transport differs from tracked descriptor restored from bundle')
        shutil.rmtree(clone/'scripts/__pycache__',ignore_errors=True)
        sys.path.insert(0,str(clone/'scripts'))
        from continuity_lib import project_fingerprint, load_session
        shutil.rmtree(clone/'scripts/__pycache__',ignore_errors=True)
        session=load_session(clone,handoff['session_id'])
        fp=project_fingerprint(clone,session)
        expected=handoff.get('project_fingerprint',{}).get('sha256')
        if fp.get('sha256')!=expected:raise RuntimeError(f'portable fingerprint mismatch {fp.get("sha256")} != {expected}')
        checks.append({'name':'portable_clone_fingerprint','status':'PASS','evidence':expected})
        # Manifest tamper must be rejected.
        tamper=base/'handoff-tamper'; shutil.copytree(hdir,tamper)
        target=tamper/'README.md'; target.write_text(target.read_text(encoding='utf-8')+'\ntampered\n',encoding='utf-8')
        tampered,_,_=verify_manifest(tamper)
        if tampered:raise RuntimeError('handoff tamper not rejected')
        checks.append({'name':'handoff_tamper_rejected','status':'PASS','evidence':'MANIFEST_SHA256.txt'})
        payload={'version':'1.2.3','status':'PASS','conversation_context_required':False,'repository_only_reconstruction':True,'source_manifest':source_manifest(),'checks':checks,'check_count':len(checks),'lifecycle_report':str(life_report.relative_to(ROOT)),'handoff_sha256':sha(handoff_zip),'portable_project_fingerprint':expected,'commands':commands}
        report.parent.mkdir(parents=True,exist_ok=True); report.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        log.parent.mkdir(parents=True,exist_ok=True); log.write_text('\n'.join(f"{x['name']} rc={x['returncode']}" for x in commands)+'\n',encoding='utf-8')
        print(json.dumps(payload,ensure_ascii=False,indent=2)); return 0
    except Exception as exc:
        payload={'version':'1.2.3','status':'FAIL','error':str(exc),'conversation_context_required':False,'repository_only_reconstruction':False,'source_manifest':source_manifest(),'checks':checks,'commands':commands,'workdir':str(base)}
        report.parent.mkdir(parents=True,exist_ok=True); report.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8'); print(json.dumps(payload,ensure_ascii=False,indent=2),file=sys.stderr); return 1
    finally:
        if created and not args.keep_temp:shutil.rmtree(base,ignore_errors=True)
if __name__=='__main__':raise SystemExit(main())
