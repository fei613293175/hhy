---
cr_id: CR-0224
status: APPROVED
requester_actor_id: codex-root-r08-004
approver_actor_id: codex-reviewer-r08-fixture-proxy
task_id: TASK-R08-004
session_id: SES-20260722T031604Z-105CF4C6
created_at: 2026-07-22T04:48:10Z
updated_at: 2026-07-22T04:48:16Z
---
# CR-0224 — 增加仅本机视觉验收fixture反向代理

## 用户需求摘要

由AI自主采集并判断浏览器页面截图，不打断开发

## 原规则

浏览器视觉证据必须来自真实页面渲染且不得调用生产写接口；当前没有可复用的本机受控数据入口。

## 新规则

不新增产品规则；增加仅监听127.0.0.1的测试反向代理，将页面静态资源转发到Vite并只对冻结合同接口返回显式开发环境fixture；脚本不进入任何生产构建、部署或运行路径。

## 修改原因

现有后台会话仅存内存且无浏览器网络拦截能力，需要不进入产品构建的本机fixture代理复现冻结合同数据

## 影响摘要

新增单一测试脚本，为R02至R05 H5与后台视觉截图提供可复现受控响应和真实页面渲染。

## 影响文件

- `scripts/serve_ui_visual_fixture_proxy.py`
- `artifacts/validation/r08-historical-ui`

## 页面

- `R02至R05 H5与后台历史页面`

## API

- `只模拟既有冻结读取接口与管理员登录；不新增接口`

## 数据库与迁移

- `无直接影响`

## 配置

- `无直接影响`

## 资金/账本与历史数据

- `无直接影响`

## 测试

- `python scripts/serve_ui_visual_fixture_proxy.py --help`
- `浏览器真实渲染与截图人工/AI视觉复核`

## 版本

- `R08`

## 迁移与兼容策略

独立测试工具，默认不启动；不改产品代码、Vite配置、API合同、数据库或部署。

## 用户确认

项目所有者已要求AI自主完成截图判断且不要因核实中断开发

## 审批

- 审批人：`codex-reviewer-r08-fixture-proxy`
- 决定：`APPROVED`
- 时间：`2026-07-22T04:48:16Z`
- 说明：批准新增仅本机测试代理；必须绑定127.0.0.1，不得代理或写入生产，不得改变产品构建。

## 状态记录 · 2026-07-22T04:48:18Z

- Actor：`codex-root-r08-004`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T031604Z-105CF4C6`
- Note：开始实现本机fixture反向代理并用于真实浏览器渲染证据。

## 状态记录 · 2026-07-22T05:31:45Z

- Actor：`codex-root-r08-004`
- Status：`IMPLEMENTED`
- Session：`SES-20260722T031604Z-105CF4C6`
- Note：仅本机127.0.0.1 fixture反向代理已实现；管理员登录、用户、实名、供应商、域名与H5邀请冻结读取响应已用于真实浏览器渲染，脚本help与py_compile通过。

## 状态记录 · 2026-07-22T05:32:05Z

- Actor：`codex-root-r08-004`
- Status：`CLOSED`
- Session：`SES-20260722T031604Z-105CF4C6`
- Note：本机代理、12张后台截图和H5邀请截图均已验证；所有页面由AI判定PASS，代理进程已停止且浏览器视口已复原，不存在生产部署或运行时依赖。
