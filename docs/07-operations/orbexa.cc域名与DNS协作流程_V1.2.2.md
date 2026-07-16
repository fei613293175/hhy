# orbexa.cc 域名与 DNS 协作流程 V1.2

Codex根据 `config/DOMAIN_PLAN.yaml` 和实际部署生成 `PENDING_USER_ACTIONS.md`。需要解析时必须明确告诉用户：记录类型、主机记录、目标值、TTL、环境和验证命令。用户完成解析后，Codex运行 `scripts/dns_action_report.py --verify` 验证DNS、HTTPS和服务健康；不得假定用户已经解析。二级域名可在开发中调整，但必须更新Domain Plan、环境变量、回调白名单、CORS、WebView白名单和CR/ADR。
