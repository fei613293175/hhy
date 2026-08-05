package cc.orbexa.hhy.redpacket

internal enum class R20EditorState { CLEAN, DIRTY, VALIDATING, SUBMITTING, SUCCESS, CONFLICT, OFFLINE, ERROR }

internal fun r20StatusLabel(status: String): String = when (status) {
    "DRAFT" -> "草稿"
    "PRE_REVIEWING" -> "预审核中"
    "PRE_REVIEW_REJECTED" -> "预审核未通过"
    "PRE_REVIEW_APPROVED" -> "预审核已通过"
    "WAITING_PAYMENT" -> "待支付"
    "PAYMENT_PROCESSING" -> "支付处理中"
    "ACTIVE" -> "进行中"
    "PAUSED_BY_CONTENT_OFFLINE" -> "内容下线暂停"
    "PAUSED_BY_OWNER" -> "发起人暂停"
    "PAUSED_BY_RISK" -> "风控暂停"
    "SOLD_OUT" -> "已领完"
    "CLOSED_BY_OWNER" -> "已关闭"
    "TERMINATED_BY_PLATFORM" -> "平台终止"
    else -> "状态待确认"
}

internal fun r20CanSubmitReview(status: String, version: Long): Boolean =
    status in setOf("DRAFT", "PRE_REVIEW_REJECTED") && version >= 0

internal fun r20CanRequestQuote(status: String, version: Long): Boolean =
    status == "PRE_REVIEW_APPROVED" && version >= 0

internal fun r20CanOrder(quoteStatus: String?, campaignStatus: String, version: Long): Boolean =
    quoteStatus == "QUOTED" && campaignStatus == "PRE_REVIEW_APPROVED" && version >= 0
