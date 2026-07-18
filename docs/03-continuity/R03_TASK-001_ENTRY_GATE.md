# TASK-R03-001 开发就绪核验

- Release：`R03` — 供应商配置、密钥、证书与域名中心
- Session：`SES-20260718T132650Z-C5038104`
- 当前 Story：`STORY-R03-004`（工程治理与三切片汇合边界）
- 依赖：R03 只声明依赖 R01；R01 的 8 个任务均为 `DONE`
- 延后门禁：`TASK-R02-007` 仅等待项目所有者真机验收，保持 `BLOCKED`，不是 R03 的依赖且未被标记完成

## 冻结输入核验

| 项目 | 结果 |
| --- | --- |
| 需求 | 6 项：CONFIG、DOMAIN、STORAGE、SECRET、CONFIG-UX、APK |
| Admin 页面 | 7 个：`ADM-CONFIG-002`—`008` |
| Admin 接口 | 13 个唯一冻结 operation |
| Story | 4 个，全部 `READY_FOR_IMPLEMENTATION` |
| 权威测试 | 18 项 |
| 数据表 | Release Manifest 登记 26 张 |
| DoR | `PASS_DOCUMENTATION_READY`，全部适用门禁为 `PASS` |
| 文档严格门禁 | `PASS`，0 errors / 0 warnings / 0 open gaps |
| API 所有权 | 316 个 REST operation 全局未分配数为 0 |

执行命令：

```powershell
python scripts/check_v122_documentation.py --strict --release R03
python scripts/verify_cloud_environment.py --check-android
```

云端连接、固定 Android 镜像 `hhy-android-toolchain:r01-46fb273` 及 Gradle 缓存均验证通过，允许继续开发。

## 任务与切片边界

| 后续任务 | Story | 主结果 | 共享边界 |
| --- | --- | --- | --- |
| `TASK-R03-002` | `STORY-R03-001` | 短信、R2/OSS、实名供应商版本与连接测试 | 不自行修改共享契约、迁移编号和权限模型 |
| `TASK-R03-003` | `STORY-R03-002` | orbexa.cc 域名、DNS/TLS/服务健康验证 | 不把 DNS 成功等同于服务激活 |
| `TASK-R03-004` | `STORY-R03-003` | 支付、出款、证书生命周期 | 禁止明文私钥、口令或生产凭据进入仓库和日志 |
| `TASK-R03-005` | `STORY-R03-004` | 共享迁移、权限、SecretRef、审计与 18 项测试汇合 | 共享 API、DTO、Flyway 和生成类型只由主控集成 |

三个业务切片按依赖满足后的候选集合领取；唯一事实分支同一时刻只领取一个 Task。执行分区保持为 `BACKEND_DATA`、`ADMIN_CLIENT`、`QUALITY_EVIDENCE`，路径必须互斥。

## APK 与外部激活边界

- R03 即使没有 Android 页面变化，也必须在 `TASK-R03-007` 交付回归 APK。
- 最低 `versionCode` 为 `10203`，继续使用稳定 Staging 测试签名。
- APK 桌面、仓库、服务器、公网四方 SHA-256 和项目所有者真机验收仍为独立门禁。
- 供应商测试环境、SecretRef、证书指纹及 DNS/TLS/服务健康只能记录真实脱敏证据；缺失项保持阻断，不使用生产 Mock 冒充通过。

结论：R03 施工输入完整、依赖已满足、三条业务切片和共享合并边界清晰，可进入 `TASK-R03-002` 实现。
