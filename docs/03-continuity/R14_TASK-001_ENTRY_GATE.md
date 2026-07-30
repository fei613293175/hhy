# R14 TASK-001 开发入口门禁

状态：`DEVELOPMENT_READY`

## 范围

本入口只证明 R14 一对一聊天核心具备可执行的页面、视觉、REST、WebSocket、数据和测试合同，不证明运行时代码、Android 页面、截图、APK 或版本关闭已经完成。

## 纠正项

- 六个 Android 交互面已建立唯一视觉合同：`SCR-CHAT-001` 精确绑定 `B07/P01`，其余五页绑定 `design/R14-UI-FROZEN/specs/` 下的批准补充规格。
- `B07/P05`、`B07/P06` 属于 R15 通知和公告，不进入 R14。
- 消息分页和发送响应统一返回 `ChatMessageResource`；消息载荷限定为 `TEXT`、`IMAGE`、`CONTENT_CARD`、`CONTACT_CARD`。
- 联系方式卡只包含手机号、微信、QQ、邮箱、其他联系方式及可选备注。
- 页面字段目录只登记用户可见业务字段；路由 ID、分页游标、幂等键、客户端消息标识、乐观锁版本和响应诊断字段留在网络层。
- 聊天举报只覆盖用户侧原因、说明、消息证据、图片证据、提交和错误恢复；后台队列、分配、SLA、审核决定归 R15。

## 非零门禁

- `python scripts/check_r14_entry_contract.py`
- `python -m unittest tests.test_r14_entry_contract tests.test_ui_visual_acceptance`
- `python scripts/check_ui_visual_acceptance.py --release R14 --catalog-only`
- `python scripts/check_v122_documentation.py --release R14 --strict`
- `python scripts/check_program_execution_plan.py --json`
- `python scripts/check_generated_assets.py`
- `python scripts/check_release_artifacts.py --release R14`

任何适用命令非零时，本任务不得关闭。完整视觉 `PASS`、截图、运行时代码和 APK 仍由后续 R14 Task 完成。
