# TASK-R02-006 四批次集成与预发布验收

状态：PASS（AC-R02-001 至 AC-R02-004）；APK 与版本交接仍分别由 TASK-R02-007、TASK-R02-008 完成。

## 被测基线

- 产品代码与四类外部作业基线：`d26aff1b69cbe5d94ee64d47042ddd9d56a22070`。
- 权威矩阵：`artifacts/validation/r02-task006-matrix.json`，25/25 PASS，0 FAIL，0 NOT_RUN。
- 外部日志：`artifacts/validation/r02-test-evidence/`，每项由证据 JSON 绑定 SHA-256。
- 隔离预发布：`artifacts/validation/r02-task006-staging/`；Compose 只使用独立项目、端口、子网和数据卷，未修改公网与 R01 环境。

## 全量结果

| 门禁 | 结果 |
|---|---|
| Java 21 / Maven 全模块 | 130 项，0 failure，0 error，2 项条件式 skip，BUILD SUCCESS |
| PostgreSQL 17.10 | V001-V018、升级、前向修复、受限回滚、并发与状态不变量 PASS；产品表 199 张 |
| Android 固定工具链 | `testDebugUnitTest lintDebug assembleDebug`，289 tasks，真实地址 `https://api.orbexa.cc` |
| H5 | 3 files / 8 tests，typecheck 与生产 build PASS |
| Admin Web | 7 files / 47 tests，typecheck 与生产 build PASS |
| 文档与生成契约 | 199 tables、316 REST、10 WebSocket、0 gap，生成资产无漂移 |

## 故障注入与安全边界

- 缺失短信供应商时在写入挑战或验证码前关闭失败，不生成测试验证码或伪造发送成功。
- 短信供应商超时异常不得记录虚假 delivery receipt；事务异常路径由回归测试覆盖。
- 覆盖挑战跨场景重用、短信验证码错误/重复消费、相同幂等键不同意图、刷新重放、会话撤销并发和非规范 Token 签名。
- 预发布首次实跑发现三项用户密钥未注入，API 按安全配置校验拒绝启动；已登记 `PROB-0020`，Compose、静态门禁和运行手册同步修复，禁止弱默认值绕过。

## 可观测性与运行手册演练

- API 完成 Flyway V018 后 readiness 为 UP，Nginx 公共状态探针返回 200。
- Prometheus `hhy-backend-r02` target 为 UP；三项 R02 用户 Gauge 存在，11 项 `hhy_business_metric_query_failures_total` 均为 0。
- 停止/恢复隔离 API 后，`HhyR02BackendDown` 取得 firing 与 resolved 投递回执。
- 注入 12 次隔离挑战失败后，`HhyR02UserChallengeFailureBurst` 取得 firing；删除测试数据后指标归零并取得 resolved，活动告警归零。
- `promtool` 验证 1 个配置和 6 条规则均成功；告警接收器只保存最小化回执，不保存请求体或密钥。

## 剩余边界

- `PROB-0012` 真实阿里云短信供应商激活仍归 TASK-R03-002；R02 不以模拟回执冒充外部通道验收。
- `PROB-0013` 公网部署的受控 SecretRef 仍归 TASK-R03-002；本次只使用隔离测试进程密钥，未写入仓库证据。
- `AC-R02-005` 与 `AC-R02-006` 保持 NOT_RUN，直到版本交接和固定签名 APK 的安装、四方哈希全部完成。
