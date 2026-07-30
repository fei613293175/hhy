# TASK-R16-004 后台订单列表实施报告

## 结论

`ADM-ORDER-001` 已完成机器验证。页面不再落入通用占位页，而是通过生成的 R16 Admin OpenAPI 类型读取真实订单列表与详情。视觉目录保持 `IN_REVIEW`，待 R16 大版本候选使用真实数据生成截图后由 AI 完成最终视觉判定。

## 实施边界

- 变更请求：`CR-0448`
- 页面：`apps/admin-web/src/views/CommerceOrdersPage.vue`
- 服务：`apps/admin-web/src/services/adminOrders.ts`
- 权限：`order.read`
- 接口：
  - `adminOrdersGetOrders` / `GET /admin-api/v1/orders`
  - `adminOrdersGetOrdersByOrderno` / `GET /admin-api/v1/orders/{orderNo}`

页面具备中文标题、真实统计、关键词/订单号/状态/排序筛选、URL 查询同步、专业表格、分页、刷新时间、复制订单号和用户编号、加载/空/失败/离线/无权限状态，以及只读详情抽屉。详情展示商品明细、完整价格快照、不可退款确认凭证、时间和数据版本。

## 不虚构约束

- 金额、状态、商品、价格快照和不可退款凭证仅来自 `OrderResource`。
- 统计仅描述查询结果或当前页事实，不伪造经营指标。
- 不提供批量写、退款、余额修改、支付操作、客服、删除或无真实合同的导出入口。
- 视觉目录的遗留 React TSX 路径已纠正为仓库实际 Vue 3 SFC 路径。

## 自动化证据

执行：

```text
pnpm --filter @hhy/admin-web typecheck
pnpm --filter @hhy/admin-web test
```

结果：

- Vue TypeScript 类型检查：PASS
- 测试文件：27 PASS
- 测试用例：121 PASS
- 新增覆盖：查询白名单、认证令牌、网络重试、业务失败不重试、路径安全、真实金额与中文状态、URL 同步、只读详情和刷新局部失败。

## 后续

继续 `STORY-R16-002` / `SCR-ORDER-002` Android 订单详情。R16 大版本完成时统一执行 Android 全量门禁、候选 APK、真实数据页面截图与 AI 视觉验收。
