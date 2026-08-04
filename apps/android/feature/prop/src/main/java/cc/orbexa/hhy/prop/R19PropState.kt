package cc.orbexa.hhy.prop

import cc.orbexa.hhy.network.R07CallResult

internal fun canUseProp(status: String, quantity: Long): Boolean =
    status == "AVAILABLE" && quantity > 0

internal fun propFailureMessage(failure: R07CallResult.Failure): String = when (failure.statusCode) {
    null -> "网络不可用，保留当前输入后重试"
    401 -> "登录状态已失效"
    409 -> "数据已变化，请刷新后重新确认"
    422 -> "当前道具无法按此方式使用"
    else -> if (failure.retryable) "服务暂时不可用，请重试" else "操作未完成，请检查后重试"
}
