---
cr_id: CR-0229
status: APPROVED
requester_actor_id: codex-root-r08-007
approver_actor_id: codex-reviewer-ui-token-r08
task_id: TASK-R08-007
session_id: SES-20260722T064621Z-68304DE1
created_at: 2026-07-22T07:03:42Z
updated_at: 2026-07-22T07:04:53Z
---
# CR-0229 — 修复最终候选全量Token门禁历史裸参数

## 用户需求摘要

项目所有者要求既有和后续UI严格遵循开发文档参数并在大版本最终候选统一验证，不允许规则失效或重复建立事实源。

## 原规则

design/tokens/hhy_design_tokens_v1.2.2.json及其确定性Android/CSS派生物是唯一UI参数事实源，页面禁止裸dp、sp和十六进制颜色；最终候选前必须通过全量Token门禁。

## 新规则

不新增Token或页面参数规则；修复既有派生器遗漏：从既有gradient.brand生成唯一--hhy-gradient-brand，H5引用该派生变量；Android启动卡和R08项目页将裸dp全部等价改写为HhySize、HhySpacing、HhyRadius和HhyElevation组合。

## 修改原因

R08最终候选前置全量check_ui_tokens发现H5品牌渐变仍有裸十六进制、R02启动卡和R08项目页仍有裸dp；此前受影响MODULE未执行全量门禁，GitHub候选将确定失败。需在既有Token唯一事实源内补齐派生品牌渐变并把页面裸值改为既有Token组合。

## 影响摘要

修复全量候选必然失败的历史参数漂移，保持像素参数来源仍为同一JSON，不改变页面业务、API或数据库；登记PROB-0080并增加派生渐变和零裸值回归。

## 影响文件

- `scripts/check_ui_tokens.py`
- `tests/test_ui_tokens.py`
- `packages/design-tokens/h5.css`
- `packages/design-tokens/admin.css`
- `apps/h5/src/styles.css`
- `apps/android/feature/startup/src/main/java/cc/orbexa/hhy/startup/StartupGateScreen.kt`
- `apps/android/feature/project/src/main/java/cc/orbexa/hhy/project/R08ProjectScreens.kt`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- `H5公开分享页`
- `SCR-APP-001`
- `SCR-APP-002`
- `SCR-APP-003`
- `SCR-LIST-001`
- `SCR-DETAIL-001`
- `SCR-PUB-002`

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- 无直接影响（已在影响摘要说明）

## 配置

- `既有V1.2.2 Design Token派生`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `python -m unittest tests.test_ui_tokens；python scripts/check_ui_tokens.py；Android受影响MODULE编译`

## 版本

- `R08`

## 迁移与兼容策略

仅UI编译期Token引用与确定性CSS派生变化；颜色值仍精确来自既有gradient.brand，Android尺寸为既有Token的等价组合，无数据迁移、接口变化或视觉合同扩张。

## 用户确认

项目所有者明确要求UI参数硬规则真正生效、规则查重更新且换AI换电脑无需重述。

## 审批

- 审批人：`codex-reviewer-ui-token-r08`
- 决定：`APPROVED`
- 时间：`2026-07-22T07:04:53Z`
- 说明：已核对既有Token规则与CR-0213视觉规则；本修复只补齐同一派生链并以等价Token组合替换裸值，不新增平行参数事实源。

## 状态记录 · 2026-07-22T07:04:58Z

- Actor：`codex-root-r08-007`
- Status：`IMPLEMENTING`
- Session：`SES-20260722T064621Z-68304DE1`
- Note：开始修复PROB-0080全量Token门禁历史裸参数。
