from __future__ import annotations
from pathlib import Path
from collections import defaultdict, OrderedDict
import csv,re,hashlib,json,yaml

ROOT = Path(__file__).resolve().parents[1]
SCHEMA='hhy'

def read_csv(rel):
    with (ROOT/rel).open(encoding='utf-8-sig',newline='') as f: return list(csv.DictReader(f))
def write_csv(rel,rows,fields=None):
    p=ROOT/rel;p.parent.mkdir(parents=True,exist_ok=True)
    if fields is None: fields=list(rows[0]) if rows else []
    with p.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
def sqlname(prefix,*parts):
    raw='_'.join([prefix,*[p for p in parts if p]])
    if len(raw)<=60:return raw
    return raw[:51]+'_'+hashlib.sha1(raw.encode()).hexdigest()[:8]
def split_fields(s): return [x.strip() for x in s.split(',') if x.strip()]

def normalize_catalog():
    tables=read_csv('catalogs/data_tables.csv')
    early={
      'admin_users','admin_roles','admin_permissions','admin_user_roles','admin_role_permissions','admin_operation_logs','admin_approval_requests',
      'provider_config_definitions','provider_config_versions','provider_connection_tests','provider_certificates','provider_certificate_access_logs',
      'domain_configs','dns_action_items','storage_scope_bindings','storage_migration_jobs'
    }
    p00={'system_configs','system_config_versions','feature_flags','idempotency_records','app_versions'}
    for t in tables:
        if t['表名'] in early: t['计划版本']='R01/R03/R30'
        if t['表名'] in p00: t['计划版本']='P00/R29/R30'
        if t['表名']=='home_modules':
            t['关键字段']=t['关键字段'].replace(', order,',', display_order,')
    write_csv('catalogs/data_tables.csv',tables)
    meta={t['表名']:t for t in tables}
    rows=read_csv('database/schema_dictionary.csv')
    for r in rows:
        t,n=r['表名'],r['字段名']
        if t in meta: r['计划版本']=meta[t]['计划版本']
        if t=='home_modules' and n=='order': r['字段名']='display_order'; n='display_order'
        # Structural normalization.
        if n=='id': r.update({'建议类型':'bigint','可空':'否','默认值':'generated','索引建议':'PRIMARY KEY'})
        if n=='created_at': r.update({'建议类型':'timestamptz','可空':'否','默认值':'now()'})
        if n=='updated_at': r.update({'建议类型':'timestamptz','可空':'否','默认值':'now()'})
        if n=='version': r.update({'建议类型':'bigint','可空':'否','默认值':'0'})
        if n.endswith('_at') or n in {'last_seen','starts_at','ends_at','settle_at','occurred_at','server_time','valid_from','valid_to','retention_until','published_at','effective_at','verified_at','rotated_at'}:
            r['建议类型']='timestamptz'
        if n.endswith('_date') or n in {'stat_date','recon_date'}: r['建议类型']='date'
        if n.endswith('_json') or n in {'result','properties','body_json','hits_json','dimensions','rollout_json','payload','headers','criteria','rule','benefits','config'}:
            r['建议类型']='jsonb'
            if r['可空']=='否' and not r['默认值']: r['默认值']="'{}'::jsonb"
        if n in {'enabled','processed','signature_valid','foreground','visible','user_action_required','mfa_required','claimable','active','secret','required'}:
            r['建议类型']='boolean'
        if n in {'attempts','failed_count','total_count','claimed','reserved','total','success','failed','required_seconds','valid_seconds','duration','quantity','display_order','sort_order','sequence','version_code','min_version','weight','unread_count','refresh_times'}:
            r['建议类型']='integer'
        if n in {'size','file_size','size_bytes'}: r['建议类型']='bigint'
        if n in {'phone'}: r['建议类型']='varchar(32)'
        if n in {'ip'}: r['建议类型']='varchar(64)'
        if n in {'provider_message_id','provider_msg_id','provider_trade_no','provider_order_no','access_jti','refresh_hash','fingerprint','sha256','commit_sha','event_id','message_id'} and not n.endswith('_id'):
            r['建议类型']='varchar(128)'
        if n in {'provider_message_id','provider_msg_id','provider_trade_no','provider_order_no'}: r['建议类型']='varchar(128)'
        if n in {'amount','current_amount','principal','service_fee','payable','fee','net_amount','reward_amount','base','budget','consumed','unit_price','original','discount','balance_after','pending','available','frozen','withdrawing','withdrawn','delta','expected_cent','actual_cent','available_cent','frozen_cent','paid_value_cent','remaining_value_cent'} or n.endswith('_cent'):
            r['建议类型']='bigint'
        if n in {'status','review_status','decision','direction','scene','type','traffic_type','channel','scope','environment'}:
            if r['建议类型'] not in {'jsonb'}: r['建议类型']='varchar(64)'
        # Fields with operational invariants should never be nullable.
        if n in {'status'}: r['可空']='否'
        if n in {'total_count','claimed','reserved','total','available_cent','frozen_cent','pending','available','frozen','withdrawing','withdrawn'}:
            r['可空']='否'; r['默认值']=r['默认值'] or '0'
        if n=='enabled': r['可空']='否'; r['默认值']=r['默认值'] or 'false'
        r['成熟度']='FROZEN'
    # deterministic sort, no duplicate.
    rows.sort(key=lambda x:(list(meta).index(x['表名']) if x['表名'] in meta else 9999,int(x['字段顺序'])))
    by=defaultdict(list)
    for r in rows: by[r['表名']].append(r)
    out=[]
    for t in meta:
        seen=set()
        for i,r in enumerate(by[t],1):
            if r['字段名'] in seen: raise ValueError(f'duplicate {t}.{r["字段名"]}')
            seen.add(r['字段名']);r['字段顺序']=str(i);out.append(r)
    write_csv('database/schema_dictionary.csv',out)
    return tables,out

