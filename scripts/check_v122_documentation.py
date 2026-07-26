#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import argparse, csv, json, re, sys, hashlib
import yaml

ROOT=Path(__file__).resolve().parents[1]
MAIN_DOCUMENT = "合伙云Pro_完整项目开发文档_V1.2.2_页面与运营规格冻结版.md"
MAIN_DOCUMENT_POINTER = "docs/00-baseline/合伙云Pro_完整项目开发文档_V1.2.2_开发就绪版.md"


def validate_main_document_pointer(root, require):
    target = root / MAIN_DOCUMENT
    pointer = root / MAIN_DOCUMENT_POINTER
    require(target.is_file(), "MAIN_DOC_TARGET_MISSING", MAIN_DOCUMENT)
    require(pointer.is_file(), "MAIN_DOC_POINTER_MISSING", MAIN_DOCUMENT_POINTER)
    if pointer.is_file():
        linked_documents = re.findall(r"`([^`]+\.md)`", pointer.read_text(encoding="utf-8"))
        require(
            linked_documents == [MAIN_DOCUMENT],
            "MAIN_DOC_POINTER_DRIFT",
            f"主开发文档定位指针必须且只能指向 {MAIN_DOCUMENT}: {linked_documents}",
        )

def read_csv(rel):
    with (ROOT/rel).open("r",encoding="utf-8-sig",newline="") as f:
        return list(csv.DictReader(f))

def load_yaml(rel):
    return yaml.safe_load((ROOT/rel).read_text("utf-8"))

def validate_project_baseline(baseline, require):
    """Keep the product specification baseline separate from package tooling."""
    package_version=str(baseline.get("package_version") or "").strip()
    require(bool(package_version),"BASELINE_PACKAGE_VERSION","PROJECT_BASELINE package_version is required")
    require(
        baseline.get("product_spec_baseline")=="V1.2.2",
        "BASELINE_PRODUCT_SPEC_VERSION",
        "PROJECT_BASELINE product_spec_baseline must be V1.2.2",
    )

def ops(doc):
    out=[]
    for path,item in doc.get("paths",{}).items():
        for method,op in item.items():
            if method.lower() in {"get","post","put","patch","delete"}:
                out.append((op.get("operationId",""),method.upper(),path,op))
    return out

