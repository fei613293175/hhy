# SCR-RP-ADV-001 红包活动列表视觉施工规格

## 绑定

- 精确面板：B08/P05「红包活动管理」，原图 `design/effect-previews/B08/HHY_B08_8PAGE_UI_REFERENCE.png`。
- 施工模板：MOB-LIST。目标为 Pixel 7 / API 35。

## 保留结构

1. 顶部返回、居中标题「红包活动管理」和右侧登记的「新建活动」入口。
2. 四段状态筛选：全部、进行中、未开始、已结束；筛选下方为可滚动活动列表。
3. 每项保持白底圆角、轻边框、紧凑上下信息层级：真实活动标题/状态、总金额与已发放金额、开始/结束时间、进度和页面登记的行操作。
4. 底部导航沿现有 Android 业务壳保留，当前入口高亮；不复制原图中示例头像、日期、金额和未登记入口。

## 产品映射

- 列表只调用 `redPacketGetMeRedPacketCampaigns`；展示 `id`、`contentId`、`status`、`totalCount`、`remainingCount`、`amountPerClaimCent`、`principalCent`、`serviceFeeCent`、`startAt`、`endAt`、`version`。
- 金额来自整数分并格式化为元；状态文案使用产品状态映射，不直出技术枚举；ID 只在必要上下文展示。
- LOADING、CONTENT、EMPTY、REFRESHING、APPENDING、PARTIAL_ERROR、ERROR、OFFLINE 均保留同一列表层级和可恢复入口。

## 禁止

- 不增加批量写、自动发放、订单支付按钮或效果图中的虚构活动。
- 不用整页通用卡片替代 B08/P05 的列表密度，不用 Toast 作为唯一成功/失败反馈。
