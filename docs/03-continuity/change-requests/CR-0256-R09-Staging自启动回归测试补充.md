---
cr_id: CR-0256
status: APPROVED
requester_actor_id: codex-root-r09-observability
approver_actor_id: codex-r09-observability-review
task_id: TASK-R09-006
session_id: SES-20260722T200509Z-8FC026EC
created_at: 2026-07-22T20:25:42Z
updated_at: 2026-07-22T20:25:48Z
---
# CR-0256 — R09 Staging自启动回归测试补充

## 用户需求摘要

持续推进R09并避免版本关闭重复踩坑。

## 原规则

CR-0255的静态门禁可检查令牌但不验证自启动调用顺序和非空项目保护先于Compose变更，Bug提交分类器也不将scripts/check_r09_observability.py识别为测试资产。

## 新规则

只增加一个tests目录回归，读取既有R09入口并断言空项目保护、明确诊断、自启动命令和入口调用顺序；不新增运行入口或业务规则。

## 修改原因

CR-0255已批准精确脚本与问题登记范围，但Bug提交门禁还要求tests目录中的可执行回归，静态检查脚本不被测试分类器视为回归测试。

## 影响摘要

补齐CR-0255确定性回归并满足既有Bug门禁，不改变产品、API、数据库、配置或验收流程。

## 影响文件

- `tests/test_r09_staging_acceptance.py`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- 无直接影响（已在影响摘要说明）

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest tests.test_r09_staging_acceptance`

## 版本

- `R09`

## 迁移与兼容策略

纯测试；与现有R09脚本合同兼容。

## 用户确认

用户已授权持续修复并要求把踩坑转为可复用防回归。

## 审批

- 审批人：`codex-r09-observability-review`
- 决定：`APPROVED`
- 时间：`2026-07-22T20:25:48Z`
- 说明：只增加现有单一入口的源码顺序回归，无平行规则或产品影响。

## 状态记录 · 2026-07-22T20:26:42Z

- Actor：`codex-root-r09-observability`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T200509Z-8FC026EC`
- Note：已增加入口顺序与空项目保护的可执行回归，开始冻结修复Commit。

## 状态记录 · 2026-07-22T20:50:39Z

- Actor：`codex-root-r09-observability`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T200509Z-8FC026EC`
- Note：R09 Staging入口、子网、端口和首次抓取顺序共5项回归通过。

## 状态记录 · 2026-07-22T20:50:42Z

- Actor：`codex-root-r09-observability`
- Status：`CLOSED`
- Session：`SES-20260722T200509Z-8FC026EC`
- Note：回归测试与最终现场证据绑定完成，关闭测试补充CR。
