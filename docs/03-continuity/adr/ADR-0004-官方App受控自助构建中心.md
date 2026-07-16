# ADR-0004：官方App受控自助构建中心

- 状态：ACCEPTED
- 决策：后台仅允许从批准Git Ref构建合伙云Pro官方App，隔离Runner，产出测试/生产APK、日志、SHA、发布和回滚；禁止任意Shell和白标构建。
