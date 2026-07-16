# 异常中断与 AI 接管演练 V1.2.3

## 正常交接

1. 原 Actor 创建最新检查点。
2. 执行 `continuity.py handoff` 生成 WIP Patch、未跟踪快照、Context Pack、Git Bundle 和 SHA Manifest。
3. 新 Actor 校验包并执行 `continuity.py takeover`。
4. 新 Session 继承 Task、Story、CR、Base Commit、项目指纹和精确下一步；旧 Session 标记 TRANSFERRED。

## 异常恢复

租约过期或执行环境丢失时，新 Actor 执行 `continuity.py recover`，必须写明异常原因和下一步。旧 Session 标记 ABANDONED，事件哈希链保留关系。

## 演练场景

- 双重领取被拒绝。
- 无测试检查点被拒绝。
- 检查点后修改被拒绝提交。
- 冻结事实无批准 CR 被拒绝。
- Handoff Manifest 被篡改后校验失败。
- Event Log 被追加/修改后哈希链失败。
- 新 AI 不读取旧对话，仅凭仓库和 Handoff 完成接管、提交和关闭。

证据由 `scripts/run_continuity_self_test.py` 和 `scripts/test_continuity_protocol.py` 生成到 `artifacts/validation/`。
