---
cr_id: CR-0043
status: APPROVED
requester_actor_id: codex-root
approver_actor_id: project-owner-standing-authorization
task_id: TASK-R02-007
session_id: SES-20260718T105025Z-0B8DE284
created_at: 2026-07-18T11:18:49Z
updated_at: 2026-07-18T11:19:07Z
---
# CR-0043 — TASK-R02-007精确登记APK交付证据文件

## 用户需求摘要

用户要求R02收尾、APK放桌面并记录连接失败不再复发

## 原规则

TASK-R02-007会话默认允许路径不包含artifacts目录

## 新规则

仅允许本任务写入列出的R02 APK Manifest、构建/交付证据、任务报告和生成式文档校验结果精确文件

## 修改原因

CR-0042使用通配路径无法应用，必须改为精确列举已生成的R02 APK Manifest、报告和机器证据文件

## 影响摘要

固化已完成的R02 APK机器交付事实，不改变APK二进制、产品契约、API、数据库或版本范围

## 影响文件

- `artifacts/apk/R02/APK_MANIFEST.yaml`
- `artifacts/reports/R02/TASK-R02-007-android-apk.md`
- `artifacts/validation/r02-apk-delivery/delivery-evidence.json`
- `artifacts/validation/r02-task007-android/api-platform-status.json`
- `artifacts/validation/r02-task007-android/apk-badging.txt`
- `artifacts/validation/r02-task007-android/apk-signing.txt`
- `artifacts/validation/r02-task007-android/build-evidence.json`
- `artifacts/validation/r02-task007-android/gradle-build.exit`
- `artifacts/validation/r02-task007-android/gradle-build.log`
- `artifacts/validation/r02-task007-android/nginx-download-gate.txt`
- `artifacts/validation/r02-task007-android/zipalign.txt`
- `artifacts/validation/project-doctor-v1.2.2.json`

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

- `deliver_android_test_apk prepare+verify; test_android_apk_delivery; check_release_artifacts R02`

## 版本

- `R02`

## 迁移与兼容策略

纯交付证据与报告，无运行时迁移；APK仍绑定bbad603源码提交

## 用户确认

后续这种问题不要问我确认，你自己决定

## 审批

- 审批人：`project-owner-standing-authorization`
- 决定：`APPROVED`
- 时间：`2026-07-18T11:19:07Z`
- 说明：仅批准列出的TASK-R02-007精确交付证据文件
