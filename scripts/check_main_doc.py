#!/usr/bin/env python3
from pathlib import Path
import sys
R=Path(__file__).resolve().parents[1];p=R/'合伙云Pro_完整项目开发文档_V1.2.2_开发就绪版.md';s=p.read_text(encoding='utf-8')
need=['V1.2 工程执行强化规则','供应商配置','官方App自助构建','orbexa.cc','P00 + R01-R32','效果图只']
for x in need:
 if x not in s:print('MAIN_DOC_MISSING',x);sys.exit(1)
print('MAIN_DOC_OK',len(s.splitlines()))
