# TASK-R16-001 开发入口与变更基线

## 结论

R16 三项需求、十个接口、七张范围表、三张页面和四个故事已重新核验。原冻结材料存在资源类型与视觉绑定漂移，已通过 `CR-0440` 纠正，入口状态为 `PASS_DOCUMENTATION_READY`；该结论不代表运行时代码或版本出口已完成。

## 已纠正的合同

- 商品接口唯一返回 `ProductResource`。
- SKU 接口唯一返回 `ProductSkuResource`，权益使用既有结构化 `BenefitResource`。
- 客户端和后台订单接口唯一返回 `OrderResource`。
- 订单项、价格快照和不退款证据分别使用封闭 Schema；页面不得读取自由 JSON 猜测业务字段。
- 十个响应均禁止 `oneOf` 和 `CommandResultResource` 回退。
- `ORDER_STATUS` 与 `database/enum_registry.yaml` 八个值完全一致；未冻结的商品/SKU状态没有擅自创造枚举。

## 已冻结的视觉施工

- `SCR-ORDER-001` 精确绑定 `B08/P04`。
- `SCR-ORDER-002` 绑定批准补充规格并复用 `B08/P04` 视觉语言。
- `ADM-ORDER-001` 绑定商业后台补充规格，固定侧栏与主区独立滚动。
- 示例订单号、金额、商品内容、联系客服、支付、退款、物流和其他未登记动作全部过滤。
- 三页当前均为 `IN_REVIEW`，只有真实实现与候选截图经 AI 对照复核后才可转为 `PASS`。

## 验证

- `python scripts/check_r16_entry_contract.py`
- `python scripts/check_ui_visual_acceptance.py --release R16 --catalog-only`
- `python scripts/check_v122_documentation.py --release R16`
- `python scripts/check_api_contract.py`
- `python scripts/check_generated_assets.py`
- `python scripts/check_release_artifacts.py --release R16`

机器证据：`artifacts/validation/r16-entry/entry-contract.json`。

## 下一步

`TASK-R16-002` 只负责按本合同向前迁移数据库、建立唯一约束、历史空值兼容和不变量测试，不再重新决定 API 字段或视觉结构。
