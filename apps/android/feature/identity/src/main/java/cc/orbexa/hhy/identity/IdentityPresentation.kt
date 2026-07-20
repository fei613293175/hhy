package cc.orbexa.hhy.identity

import cc.orbexa.hhy.network.IdentityCallResult
import cc.orbexa.hhy.network.IdentitySessionResource

internal data class IdentityFormErrors(
    val realName: String? = null,
    val idNumber: String? = null,
) {
    val isEmpty: Boolean get() = realName == null && idNumber == null
}

internal fun validateIdentityForm(realName: String, idNumber: String): IdentityFormErrors =
    IdentityFormErrors(
        realName = when {
            realName.isBlank() -> "请输入真实姓名"
            realName.trim().length > 2_000 -> "姓名内容过长"
            else -> null
        },
        idNumber = when {
            idNumber.isBlank() -> "请输入身份证号"
            idNumber.trim().length > 2_000 -> "身份证号内容过长"
            else -> null
        },
    )

internal enum class IdentityResultKind { READY, PENDING, SUCCESS, FAILED, UNKNOWN }

internal fun IdentitySessionResource.resultKind(): IdentityResultKind = when (status) {
    "SESSION_CREATED" -> IdentityResultKind.READY
    "LIVENESS_PENDING", "PROVIDER_PROCESSING", "MANUAL_REVIEW" -> IdentityResultKind.PENDING
    "VERIFIED", "COMPLETED" -> IdentityResultKind.SUCCESS
    "REJECTED", "FAILED", "EXPIRED" -> IdentityResultKind.FAILED
    else -> IdentityResultKind.UNKNOWN
}

internal fun IdentitySessionResource.businessStatusText(): String = when (resultKind()) {
    IdentityResultKind.READY -> "认证资料已提交"
    IdentityResultKind.PENDING -> if (status == "MANUAL_REVIEW") "正在人工审核" else "正在核验身份"
    IdentityResultKind.SUCCESS -> "实名认证已完成"
    IdentityResultKind.FAILED -> when (status) {
        "EXPIRED" -> "认证已过期"
        "REJECTED" -> "认证未通过"
        else -> "认证未完成"
    }
    IdentityResultKind.UNKNOWN -> "正在确认认证结果"
}

internal fun IdentityCallResult.Failure.businessMessage(defaultMessage: String): String = when (statusCode) {
    null -> "网络连接不稳定，请检查网络后重试"
    400 -> fieldErrors.values.firstOrNull() ?: "提交内容有误，请检查后重试"
    401 -> "登录状态已失效，请重新登录"
    403 -> "当前账号暂时不能进行实名认证"
    404 -> "认证记录不存在，请重新开始"
    409 -> "认证状态已更新，请刷新后继续"
    422 -> fieldErrors.values.firstOrNull() ?: "当前信息暂未通过校验，请核对后重试"
    429 -> retryAfterSeconds?.let { "操作过于频繁，请在${it}秒后重试" } ?: "操作过于频繁，请稍后重试"
    else -> defaultMessage
}
