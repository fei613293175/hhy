---
cr_id: CR-0267
status: APPROVED
requester_actor_id: codex-root-r10-entry
approver_actor_id: codex-r10-data-review
task_id: TASK-R10-001
session_id: SES-20260723T025104Z-E29F6208
created_at: 2026-07-23T04:08:41Z
updated_at: 2026-07-23T04:09:41Z
---
# CR-0267 — 补齐首页真实CMS数据源、去重与查看更多闭环

## 用户需求摘要

项目所有者要求所有已开发功能必须一比一对应主开发文档；真实登录首页不能因home_modules空表而继续显示空页面。

## 原规则

首页后端只把home_modules.source_type当模块类型并读取config_json.items静态数组，moreTarget固定返回null；共享Staging home_modules为0行，导致真实登录首页无Banner、公告或内容模块，且无法落实后台数据源、数量和同请求去重。

## 新规则

在CR-0265既有主开发文档一一对应规则下，GET /api/v1/home必须读取已启用且排期有效的真实CMS模块，支持受控dataSource与limit从ONLINE内容生成卡片，解析moreTarget，并按display_order跨模块去重contentId；Staging必须通过幂等、显式STAGING限定的脚本配置公告、Banner和当前已开发项目/App模块，所有卡片只指向真实路由、真实内容和可访问HTTPS媒体。未来群聊、团队长、红包、头条等模块只能在对应版本真实能力完成后接入。

## 修改原因

CR-0265已恢复客户端结构，但实时核验发现api.orbexa.cc共享Staging数据库home_modules为0行；后端未解析moreTarget且静态items不能落实主开发文档4.3的数据源、数量和同请求去重。需在原纠偏基础上补真实数据闭环。

## 影响摘要

补齐首页聚合服务的真实数据源、数量、跨模块去重和查看更多合同；增加受控Staging CMS配置并让Android显示真实查看更多动作；不新增开发文档外业务，不伪造尚未开发的群聊、团队长、红包或头条功能。

## 影响文件

- `services/backend/content/src/main/java/cc/orbexa/hhy/content/ContentStore.java`
- `services/backend/content/src/main/java/cc/orbexa/hhy/content/ContentPostgresStore.java`
- `services/backend/content/src/main/java/cc/orbexa/hhy/content/ContentService.java`
- `services/backend/boot/src/test/java/cc/orbexa/hhy/content/ContentServiceTest.java`
- `apps/android/feature/shell/src/main/java/cc/orbexa/hhy/shell/HhyShellScreen.kt`
- `scripts/prepare_home_cms_staging_fixture.sh`
- `tests/test_home_contract_alignment.py`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `docs/03-continuity/PITFALLS.md`
- `CHANGELOG.md`

## 页面

- `SCR-HOME-001`

## API

- `homeGetHome`

## 数据库与迁移

- `home_modules`
- `content_posts`
- `content_media`
- `media_objects`

## 配置

- `home_modules.config_json:dataSource,limit,moreTarget`

## 资金/账本与历史数据

- `CR-0265真实数据闭环与Staging 0行根因证据`

## 测试

- `ContentServiceTest首页数据源/去重/moreTarget`
- `tests.test_home_contract_alignment`
- `Staging home_modules配置与登录态GET /api/v1/home黑盒`

## 版本

- `R06`
- `R08`
- `R09`
- `R10`

## 迁移与兼容策略

不新增数据库表或破坏字段；home_modules现有静态items继续兼容，dataSource、limit、moreTarget均为config_json可选字段。Staging配置脚本显式拒绝非STAGING，使用code幂等upsert且仅引用现有ONLINE内容和HTTPS媒体；生产数据不自动写入。

## 用户确认

项目所有者明确要求必须修正，所有已开发功能必须一比一对应其点名的V1.2工程执行强化版开发文档。

## 审批

- 审批人：`codex-r10-data-review`
- 决定：`APPROVED`
- 时间：`2026-07-23T04:09:41Z`
- 说明：实时只读核验已证明共享Staging home_modules为0行；新合同保持既有表和静态items兼容，只增加可选真实数据源、去重、moreTarget及显式STAGING幂等配置，且禁止提前伪造未来功能，范围最小且与主开发文档4.3一致。

## 状态记录 · 2026-07-23T04:09:46Z

- Actor：`codex-root-r10-entry`
- Status：`IMPLEMENTING`
- Session：`SES-20260723T025104Z-E29F6208`
- Note：开始实现首页真实CMS数据源、跨模块去重、moreTarget和Staging配置闭环。

## 状态记录 · 2026-07-23T04:36:02Z

- Actor：`codex-root-r10-entry`
- Status：`IMPLEMENTED`
- Session：`SES-20260723T025104Z-E29F6208`
- Note：真实ONLINE数据源、跨模块去重、moreTarget、Android查看更多及显式STAGING幂等配置已实现；公网登录态首页返回4模块和4条真实内容，重复执行稳定，非STAGING拒绝。