def pgtype(t):
    t=t.strip().lower()
    allowed={'bigint','integer','boolean','text','jsonb','date','timestamptz','varchar(8)','varchar(16)','varchar(32)','varchar(64)','varchar(128)','varchar(255)'}
    if t not in allowed: raise ValueError('unsupported type '+t)
    return t

def default_sql(r):
    d=r['默认值'].strip()
    if not d:return ''
    if d=='generated': return ' GENERATED BY DEFAULT AS IDENTITY'
    if d in {'now()','0','1','true','false'} or d.startswith("'"): return ' DEFAULT '+d
    return ''

def first_release(expr):
    parts=re.findall(r'(?:P|R)\d{2}',expr)
    if 'P00' in parts:return 0
    vals=[int(x[1:]) for x in parts if x.startswith('R')]
    return min(vals) if vals else 99

def migration_group(expr):
    n=first_release(expr)
    if n<=3:return 'V002__access_admin_and_platform.sql'
    if n<=15:return 'V003__content_identity_and_communication.sql'
    if n<=22:return 'V004__commerce_redpacket_and_finance.sql'
    return 'V005__growth_operations_and_delivery.sql'

UNIQUE_GROUPS={
 'users':[['phone'],['invite_code']], 'user_credentials':[['user_id']], 'user_profiles':[['user_id']], 'user_sessions':[['refresh_hash']],
 'user_devices':[['user_id','device_fingerprint']], 'user_settings':[['user_id']], 'identity_profiles':[['user_id'],['id_hash']],
 'identity_verification_sessions':[['state']], 'media_objects':[['bucket','object_key']], 'project_details':[['content_id']], 'app_details':[['content_id']],
 'group_details':[['content_id']], 'team_leader_details':[['owner_id']], 'content_media':[['content_id','sort_order']],
 'content_versions':[['content_id','version_no']], 'content_stats':[['content_id']], 'content_daily_stats':[['content_id','stat_date']],
 'content_favorites':[['user_id','content_id']], 'publisher_follows':[['user_id','publisher_id']], 'conversation_members':[['conversation_id','user_id']],
 'chat_messages':[['sender_id','client_msg_id']], 'chat_read_receipts':[['message_id','user_id']], 'user_blocks':[['user_id','blocked_user_id']],
 'notification_templates':[['code']], 'push_tokens':[['token']], 'announcement_reads':[['announcement_id','user_id']], 'product_skus':[['code']],
 'orders':[['order_no']], 'order_price_snapshots':[['order_id']], 'payment_transactions':[['provider_trade_no']],
 'payment_callbacks':[['gateway','provider_trade_no']], 'payment_reconciliation_records':[['recon_date','gateway']],
 'red_packet_campaigns':[['content_id']], 'red_packet_campaign_versions':[['campaign_id','version_no']],
 'red_packet_view_sessions':[['campaign_id','user_id','status']], 'red_packet_claims':[['campaign_id','user_id']],
 'reward_accounts':[['user_id']], 'withdrawal_requests':[['withdraw_no']], 'commission_records':[['order_id','beneficiary_id','level']],
 'referral_relations':[['invitee_id']], 'referral_milestone_rewards':[['relation_id','rule_version_id','milestone_id']],
 'task_definitions':[['code']], 'user_task_progress':[['user_id','task_id','period_key']], 'support_tickets':[['ticket_no']],
 'home_modules':[['code']], 'agreements':[['code']], 'agreement_versions':[['agreement_id','version']],
 'app_versions':[['version_code']], 'device_fingerprints':[['fingerprint']], 'blacklists':[['type','value_hash']], 'whitelists':[['type','value_hash']],
 'admin_users':[['username']], 'admin_roles':[['code']], 'admin_permissions':[['code']], 'admin_user_roles':[['admin_id','role_id']],
 'admin_role_permissions':[['role_id','permission_id']], 'system_configs':[['key','scope']], 'feature_flags':[['key']], 'job_definitions':[['code']],
 'idempotency_records':[['scope','idem_key']], 'daily_kpis':[['stat_date','metric_code','dimensions']],
 'provider_config_definitions':[['provider_code','field_code']], 'provider_config_versions':[['provider_code','version_no']],
 'domain_configs':[['environment','code']], 'storage_scope_bindings':[['scope_code','status']], 'app_build_profiles':[['environment','name']],
 'app_build_job_steps':[['job_id','step_code']], 'app_build_artifacts':[['job_id','artifact_type']], 'app_release_channels':[['code']],
 'outbox_events':[['event_id']], 'inbox_messages':[['consumer','message_id']], 'ledger_accounts':[['account_no']],
 'accounting_transactions':[['transaction_no'],['biz_type','idempotency_key']], 'accounting_entries':[['transaction_id','sequence']],
 'balance_snapshots':[['account_id']], 'reconciliation_runs':[['run_no']], 'reconciliation_differences':[['run_id','source_type','source_id']]
}

