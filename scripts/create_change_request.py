#!/usr/bin/env python3
from pathlib import Path
import argparse,datetime,re
P=argparse.ArgumentParser();P.add_argument('title');P.add_argument('--user-request',default='');a=P.parse_args();R=Path(__file__).resolve().parents[1];d=R/'docs/03-continuity/change-requests';d.mkdir(parents=True,exist_ok=True)
nums=[]
for p in d.glob('CR-*.md'):
 m=re.match(r'CR-(\d+)',p.name)
 if m:nums.append(int(m.group(1)))
n=max(nums,default=0)+1;path=d/f'CR-{n:04d}.md';path.write_text(f'''# CR-{n:04d} — {a.title}\n\n- 状态：PROPOSED\n- 创建时间：{datetime.datetime.now().isoformat()}\n- 用户需求摘要：{a.user_request}\n\n## 原规则\n待填写\n\n## 新规则\n待填写\n\n## 影响分析\n- 页面：\n- API：\n- 数据库：\n- 配置：\n- 资金/账本：\n- 测试：\n- 版本：\n- 迁移与兼容：\n\n## 用户确认\n待确认\n''',encoding='utf-8');print(path)
