# SCR-RP-ADV-002 创建红包视觉施工规格

## 绑定

- 内容层级精确面板：B03/P01「项目详情」。
- 红包金额摘要精确面板：B09/P08「红包余额与明细」；两者组合仅用于本页，不复制示例数据。
- 原图：`design/effect-previews/B03/HHY_B03_8PAGE_UI_REFERENCE.png`、`design/effect-previews/B09/HHY_B09_8PAGE_UI_REFERENCE.png`。目标为 Pixel 7 / API 35。

## 保留结构

1. 顶部返回、标题「创建红包」和稳定的滚动内容区。
2. 先展示关联内容摘要，使用 B03/P01 的标题/摘要/分区层级；内容 ID 作为真实字段，不展示示例图片或虚构发布者。
3. 表单按金额与规则分区：红包总数量、单个红包金额、开始时间、结束时间、定向规则；金额摘要沿 B09/P08 的高对比金额块，但仅显示服务端可确认的字段。
4. 底部固定主操作区：保存草稿/提交预审核的登记动作；提交期间锁定同资源写入并保留非敏感输入。

## 产品映射

- 创建调用 `redPacketPostRedPacketCampaigns`，编辑调用 `redPacketPatchRedPacketCampaignsById`；字段严格来自 `contentId`、`totalCount`、`amountPerClaimCent`、`startAt`、`endAt`、`targeting`、`expectedVersion`。
- 只允许实名广告主；金额、数量、时间和定向规则由服务端 Schema 校验。DIRTY、VALIDATING、SUBMITTING、SUCCESS、CONFLICT、OFFLINE、ERROR 需有明确原位状态。

## 禁止

- 不在客户端计算服务费、最终金额、库存或资格，不增加效果图中充值/会员/支付渠道入口。
- 不把 B03/B09 的示例标题、头像、金额、日期或文案带入产品。
