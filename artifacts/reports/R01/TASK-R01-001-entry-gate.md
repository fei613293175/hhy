# TASK-R01-001 开发就绪核验

- Release：R01
- Session：SES-20260717T074317Z-C575A687
- Story claims：STORY-R01-001、STORY-R01-002、STORY-R01-003
- Dependency：P00 已完成并通过版本关闭门禁
- DoR：`releases/R01/DEFINITION_OF_READY.yaml` 为 `PASS_DOCUMENTATION_READY`
- 文档门禁：`python scripts/check_v122_documentation.py --strict --release R01` 通过，0 errors / 0 warnings
- 冻结事实：产品规格继续为 V1.2.2，本任务未改变页面、API、数据库或配置契约
- 变更基线：CR-0011 修正文档验证器版本字段语义；CR-0012 补齐 NEXT_TASK 统一命令生成；CR-0013 对齐 8 个接口与 27 项测试的派生任务数量
- Android：本版本要求测试 APK；TASK-R01-007 必须复制到项目所有者桌面，真机确认前不得关闭 R01

结论：R01 业务输入完整，可进入数据迁移与领域不变量任务。
