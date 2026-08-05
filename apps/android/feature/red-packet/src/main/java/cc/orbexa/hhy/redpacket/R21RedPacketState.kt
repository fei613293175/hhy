package cc.orbexa.hhy.redpacket

import cc.orbexa.hhy.network.CommandResultResource

internal fun r21CanPause(status: String, version: Long): Boolean =
    status == "ACTIVE" && version >= 0

internal fun r21CanResume(status: String, version: Long): Boolean =
    status == "PAUSED_BY_OWNER" && version >= 0

internal fun r21CanClose(status: String, version: Long): Boolean =
    status == "ACTIVE" && version >= 0

internal fun r21CanRequestIncrease(status: String, version: Long): Boolean =
    status in setOf("ACTIVE", "PAUSED_BY_OWNER", "PAUSED_BY_CONTENT_OFFLINE", "PAUSED_BY_RISK") && version >= 0

internal fun r21QuoteReady(quote: CommandResultResource?): Boolean =
    quote?.status == "QUOTED" && quote.resourceId != null
        && quote.principalCent != null && quote.serviceFeeCent != null
        && quote.payableCent != null && quote.amountPerClaimCent != null
        && quote.expiresAt != null

internal fun r21QuoteStatusLabel(quote: CommandResultResource?): String = when {
    quote == null -> "未获取报价"
    quote.status == "QUOTED" && quote.expiresAt != null -> "报价有效"
    quote.status == "EXPIRED" -> "报价已过期"
    else -> "报价状态待确认"
}
