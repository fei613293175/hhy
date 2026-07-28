# Single Source of Truth · V1.2.2

## 权威层级

1. **产品与业务规则**：`合伙云Pro_完整项目开发文档_V1.2.2_页面与运营规格冻结版.md`、`catalogs/requirements_catalog.csv`、ADR。
2. **页面施工**：`catalogs/ui_page_specifications.csv`、`ui_page_fields.csv`、`ui_page_states.csv`、`ui_action_matrix.csv`、逐页 Markdown。
3. **后台运营**：`catalogs/admin_page_operation_specs.csv`。
4. **接口**：`contracts/openapi.yaml`、`admin-openapi.yaml`、`websocket-events.yaml`。
5. **配置**：`catalogs/config_registry.csv`、`config_cross_field_rules.csv`、`config_role_matrix.csv`。
6. **数据与状态**：`database/schema_dictionary.csv`、`database/state_machines.yaml`、迁移。
7. **追踪与计划**：`catalogs/TRACEABILITY_MATRIX.csv`、`frontend_backend_matrix.csv`、`api_ui_ownership.csv`、逐版本 DoR 和 Stories。
8. **派生文件**：HTML、JSON scaffold、汇总报告和索引，只能由上述事实源生成，不得手工形成第二套规则。

R14聊天举报原因以`contracts/openapi.yaml`中`ChatPostConversationsByIdReportRequest.reasonCode.x-hhy-options`为唯一人工事实源；`enum`、后端允许集和Android展示列表均为确定性派生物。

## 冲突处理

- 效果图与字段/动作冲突：以页面施工目录为准。
- 页面与接口冲突：先创建 CR；未批准前以冻结 OpenAPI 为准。
- 配置默认值与代码冲突：以配置注册表和业务快照规则为准。
- 主文档摘要与机器目录数量冲突：以机器目录和验证报告为准。
- 聊天记录、个人笔记、临时代码和旧版本不得覆盖本层级。