def find_literal_open_object(node,path=""):
    hits=[]
    if isinstance(node,dict):
        for k,v in node.items():
            p=f"{path}.{k}" if path else k
            if k=="additionalProperties" and v is True:
                hits.append(p)
            hits.extend(find_literal_open_object(v,p))
    elif isinstance(node,list):
        for i,v in enumerate(node):
            hits.extend(find_literal_open_object(v,f"{path}[{i}]"))
    return hits

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--strict",action="store_true")
    ap.add_argument("--release")
    ap.add_argument("--json-out",default="artifacts/validation/project-doctor-v1.2.2.json")
    args=ap.parse_args()
    errors=[]; warnings=[]; metrics={}
    def require(cond,code,msg):
        if not cond: errors.append({"code":code,"message":msg})
    def warn(cond,code,msg):
        if not cond: warnings.append({"code":code,"message":msg})

    required=[
        "V1.2.2_最终文档冻结说明.md",
        "合伙云Pro_完整项目开发文档_V1.2.2_页面与运营规格冻结版.md",
        "catalogs/ui_templates.csv","catalogs/ui_page_specifications.csv","catalogs/ui_page_fields.csv",
        "catalogs/ui_page_states.csv","catalogs/ui_action_matrix.csv","catalogs/ui_navigation_specifications.csv",
        "catalogs/admin_page_operation_specs.csv","catalogs/api_ui_ownership.csv",
        "catalogs/config_registry.csv","catalogs/config_cross_field_rules.csv","catalogs/config_role_matrix.csv",
        "catalogs/release_story_backlog.csv","catalogs/release_definition_of_ready.csv",
        "scripts/check_v122_documentation.py",
    ]
    for rel in required:
        require((ROOT/rel).is_file(),"FILE_MISSING",rel)
    validate_main_document_pointer(ROOT, require)

    baseline=load_yaml("PROJECT_BASELINE.yaml")
    validate_project_baseline(baseline,require)

    android=read_csv("catalogs/android_screens.csv")
    h5=read_csv("catalogs/h5_screens.csv")
    admin=read_csv("catalogs/admin_pages.csv")
    pages=read_csv("catalogs/ui_page_specifications.csv")
    fields=read_csv("catalogs/ui_page_fields.csv")
    states=read_csv("catalogs/ui_page_states.csv")
    actions=read_csv("catalogs/ui_action_matrix.csv")
    templates=read_csv("catalogs/ui_templates.csv")
    nav=read_csv("catalogs/ui_navigation_specifications.csv")
    admin_ops=read_csv("catalogs/admin_page_operation_specs.csv")
    ownership=read_csv("catalogs/api_ui_ownership.csv")
    configs=read_csv("catalogs/config_registry.csv")
    cfg_rules=read_csv("catalogs/config_cross_field_rules.csv")
    cfg_roles=read_csv("catalogs/config_role_matrix.csv")
    reqs=read_csv("catalogs/requirements_catalog.csv")
    trace=read_csv("catalogs/TRACEABILITY_MATRIX.csv")
    tests=read_csv("catalogs/test_cases.csv")
    stories=read_csv("catalogs/release_story_backlog.csv")
    dor=read_csv("catalogs/release_definition_of_ready.csv")
    releases=read_csv("catalogs/release_plan.csv")
    risks=read_csv("catalogs/development_risk_register.csv")
    schema=read_csv("database/schema_dictionary.csv")

    metrics.update({
        "android_surfaces":len(android),"h5_pages":len(h5),"admin_pages":len(admin),"ui_surfaces":len(pages),
        "templates":len(templates),"fields":len(fields),"states":len(states),"actions":len(actions),
        "configs":len(configs),"config_rules":len(cfg_rules),"config_roles":len(cfg_roles),
        "requirements":len(reqs),"tests":len(tests),"stories":len(stories),"dor_gates":len(dor),
        "tables":len({r["表名"] for r in schema}),
    })
    require(len(pages)==len(android)+len(h5)+len(admin),"PAGE_COUNT","page specs must cover every Android/H5/Admin surface")
    require(len({p["页面ID"] for p in pages})==len(pages),"PAGE_ID_DUP","duplicate page IDs")
    template_ids={t["模板ID"] for t in templates}
    require(all(p["模板ID"] in template_ids for p in pages),"TEMPLATE_BINDING","every page must bind a known template")
    page_ids={p["页面ID"] for p in pages}
    for label,rows in [("field",fields),("state",states),("action",actions),("navigation",nav)]:
        covered={r["页面ID"] if "页面ID" in r else r["UI_ID"] for r in rows}
        missing=sorted(page_ids-covered)
        require(not missing,f"PAGE_{label.upper()}_MISSING",f"{label} missing for {missing[:10]}")
    require({r["页面ID"] for r in admin_ops}=={r["ID"] for r in admin},"ADMIN_OPERATION_COVERAGE","all admin pages need operational specs")
    for p in pages:
        matches=list((ROOT/"docs/02-ui/page-specs").glob(f"**/{p['页面ID']}_*.md"))
        require(len(matches)==1,"PAGE_DOC",f"{p['页面ID']} must have exactly one generated page document")
        require(p.get("DoR状态")=="READY","PAGE_DOR",f"{p['页面ID']} DoR is not READY")
    required_action_cols=["显示条件","可用条件","前置校验","二次确认","请求映射","并发控制","加载表现",
                          "成功状态","失败状态","重试策略","成功后导航","审计要求","敏感处理","DoR状态"]
    for a in actions:
        for c in required_action_cols:
            require(bool(a.get(c,"").strip()),"ACTION_DETAIL",f"{a.get('动作ID')} missing {c}")
        require(a.get("成功状态")!="更新页面状态并记录成功埋点","GENERIC_SUCCESS",a.get("动作ID",""))
        require(not a.get("失败状态","").startswith("按错误码映射展示可恢复"),"GENERIC_FAILURE",a.get("动作ID",""))

    client=load_yaml("contracts/openapi.yaml")
    admindoc=load_yaml("contracts/admin-openapi.yaml")
    ws=load_yaml("contracts/websocket-events.yaml")
    all_ops=ops(client)+ops(admindoc)
    opids=[x[0] for x in all_ops]
    metrics["rest_operations"]=len(opids)
    metrics["websocket_events"]=len(ws.get("events",[]))
    require(len(opids)==len(set(opids)),"OPERATION_ID_DUP","REST operationId values must be globally unique")
    require("GenericCommand" not in client.get("components",{}).get("schemas",{}),"GENERIC_COMMAND","client GenericCommand forbidden")
    require("GenericCommand" not in admindoc.get("components",{}).get("schemas",{}),"GENERIC_COMMAND","admin GenericCommand forbidden")
    require("NONE" not in client.get("components",{}).get("schemas",{}),"NONE_SCHEMA","client NONE schema forbidden")
    require("NONE" not in admindoc.get("components",{}).get("schemas",{}),"NONE_SCHEMA","admin NONE schema forbidden")
    require(not find_literal_open_object(client),"OPEN_OBJECT","client literal additionalProperties:true forbidden")
    require(not find_literal_open_object(admindoc),"OPEN_OBJECT","admin literal additionalProperties:true forbidden")
    require(not find_literal_open_object(ws),"OPEN_OBJECT","websocket literal additionalProperties:true forbidden")
    for opid,method,path,op in all_ops:
        require(not(method in {"GET","DELETE"} and "requestBody" in op),"GET_DELETE_BODY",f"{method} {path}")
    owner_by_op={r["operationId"]:r for r in ownership}
    require(set(opids)==set(owner_by_op),"API_OWNERSHIP_SET","ownership must cover all REST operations exactly")
    require(all(r["状态"] in {"FROZEN","EXPLICIT_SYSTEM_OWNER"} for r in ownership),"API_OWNERSHIP_STATUS","unassigned API ownership")
    metrics["unassigned_api_operations"]=sum(r["状态"]=="BLOCKED" for r in ownership)

    cfg_required=["display_name","ui_group","control_type","help_text","visible_when","enabled_when","editable_by",
                  "approver_role","impact_scope","preview_strategy","rollback_strategy","audit_event","maturity"]
    for c in configs:
        for col in cfg_required:
            require(bool(c.get(col,"").strip()),"CONFIG_METADATA",f"{c.get('key')} missing {col}")
    require(len({c["editable_by"] for c in configs})>5,"CONFIG_ROLE_VARIETY","config roles still effectively single-role")
    require(not all("super_admin" in c["editable_by"] for c in configs),"CONFIG_SUPERADMIN","all configs cannot default to super_admin")
    rule_ids={r["规则ID"] for r in cfg_rules}
    for c in configs:
        for rid in filter(None,c.get("cross_field_rule_ids","").split(";")):
            require(rid in rule_ids,"CONFIG_RULE_REF",f"{c['key']} -> {rid}")
    require(len(cfg_roles)>=10,"CONFIG_ROLE_COUNT","configuration role matrix incomplete")

    req_ids={r["需求ID"] for r in reqs}
    trace_by={r["需求ID"]:r for r in trace}
    require(req_ids==set(trace_by),"TRACE_REQUIREMENTS","traceability must cover every requirement exactly")
    for rid,tr in trace_by.items():
        require(bool(tr.get("配置组","").strip()),"TRACE_CONFIG_GROUP",rid)
        require(bool(tr.get("配置项","").strip()),"TRACE_CONFIG_KEYS",rid)
        require(bool(tr.get("页面规格","").strip()),"TRACE_PAGE_SPEC",rid)
        require(bool(tr.get("动作规格","").strip()),"TRACE_ACTION_SPEC",rid)

    test_ids={t["测试ID"] for t in tests}
    for a in actions:
        for tid in filter(None,a.get("测试ID","").split(";")):
            require(tid in test_ids,"ACTION_TEST_REF",f"{a.get('动作ID')} -> {tid}")

    release_codes={r["版本"] for r in releases}
    require(len(release_codes)==33,"RELEASE_COUNT","expected P00 plus R01-R32")
    for rel in release_codes:
        if args.release and rel!=args.release:
            continue
        sfile=ROOT/f"releases/{rel}/STORIES.yaml"
        dfile=ROOT/f"releases/{rel}/DEFINITION_OF_READY.yaml"
        require(sfile.is_file(),"RELEASE_STORIES",rel)
        require(dfile.is_file(),"RELEASE_DOR",rel)
        if sfile.is_file():
            sd=yaml.safe_load(sfile.read_text("utf-8"))
            require(bool(sd.get("stories")),"RELEASE_STORY_EMPTY",rel)
        if dfile.is_file():
            dd=yaml.safe_load(dfile.read_text("utf-8"))
            require(dd.get("status")=="PASS_DOCUMENTATION_READY","RELEASE_DOR_STATUS",rel)
            require(all(g.get("当前结论")=="PASS" for g in dd.get("gates",[])),"RELEASE_DOR_GATE",rel)
    require(all(r.get("当前结论")=="PASS" for r in dor),"DOR_CATALOG","central DoR contains non-PASS")
    require(all(r.get("DoR")=="READY" for r in stories),"STORY_DOR","story backlog contains non-ready story")

    risk_ids={r["risk_id"] for r in risks}
    require("RISK-BUSINESS-IMPLEMENTATION" not in risk_ids,"IMPLEMENTATION_AS_RISK","P00-R32 implementation must not be listed as a risk")
    require(all(r["status"]=="CLOSED" for r in risks if r.get("classification")=="DOCUMENT_GAP"),"DOCUMENT_GAP_OPEN","document gaps remain open")
    metrics["open_document_gaps"]=sum(r.get("classification")=="DOCUMENT_GAP" and r["status"]!="CLOSED" for r in risks)

    required_tables={"admin_sessions","admin_mfa_methods","admin_recovery_codes","admin_login_logs",
                     "accounting_reversal_requests","risk_rule_simulations","notification_delivery_attempts",
                     "export_jobs","export_job_files","export_download_logs"}
    table_names={r["表名"] for r in schema}
    require(required_tables<=table_names,"V122_TABLES","new operational tables missing")
    require((ROOT/"database/migrations/V009__v122_operational_governance.sql").is_file(),"V122_MIGRATION","V009 migration missing")


    def collect_refs(node):
        found=[]
        if isinstance(node,dict):
            for k,v in node.items():
                if k=="$ref" and isinstance(v,str):
                    found.append(v)
                else:
                    found.extend(collect_refs(v))
        elif isinstance(node,list):
            for v in node:
                found.extend(collect_refs(v))
        return found
    def resolves(doc,ref):
        if not ref.startswith("#/"):
            return True
        cur=doc
        for part in ref[2:].split("/"):
            part=part.replace("~1","/").replace("~0","~")
            if not isinstance(cur,dict) or part not in cur:
                return False
            cur=cur[part]
        return True
    for label,doc in [("client",client),("admin",admindoc),("websocket",ws)]:
        missing=sorted({ref for ref in collect_refs(doc) if ref.startswith("#/") and not resolves(doc,ref)})
        require(not missing,"LOCAL_REF",f"{label} missing refs: {missing[:10]}")
    client_catalog={(r["方法"],r["路径"]) for r in read_csv("catalogs/api_endpoints.csv")}
    admin_catalog={(r["方法"],r["路径"]) for r in read_csv("catalogs/admin_api_endpoints.csv")}
    require(client_catalog=={(method,path) for _,method,path,_ in ops(client)},"CLIENT_API_CATALOG","client API catalog differs from OpenAPI")
    require(admin_catalog=={(method,path) for _,method,path,_ in ops(admindoc)},"ADMIN_API_CATALOG","admin API catalog differs from OpenAPI")
    action_operation_ids={a["operationId"] for a in actions if a.get("operationId")}
    ui_owned={r["operationId"] for r in ownership if r["所有者类型"]=="PAGE_ACTION"}
    require(action_operation_ids==ui_owned,"ACTION_OWNERSHIP","UI-owned operations must have exactly one or more action contracts")
    table_names={r["表名"] for r in schema}
    unknown_tables=[]
    for tr in trace:
        for table in filter(None,tr.get("数据表","").split(";")):
            if not table.startswith("N/A") and table not in table_names:
                unknown_tables.append((tr["需求ID"],table))
    require(not unknown_tables,"TRACE_TABLES",f"unknown trace tables: {unknown_tables[:10]}")

    report={
        "version":"1.2.2","status":"PASS" if not errors and (not args.strict or not warnings) else "FAIL",
        "strict":args.strict,"release_filter":args.release,"metrics":metrics,"errors":errors,"warnings":warnings
    }
    out=ROOT/args.json_out
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 0 if report["status"]=="PASS" else 1

if __name__=="__main__":
    raise SystemExit(main())
