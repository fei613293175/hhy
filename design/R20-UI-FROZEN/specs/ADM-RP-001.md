# ADM-RP-001 红包活动列表后台视觉施工规格

## 绑定

- 原目录为 `TOKENS_ONLY`，无可直接照抄的截图。
- 精确补充合同：后台 Design Token `design/tokens/admin-design-tokens.v1.2.2.css`、`ADM-LIST`、`catalogs/admin_page_operation_specs.csv`。
- 目标视口 1440x900；截图 `artifacts/reports/R20/visual/admin/ADM-RP-001.png`。

## 保留结构

1. 后台标准侧栏/顶部栏，主区标题「红包活动」与刷新、筛选和导出入口。
2. URL 同步的页码、每页数量、游标、状态、关键词、排序筛选；紧凑表格列严格按运营规格：ID、内容ID、owner User Id、状态、红包总数量、remaining Count、单个红包金额、principal Cent、service Fee Cent、开始时间、结束时间、版本。
3. 详情通过行操作进入；详情 Tab 顺序为概览、业务数据、状态历史、关联对象、操作审计。
4. 首屏、空、刷新、翻页/局部失败、无权限、404、离线与版本冲突均保留筛选上下文。

## 产品映射与禁止

- 读取动作使用 `redpacket.read`；账本读取单独要求 `redpacket.finance`。敏感字段默认脱敏，禁止增加批量最终决定或未登记导出字段。
- 不使用移动端卡片拼接后台页面，不以无上下文空表或单一 Toast 代替恢复反馈。
