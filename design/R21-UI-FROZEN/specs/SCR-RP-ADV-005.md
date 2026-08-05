# SCR-RP-ADV-005 红包活动详情视觉施工规格

## 绑定

- 精确面板：B08/P05「红包活动管理页」，复用其顶部返回/标题、活动分段列表式信息密度、状态标签、统计摘要和固定底部操作层级。
- 原图：`design/effect-previews/B08/HHY_B08_8PAGE_UI_REFERENCE.png`；目标为 Pixel 7 / API 35（1080x2400 capture）。
- 产品事实：`docs/02-ui/page-specs/android/SCR-RP-ADV-005_红包活动详情.md`、`catalogs/ui_page_fields.csv`、`catalogs/ui_page_states.csv`、`catalogs/ui_action_matrix.csv`、`contracts/openapi.yaml`。

## 保留结构

1. 顶部返回、页面标题「红包活动详情」和当前活动上下文；滚动内容不遮挡系统栏。
2. 主体按「媒体/标题 | 主体信息 | 状态与统计 | 分区详情」顺序组织，采用白底、细分隔线、紧凑数值摘要和状态色标签；长内容可滚动，固定操作区留出底部安全空间。
3. 固定操作区只显示服务端状态机允许的「暂停」「恢复」「关闭」和返回/刷新动作；高风险关闭操作二次确认，提交期间锁定同资源写操作。
4. LOADING、CONTENT、STALE_CACHE、NOT_FOUND、FORBIDDEN、ERROR、OFFLINE 等状态保留同一最终布局骨架；缓存明确标记只读，错误显示 requestId 与重试。

## 产品映射

- 读取使用 `redPacketGetMeRedPacketCampaignsByIdAnalytics`；写操作使用 `redPacketPostRedPacketCampaignsByIdPause`、`redPacketPostRedPacketCampaignsByIdResume`、`redPacketPostRedPacketCampaignsByIdClose`。
- 字段只使用 `id`、`contentId`、`ownerUserId`、`status`、`totalCount`、`remainingCount`、`amountPerClaimCent`、`principalCent`、`serviceFeeCent`、`startAt`、`endAt`、`version` 及目录登记的分页/原因/expectedVersion/header 字段。
- 每个写意图使用稳定 `X-Idempotency-Key` 与服务端 `expectedVersion`；409 不盲重试，按规格重新读取后由用户确认。

## 禁止

- 不复制 B08/P05 的示例标题、金额、用户头像、状态数量、未登记入口或订单中心入口。
- 不把效果图中「新建活动」或统计/编辑等未登记动作带入 R21；不在客户端推导金额、版本或状态。