# Explicit foreign keys. Only unambiguous relations are enforced here; others are documented as application references.
FIELD_FK={
 'user_id':'users','owner_id':'users','publisher_id':'users','inviter_id':'users','invitee_id':'users','beneficiary_id':'users','source_user_id':'users','target_user_id':'users','blocked_user_id':'users',
 'content_id':'content_posts','media_id':'media_objects','media_object_id':'media_objects','conversation_id':'conversations','order_id':'orders','product_id':'products','sku_id':'product_skus',
 'campaign_id':'red_packet_campaigns','quote_id':'red_packet_quotes','claim_id':'red_packet_claims','transaction_id':'accounting_transactions','account_id':'ledger_accounts',
 'run_id':'reconciliation_runs','ticket_id':'support_tickets','notification_id':'notifications','agreement_id':'agreements','coupon_id':'coupons'
}
TABLE_FIELD_FK={
 ('identity_review_records','session_id'):'identity_verification_sessions',('identity_media','session_id'):'identity_verification_sessions',('identity_provider_requests','session_id'):'identity_verification_sessions',
 ('chat_message_attachments','message_id'):'chat_messages',('chat_read_receipts','message_id'):'chat_messages',('conversation_members','last_read_message_id'):'chat_messages',('conversations','last_message_id'):'chat_messages',
 ('red_packet_orders','order_id'):'orders',('red_packet_orders','quote_id'):'red_packet_quotes',('red_packet_heartbeats','session_id'):'red_packet_view_sessions',('red_packet_reservations','session_id'):'red_packet_view_sessions',
 ('red_packet_task_evidence','session_id'):'red_packet_view_sessions',('red_packet_task_evidence','claim_id'):'red_packet_claims',('red_packet_claims','reward_ledger_id'):'reward_ledger',
 ('reward_settlements','ledger_id'):'reward_ledger',('reward_freezes','ledger_id'):'reward_ledger',('commission_settlements','commission_id'):'commission_records',
 ('ticket_messages','ticket_id'):'support_tickets',('ticket_attachments','ticket_id'):'support_tickets',('ticket_status_logs','ticket_id'):'support_tickets',
 ('agreement_versions','agreement_id'):'agreements',('user_agreement_acceptances','version_id'):'agreement_versions',('app_download_logs','version_id'):'app_versions',
 ('admin_user_roles','admin_id'):'admin_users',('admin_user_roles','role_id'):'admin_roles',('admin_role_permissions','role_id'):'admin_roles',('admin_role_permissions','permission_id'):'admin_permissions',
 ('provider_connection_tests','config_version_id'):'provider_config_versions',('provider_certificate_access_logs','certificate_id'):'provider_certificates',('dns_action_items','domain_config_id'):'domain_configs',
 ('storage_migration_jobs','source_binding_id'):'storage_scope_bindings',('storage_migration_jobs','target_binding_id'):'storage_scope_bindings',
 ('app_build_jobs','profile_id'):'app_build_profiles',('app_build_job_steps','job_id'):'app_build_jobs',('app_build_artifacts','job_id'):'app_build_jobs',
 ('app_release_records','artifact_id'):'app_build_artifacts',('app_release_records','channel_id'):'app_release_channels',('app_release_records','rollback_from_id'):'app_release_records',
 ('app_release_approvals','release_record_id'):'app_release_records',('accounting_transactions','reversal_of_id'):'accounting_transactions'
}

