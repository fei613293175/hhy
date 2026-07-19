# R03 注册认证体验与安全策略修复验证

## 冻结决策

- 新密码规则：8–20位，必须同时包含字母和数字。
- 认证路由切换：清空手机号、密码、确认密码、短信验证码、邀请码和安全挑战状态。
- 注册错误：手机号已注册、邀请码无效或失效、密码策略不符分别使用专用错误码；客户端仅展示商业文案。
- 安全验证码有效期：默认120秒，后台配置及运行时允许60–300秒；一次性使用、最大尝试次数与创建/校验限流保持不变。

## 验证范围

- Android：新密码规则、确认密码提示、专用错误文案、路由状态隔离。
- H5：持续规则提示、20位上限、专用注册错误文案。
- Backend：注册专用错误码、冻结密码最大长度、TTL运行时闭锁。
- Contract / Config：OpenAPI错误码矩阵、错误码表、配置目录和跨字段规则一致。

## 状态

- PASS：H5 Vitest 12/12。
- PASS：Android `:feature:auth:testDebugUnitTest` 5/5（云端测试 XML：0 failures / 0 errors）。
- PASS：Backend Maven `verify` 200项，0失败、0错误、2项外部 PostgreSQL 条件跳过。
- PASS：Backend注册与策略定向测试20/20。
- PASS：Python生成资产、迁移和APK交付工具23/23；配置目录314项通过。
- PASS：OpenAPI、运行时契约与状态哈希一致；商业UI技术字段门禁通过。
- PASS：后端候选 `hhy-r03-authux-e4994ed` 健康，V020迁移成功；公网已切换并通过状态、注册配置、160×56 PNG安全验证及错误码黑盒门禁。
- PASS：数据库运行值为 `auth.password.max_length=20`、`auth.security_challenge.ttl_seconds=120`；切换前数据库备份及旧健康实例均保留用于回滚。
- IN PROGRESS：Android真机回归包版本身份已递增至10205，等待精确提交云端构建和四方SHA-256交付门禁。
- PENDING：项目所有者安装10205后完成四项注册体验真机复测。
