# SCR-RP-ADV-003 预审核结果视觉施工规格

## 绑定

- 精确面板：B03/P08「内容下架/失效提示态」，复用其居中状态图标、明确原因、主次操作层级。
- 原图：`design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`。目标为 Pixel 7 / API 35。

## 保留结构

1. 顶部返回、标题「预审核结果」和资源上下文。
2. 结果区采用 B03/P08 的单列状态结构：状态标识、真实审核状态、服务端原因/备注、关键金额和时间摘要。
3. 结果下方保留版本与审计上下文；主操作按状态显示提交预审核、返回列表或查看详情，次操作不抢占主层级。

## 产品映射

- 首屏 `redPacketGetRedPacketCampaignsById`，提交 `redPacketPostRedPacketCampaignsByIdSubmitReview`；字段只使用资源字段、`expectedVersion`、`remark`。
- PENDING/REVIEWING/DECIDED/REJECTED、CONFLICT、OFFLINE、ERROR 均可解释并可恢复；拒绝原因来自服务端，不展示风控规则细节。

## 禁止

- 不复制 B03/P08 的文件图标、示例标题、示例原因或“浏览其他内容”入口。
- 不在决定尚未确认时显示成功，不以技术状态码代替业务文案。
