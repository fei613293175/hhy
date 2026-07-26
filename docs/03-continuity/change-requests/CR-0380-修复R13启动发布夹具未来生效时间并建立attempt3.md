---
cr_id: CR-0380
status: APPROVED
requester_actor_id: codex-root-r13-candidate-20260727
approver_actor_id: owner-standing-delegation-20260727
task_id: TASK-R13-007
session_id: SES-20260726T191158Z-2B506AB7
created_at: 2026-07-26T21:02:12Z
updated_at: 2026-07-26T21:02:54Z
---
# CR-0380 — 修复R13启动发布夹具未来生效时间并建立attempt3

## 用户需求摘要

批准，以后不要让我批准了 你自己持续开发就行了

## 原规则

R13夹具虽然创建official+STAGING+10222的PUBLISHED记录并输出release=1，但published_at固定在候选运行之后；既有专项测试只检查记录存在，没有证明published_at已生效，也未把公网version-check 200/NONE作为每次候选前置条件。

## 新规则

将R13发布记录published_at固定到候选日前的确定性过去时刻，并对既有记录显式校验published_at不晚于当前时间；R13专项测试锁定过去时刻与生效断言；候选前必须连续双跑夹具且公网version-check对10222返回200和NONE。attempt2保留已消费，以唯一R13-CANDIDATE-20260727-003运行普通attempt3。

## 修改原因

Run 30219507396的OIDC bootstrap、session和/api/v1/me均为200，但R13夹具把10222发布记录固定为2026-07-27T00:00:00Z，早于该时刻运行的StartupGate按published_at<=now查询不到记录并返回404；attempt2已消费，需最小修复、回归与连续attempt3。

## 影响摘要

仅修复R13隔离Staging启动发布记录的生效时间、专项回归、候选请求和既有PROB-0114事实；不改生产数据、API合同、Android业务源码、视觉尺寸或版本身份。

## 影响文件

- `scripts/prepare_r13_ci_fixture.sh`
- `tests/test_r13_candidate.py`
- `config/android-candidate-request.yaml`
- `docs/03-continuity/PROBLEM_REGISTRY.yaml`
- `CHANGELOG.md`

## 页面

- 无直接影响（已在影响摘要说明）

## API

- 无直接影响（已在影响摘要说明）

## 数据库与迁移

- `R13专用Staging app_release_records.published_at幂等校正`

## 配置

- `android-candidate-request:R13-attempt-3`

## 资金/账本与历史数据

- 无直接影响（已在影响摘要说明）

## 测试

- `tests.test_r13_candidate;tests.test_android_ci_gate;R13 fixture double-run;public version-check 200/NONE`

## 版本

- `R13`

## 迁移与兼容策略

对专用R13 Staging发布记录执行幂等校正；PROD硬隔离，10222、1.2.2、official、STAGING及稳定测试签名不变。旧attempt2 Run 30219507396不得重跑，attempt3只运行一次。

## 用户确认

批准，以后不要让我批准了 你自己持续开发就行了

## 审批

- 审批人：`owner-standing-delegation-20260727`
- 决定：`APPROVED`
- 时间：`2026-07-26T21:02:54Z`
- 说明：项目所有者已再次明确持续开发且不再逐次批准；独立范围复核确认Run 30219507396的404由未来published_at确定性导致，修复只校正专用Staging夹具并保留生产隔离、普通三轮上限和单次候选约束。

## 状态记录 · 2026-07-26T21:03:02Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTING`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：已下载Run 30219507396运行证据并确认version-check 404来自未来published_at，开始最小夹具、回归、既有PROB-0114和attempt3修复。

## 状态记录 · 2026-07-26T21:10:07Z

- Actor：`codex-root-r13-candidate-20260727`
- Status：`IMPLEMENTED`
- Session：`SES-20260726T191158Z-2B506AB7`
- Note：R13夹具已在obx-test通过bash语法检查、固定候选容器连续双跑严格一致，公网version-check对ANDROID/10222/official/STAGING返回HTTP 200、updateType NONE和latestVersionCode 10222；专项代码与测试已完成。
