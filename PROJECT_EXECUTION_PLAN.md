# P00—R32 项目执行计划说明

P00—R32 是软件开发工作本身，不属于“未解决风险”。

- `releases/<release>/DEFINITION_OF_READY.yaml`：编码前输入完整性。
- `releases/<release>/STORIES.yaml`：可领取的纵向故事。
- `releases/<release>/TASKS.yaml`：契约、数据、后端、前端、测试、观测、产物和交接顺序。
- `releases/<release>/ACCEPTANCE_MATRIX.csv`：版本完成验收。
- `RELEASE_MANIFEST.yaml`：版本范围和证据。
- `releases/PROGRAM_EXECUTION_PLAN.yaml`：由 `CURRENT_STATUS.active_release` 确定性生成的当前滚动窗口；不得保留硬编码的历史版本并由检查器自证 PASS。

项目应按依赖顺序推进；任何范围、页面、接口或业务规则变化必须通过 Change Request。R12 起每个大版本机器关闭前必须完成六项全局漂移审计，发现偏差先修订原事实源和回归，再进入下一版本。
