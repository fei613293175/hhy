# 事务 Outbox 与幂等 Inbox

- 状态：ACCEPTED
- 日期：2026-07-16

## 决策

业务事务与事件同库提交；发布至少一次，消费者以 messageId 去重，支持重试、死信和人工重放。

## 后果

该决策进入 `project-doctor` 和 CI 门禁；偏离必须提交 ADR supersede 与 Change Request。
