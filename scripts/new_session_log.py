#!/usr/bin/env python3
from pathlib import Path
import argparse,datetime
P=argparse.ArgumentParser();P.add_argument('--task',required=True);P.add_argument('--goal',default='');a=P.parse_args();R=Path(__file__).resolve().parents[1];now=datetime.datetime.now();d=R/'docs/03-continuity/sessions'/now.strftime('%Y-%m');d.mkdir(parents=True,exist_ok=True);p=d/f'SES-{now.strftime("%Y%m%d-%H%M%S")}.md';p.write_text(f'''# 开发会话记录\n\n- 任务：{a.task}\n- 开始：{now.isoformat()}\n- 目标：{a.goal}\n- 起始分支/Commit：待填写\n\n## 已完成\n\n## 修改文件\n\n## 契约/数据库/配置变化\n\n## 测试与APK\n\n## 问题、根因与解决\n\n## 可复用优点\n\n## 未完成和下一步\n\n## Commit/PR\n''',encoding='utf-8');print(p)
