# Backend

Java 21 + Spring Boot 4.1 模块化单体，Maven Wrapper 3.9.11 为唯一构建入口。

## 模块

- `shared-kernel`：API 信封、错误、金额、领域事件和幂等基础类型；
- `access`：认证、用户、实名、RBAC 与配置边界；
- `content`：内容、搜索、审核、聊天、通知、CMS 与客服边界；
- `commerce`：商品、订单、支付、会员与道具边界；
- `incentive`：红包、奖励、提现、推广和任务边界；
- `platform`：平台状态、版本检查、Provider/运行时平台能力；
- `boot`：唯一 Spring Boot 装配入口、Flyway、Web/Security 适配与架构测试。

```bash
./mvnw clean verify
./mvnw -pl boot -am spring-boot:run
```

数据库迁移从包级 `database/migrations` 同步到 `boot/src/main/resources/db/migration`，契约同步到 `boot/src/main/resources/contracts`。提交前运行根目录 `python3 scripts/project-doctor.py --strict`，禁止跨模块 Repository、余额直改、明文秘密和契约漂移。
