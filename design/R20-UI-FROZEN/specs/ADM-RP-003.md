# ADM-RP-003 红包预审核后台视觉施工规格

## 绑定

- 原目录为 `TOKENS_ONLY`，采用后台 Design Token `design/tokens/admin-design-tokens.v1.2.2.css`、`ADM-REVIEW` 和后台运营规格冻结施工。
- 目标视口 1440x900；截图 `artifacts/reports/R20/visual/admin/ADM-RP-003.png`。

## 保留结构

1. 后台标准壳、标题「红包预审核」、待审核筛选和优先级排序 `priority:desc,createdAt:asc`。
2. 紧凑表格沿 ADM-RP-001 列结构，行操作只显示服务端状态与权限允许的预审核、平台暂停、恢复、平台终止且不退款。
3. 预审核面板包含 `decision`、`reason`、`expectedVersion`、`evidenceIds`，二次确认展示目标、影响、原因、审批要求和版本。
4. 详情 Tab 顺序为概览、业务数据、状态历史、关联对象、操作审计；成功更新行、计数和审计入口，失败保留输入并原位显示字段错误。

## 产品映射与禁止

- 读取要求 `redpacket.read`；预审核 `redpacket.review`；暂停/恢复 `redpacket.manage`；终止 `redpacket.terminate`。所有写动作使用 `X-Idempotency-Key` 和 `expectedVersion`。
- 不复制移动端示例数据，不增加批量最终决定，不泄露手机号、支付账号、IP 或风控细节；409 不得盲重试。