def enum_checks():
    d=yaml.safe_load((ROOT/'database/enum_registry.yaml').read_text(encoding='utf-8'))
    return {e['code']:e['values'] for e in d['enums']}

def generate(tables,fields):
    mig=ROOT/'database/migrations'; mig.mkdir(parents=True,exist_ok=True)
    for x in mig.glob('V*.sql'): x.unlink()
    (mig/'V001__extensions_and_schema.sql').write_text("""-- 合伙云 Pro V1.2.2\n-- PostgreSQL 16+ / UTF-8\nCREATE SCHEMA IF NOT EXISTS hhy;\nSET search_path TO hhy, public;\nCREATE EXTENSION IF NOT EXISTS pgcrypto;\nCREATE EXTENSION IF NOT EXISTS pg_trgm;\n\nCREATE OR REPLACE FUNCTION hhy.set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$\nBEGIN\n  NEW.updated_at := clock_timestamp();\n  RETURN NEW;\nEND;\n$$;\n""",encoding='utf-8')
    by=defaultdict(list)
    for r in fields: by[r['表名']].append(r)
    group_lines=defaultdict(lambda:["-- Generated from database/schema_dictionary.csv. Do not hand edit.\nSET search_path TO hhy, public;\n"])
    trace=[]
    for trow in tables:
        t=trow['表名']; group=migration_group(trow['计划版本']); cols=[]
        for r in by[t]:
            typ=pgtype(r['建议类型']); col=f"  {r['字段名']} {typ}{default_sql(r)}"
            if r['可空']=='否' and r['默认值']!='generated': col+=' NOT NULL'
            if r['字段名']=='id': col+=' PRIMARY KEY'
            cols.append(col)
        sql=f"\n-- {trow['领域']} / {trow['用途']} / {trow['计划版本']}\nCREATE TABLE hhy.{t} (\n"+',\n'.join(cols)+"\n);\n"
        sql+=f"COMMENT ON TABLE hhy.{t} IS '{trow['用途'].replace("'","''")}';\n"
        group_lines[group].append(sql)
        trace.append({'表名':t,'领域':trow['领域'],'计划版本':trow['计划版本'],'迁移文件':'database/migrations/'+group,'字段数':str(len(by[t])),'主键':'id','成熟度':'FROZEN'})
    for name,lines in group_lines.items(): (mig/name).write_text('\n'.join(lines),encoding='utf-8')
    # Constraints and indexes.
    lines=["-- Constraints, foreign keys and access-path indexes.\nSET search_path TO hhy, public;\n"]
    table_names=set(by)
    for t in table_names:
        names={r['字段名'] for r in by[t]}
        for group in UNIQUE_GROUPS.get(t,[]):
            if all(c in names for c in group):
                n=sqlname('uq',t,*group); lines.append(f"ALTER TABLE hhy.{t} ADD CONSTRAINT {n} UNIQUE ({', '.join(group)});")
        # marked indexes and operational indexes
        index_cols=[]
        for r in by[t]:
            if r['索引建议']=='INDEX' or r['字段名'].endswith('_id') or r['字段名'] in {'status','created_at','expires_at','occurred_at','available_at'}:
                if r['字段名']!='id': index_cols.append(r['字段名'])
        for c in dict.fromkeys(index_cols):
            n=sqlname('ix',t,c); lines.append(f"CREATE INDEX IF NOT EXISTS {n} ON hhy.{t} ({c});")
        # updated_at trigger
        if 'updated_at' in names:
            n=sqlname('trg',t,'updated_at'); lines.append(f"CREATE TRIGGER {n} BEFORE UPDATE ON hhy.{t} FOR EACH ROW EXECUTE FUNCTION hhy.set_updated_at();")
    # Explicit FKs.
    for t,rs in by.items():
        for r in rs:
            c=r['字段名']; target=TABLE_FIELD_FK.get((t,c)) or FIELD_FK.get(c)
            if not target or target not in table_names or target==t and c=='id': continue
            # Avoid false mapping for generic content/order/user fields in the referenced table itself.
            if c=='owner_id' and t in {'ledger_accounts'}: continue
            n=sqlname('fk',t,c,target); ondelete='CASCADE' if t in {'admin_user_roles','admin_role_permissions','content_media','conversation_members','chat_message_attachments','ticket_attachments'} else 'RESTRICT'
            lines.append(f"ALTER TABLE hhy.{t} ADD CONSTRAINT {n} FOREIGN KEY ({c}) REFERENCES hhy.{target}(id) ON DELETE {ondelete};")
    # High-value composite indexes.
    composites={
      'content_posts':['type, status, created_at DESC','owner_id, status, created_at DESC'],
      'content_view_logs':['user_id, created_at DESC','content_id, traffic_type, created_at DESC'],
      'chat_messages':['conversation_id, id DESC'], 'notifications':['user_id, read_at, created_at DESC'],
      'red_packet_campaigns':['status, created_at DESC'], 'red_packet_view_sessions':['user_id, status, expires_at'],
      'orders':['user_id, status, created_at DESC'], 'payment_transactions':['status, created_at DESC'],
      'withdrawal_requests':['user_id, status, created_at DESC'], 'outbox_events':['status, available_at, id'],
      'admin_operation_logs':['admin_id, created_at DESC'], 'risk_events':['scene, status, created_at DESC']
    }
    for t,defs in composites.items():
        names={r['字段名'] for r in by.get(t,[])}
        for spec in defs:
            cols=[x.split()[0] for x in spec.split(', ')]
            if all(c in names for c in cols): lines.append(f"CREATE INDEX IF NOT EXISTS {sqlname('ix',t,*cols)} ON hhy.{t} ({spec});")
    (mig/'V006__constraints_indexes_and_triggers.sql').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    # Invariants/checks.
    enums=enum_checks(); lines=["-- State, money and accounting invariants.\nSET search_path TO hhy, public;\n"]
    status_map={'content_posts':('status','CONTENT_STATUS'),'red_packet_campaigns':('status','RED_PACKET_STATUS'),'orders':('status','ORDER_STATUS'),'withdrawal_requests':('status','WITHDRAWAL_STATUS'),'identity_profiles':('status','IDENTITY_STATUS'),'identity_verification_sessions':('status','IDENTITY_STATUS'),'app_build_jobs':('status','BUILD_JOB_STATUS'),'provider_config_versions':('status','CONFIG_VERSION_STATUS'),'outbox_events':('status','OUTBOX_STATUS')}
    for t,(c,e) in status_map.items():
        if t in by and c in {r['字段名'] for r in by[t]}:
            vals=', '.join("'"+v+"'" for v in enums[e]); lines.append(f"ALTER TABLE hhy.{t} ADD CONSTRAINT {sqlname('ck',t,c)} CHECK ({c} IN ({vals}));")
    # Generic non-negative checks for quantities and monetary values (delta/balance_after intentionally excluded).
    for t,rs in by.items():
        for r in rs:
            c=r['字段名']
            positive = c in {'amount_cent','price_cent','fee_cent','net_amount_cent','principal_cent','service_fee_cent','payable_cent','total_count','claimed','reserved','total','quantity','available_cent','frozen_cent','pending','available','frozen','withdrawing','withdrawn','expected_cent','actual_cent'} or c.endswith(('_amount','_count','_cent'))
            if positive and r['建议类型'] in {'bigint','integer'} and c not in {'delta_cent'}:
                lines.append(f"ALTER TABLE hhy.{t} ADD CONSTRAINT {sqlname('ck',t,c,'nonnegative')} CHECK ({c} >= 0);")
            if c.endswith('_bps') and r['建议类型'] in {'bigint','integer'}:
                lines.append(f"ALTER TABLE hhy.{t} ADD CONSTRAINT {sqlname('ck',t,c,'bps')} CHECK ({c} BETWEEN 0 AND 10000);")
    if 'red_packet_stock' in by:
        names={r['字段名'] for r in by['red_packet_stock']}
        if {'total','claimed','reserved'}<=names: lines.append("ALTER TABLE hhy.red_packet_stock ADD CONSTRAINT ck_red_packet_stock_capacity CHECK (claimed + reserved <= total);")
    if 'accounting_entries' in by:
        lines += [
          "ALTER TABLE hhy.accounting_entries ADD CONSTRAINT ck_accounting_entries_amount CHECK (amount_cent > 0);",
          "ALTER TABLE hhy.accounting_entries ADD CONSTRAINT ck_accounting_entries_direction CHECK (direction IN ('DEBIT','CREDIT'));"
        ]
    lines += [
"""
CREATE OR REPLACE FUNCTION hhy.assert_balanced_transaction(p_transaction_id bigint) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_debit bigint; v_credit bigint; v_entry_count bigint; v_currency_count bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM hhy.accounting_transactions WHERE id=p_transaction_id AND status='POSTED') THEN RETURN; END IF;
  SELECT count(*), COALESCE(sum(amount_cent) FILTER (WHERE direction='DEBIT'),0), COALESCE(sum(amount_cent) FILTER (WHERE direction='CREDIT'),0), count(DISTINCT currency)
    INTO v_entry_count,v_debit,v_credit,v_currency_count FROM hhy.accounting_entries WHERE transaction_id=p_transaction_id;
  IF v_entry_count < 2 OR v_debit <> v_credit OR v_currency_count <> 1 THEN
    RAISE EXCEPTION 'ACCOUNTING_UNBALANCED transaction_id=%, entries=%, debit=%, credit=%, currencies=%',p_transaction_id,v_entry_count,v_debit,v_credit,v_currency_count USING ERRCODE='23514';
  END IF;
END; $$;
CREATE OR REPLACE FUNCTION hhy.check_balanced_from_entry() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM hhy.assert_balanced_transaction(COALESCE(NEW.transaction_id,OLD.transaction_id)); RETURN NULL; END; $$;
CREATE OR REPLACE FUNCTION hhy.check_balanced_from_transaction() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM hhy.assert_balanced_transaction(COALESCE(NEW.id,OLD.id)); RETURN NULL; END; $$;
CREATE CONSTRAINT TRIGGER trg_accounting_entries_balance AFTER INSERT OR UPDATE OR DELETE ON hhy.accounting_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hhy.check_balanced_from_entry();
CREATE CONSTRAINT TRIGGER trg_accounting_transactions_balance AFTER INSERT OR UPDATE ON hhy.accounting_transactions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hhy.check_balanced_from_transaction();

CREATE OR REPLACE FUNCTION hhy.prevent_immutable_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF current_setting('hhy.allow_immutable_mutation', true)='on' THEN RETURN COALESCE(NEW,OLD); END IF;
  RAISE EXCEPTION 'IMMUTABLE_TABLE: %.%',TG_TABLE_SCHEMA,TG_TABLE_NAME USING ERRCODE='55000';
END; $$;
"""
    ]
    for t in ['accounting_transactions','accounting_entries','red_packet_ledger','user_point_ledger','admin_operation_logs','sensitive_data_access_logs']:
        if t in by:
            lines.append(f"CREATE TRIGGER {sqlname('trg',t,'immutable')} BEFORE UPDATE OR DELETE ON hhy.{t} FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();")
    (mig/'V007__state_money_and_accounting_invariants.sql').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    # Seed permissions/roles, no default password/user.
    permissions=[]
    admin_pages=read_csv('catalogs/admin_pages.csv')
    for r in admin_pages:
        for key in ['读取权限','写入权限']:
            code=r.get(key,'').strip()
            if code and code not in permissions and code!='NONE': permissions.append(code)
    seed=["-- Idempotent baseline RBAC. No default administrator credential is created.\nSET search_path TO hhy, public;\n",
          "INSERT INTO hhy.admin_roles(code,name,status) VALUES ('SUPER_ADMIN','超级管理员','ACTIVE'),('AUDITOR','审计员','ACTIVE'),('OPERATOR','运营人员','ACTIVE') ON CONFLICT(code) DO NOTHING;\n"]
    for code in permissions:
        resource,_,action=code.partition('.')
        seed.append("INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ("+','.join("'"+x.replace("'","''")+"'" for x in [code,resource,action or 'access'])+") ON CONFLICT(code) DO NOTHING;")
    seed += [
      "INSERT INTO hhy.feature_flags(key,enabled,rollout_json) VALUES ('registration.enabled',true,'{}'::jsonb),('publishing.enabled',true,'{}'::jsonb),('redpacket.enabled',false,'{}'::jsonb),('withdrawal.enabled',false,'{}'::jsonb) ON CONFLICT(key) DO NOTHING;",
      "-- 首个管理员必须通过 scripts/bootstrap-admin.sh 从环境变量生成强哈希，禁止在迁移中写入默认密码。"
    ]
    (mig/'V008__baseline_rbac_and_feature_flags.sql').write_text('\n'.join(seed)+'\n',encoding='utf-8')
    # Verification and rollback.
    verify=ROOT/'database/verification';verify.mkdir(parents=True,exist_ok=True)
    verify.joinpath('verify_baseline.sql').write_text("""SET search_path TO hhy, public;
DO $$ DECLARE v_count integer; BEGIN
 SELECT count(*) INTO v_count FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';
 IF v_count <> 198 THEN RAISE EXCEPTION 'Expected 198 tables, found %',v_count; END IF;
 IF EXISTS (SELECT 1 FROM hhy.accounting_transactions t LEFT JOIN hhy.accounting_entries e ON e.transaction_id=t.id WHERE t.status='POSTED' GROUP BY t.id HAVING COALESCE(sum(e.amount_cent) FILTER(WHERE e.direction='DEBIT'),0) <> COALESCE(sum(e.amount_cent) FILTER(WHERE e.direction='CREDIT'),0)) THEN RAISE EXCEPTION 'Unbalanced accounting data'; END IF;
 IF EXISTS (SELECT 1 FROM hhy.red_packet_stock WHERE claimed+reserved>total) THEN RAISE EXCEPTION 'Invalid red packet stock'; END IF;
END $$;
SELECT 'baseline-ok' AS result, count(*) AS table_count FROM information_schema.tables WHERE table_schema='hhy' AND table_type='BASE TABLE';
""",encoding='utf-8')
    rb=ROOT/'database/rollback';rb.mkdir(parents=True,exist_ok=True)
    rb.joinpath('U001__drop_hhy_schema_DEV_ONLY.sql').write_text("-- DESTRUCTIVE: development/test reset only. Production rollback is forward-fix or restore drill.\nDROP SCHEMA IF EXISTS hhy CASCADE;\n",encoding='utf-8')
    write_csv('database/schema_traceability.csv',trace)
    # README.
    (ROOT/'database/README.md').write_text("""# 数据库执行基线

- PostgreSQL 16+；所有业务对象位于 `hhy` schema。
- Flyway 顺序执行 `database/migrations/V001...V009`。
- `schema_dictionary.csv` 是字段目录；SQL 迁移是可执行事实；二者由 `scripts/generate_database.py` 和 Project Doctor 双向校验。
- 金额均为整数分；比例为基点；时间为 `timestamptz`；敏感原文不得进入普通字段。
- `accounting_transactions` 与 `accounting_entries` 使用延期约束在提交时校验复式平衡，并禁止常规更新/删除。
- 生产环境禁止运行 `database/rollback/U001...`；生产回滚采用前滚修复或经过演练的备份恢复。
""",encoding='utf-8')
    return trace

def main():
    tables,fields=normalize_catalog(); trace=generate(tables,fields)
    print(json.dumps({'tables':len(tables),'fields':len(fields),'migrations':len(list((ROOT/'database/migrations').glob('V*.sql'))),'trace':len(trace)},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
