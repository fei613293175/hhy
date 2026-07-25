package cc.orbexa.hhy.shell

import cc.orbexa.hhy.network.R07CallResult
import java.util.Locale

enum class R12MeModuleKind { USER, MEMBERSHIP, REWARD }

enum class R12MeModuleStatus {
    LOADING,
    CONTENT,
    REFRESHING,
    STALE,
    EMPTY,
    FORBIDDEN,
    IDENTITY_REQUIRED,
    RISK_FROZEN,
    RATE_LIMITED,
    OFFLINE,
    ERROR,
}

data class R12MeModuleState<T>(
    val value: T? = null,
    val status: R12MeModuleStatus = R12MeModuleStatus.LOADING,
    val updatedAt: String? = null,
    val retryAfterSeconds: Long? = null,
) {
    val hasReliableContent: Boolean
        get() = value != null && status in setOf(
            R12MeModuleStatus.CONTENT,
            R12MeModuleStatus.REFRESHING,
        )

    val hasVisibleContent: Boolean get() = value != null

    fun refreshing(): R12MeModuleState<T> = copy(
        status = if (value == null) R12MeModuleStatus.LOADING else R12MeModuleStatus.REFRESHING,
        retryAfterSeconds = null,
    )

    fun loaded(value: T, responseTimestamp: String?, receivedAt: String): R12MeModuleState<T> = copy(
        value = value,
        status = R12MeModuleStatus.CONTENT,
        updatedAt = responseTimestamp ?: receivedAt,
        retryAfterSeconds = null,
    )

    fun failed(kind: R12MeModuleKind, failure: R07CallResult.Failure): R12MeModuleState<T> {
        val statusCode = failure.statusCode
        val nextStatus = when {
            statusCode == 403 -> R12MeModuleStatus.FORBIDDEN
            statusCode == 404 && kind != R12MeModuleKind.USER -> R12MeModuleStatus.EMPTY
            statusCode == 422 && kind == R12MeModuleKind.REWARD &&
                failure.errorCode == "IDENTITY-422-NOT_VERIFIED" -> R12MeModuleStatus.IDENTITY_REQUIRED
            statusCode == 423 && kind == R12MeModuleKind.REWARD -> R12MeModuleStatus.RISK_FROZEN
            statusCode == 429 -> R12MeModuleStatus.RATE_LIMITED
            statusCode == null && value == null -> R12MeModuleStatus.OFFLINE
            (statusCode == null || statusCode >= 500) && value != null -> R12MeModuleStatus.STALE
            value != null -> R12MeModuleStatus.STALE
            else -> R12MeModuleStatus.ERROR
        }
        return copy(
            status = nextStatus,
            retryAfterSeconds = failure.retryAfterSeconds,
        )
    }

    companion object {
        fun <T> cached(value: T): R12MeModuleState<T> = R12MeModuleState(
            value = value,
            status = R12MeModuleStatus.REFRESHING,
        )
    }
}

internal fun formatR12Cent(value: Long): String {
    require(value >= 0)
    return String.format(Locale.ROOT, "%,d.%02d", value / 100, value % 100)
}

internal fun membershipStatusLabel(status: String): String = when (status.uppercase(Locale.ROOT)) {
    "ACTIVE" -> "会员有效"
    "EXPIRED" -> "会员已到期"
    "CANCELLED" -> "会员已取消"
    "SUSPENDED" -> "会员暂不可用"
    else -> "会员状态待确认"
}

internal fun accountStatusLabel(status: String): String = when (status.uppercase(Locale.ROOT)) {
    "ACTIVE" -> "账号正常"
    "FROZEN" -> "账号受限"
    "CANCELLED" -> "账号已注销"
    else -> "账号状态待确认"
}

internal fun identityStatusLabel(status: String?): String = when (status?.uppercase(Locale.ROOT)) {
    "VERIFIED" -> "已实名"
    "PENDING", "PROCESSING" -> "实名审核中"
    "REJECTED" -> "实名未通过"
    else -> "未实名"
}
