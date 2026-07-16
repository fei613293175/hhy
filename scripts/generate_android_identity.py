#!/usr/bin/env python3
from pathlib import Path
import secrets,string,yaml
R=Path(__file__).resolve().parents[1];p=R/'config/ANDROID_IDENTITY.yaml'
if p.exists():print(p);raise SystemExit(0)
suffix=''.join(secrets.choice(string.ascii_lowercase+string.digits) for _ in range(6));base=f'cc.orbexa.hhypro.{suffix}';obj={'generated_once':True,'production_application_id':base,'staging_application_id':base+'.staging','display_name':'合伙云 Pro','staging_display_name':'合伙云 Pro 测试版','production_signing':'PROTECTED_CI_PROFILE','staging_signing':'GENERATE_LOCAL_TEST_KEYSTORE','change_policy':'修改包名必须CR；生产签名由用户/受保护CI持有'};p.write_text(yaml.safe_dump(obj,allow_unicode=True,sort_keys=False),encoding='utf-8');print(p)
