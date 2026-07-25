package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContentStatisticsResource
import cc.orbexa.hhy.network.R07CallResult
import java.net.URI
import java.util.UUID

enum class R12ContentManagementPhase {
    LOADING,
    CONTENT,
    STALE_CACHE,
    NOT_FOUND,
    FORBIDDEN,
    ERROR,
    OFFLINE,
}

data class R12ContentManagementFailure(
    val phase: R12ContentManagementPhase,
    val retryAfterSeconds: Long? = null,
)

fun R07CallResult.Failure.toR12ContentManagementFailure(hasContent: Boolean): R12ContentManagementFailure =
    R12ContentManagementFailure(
        phase = if (hasContent) {
            R12ContentManagementPhase.STALE_CACHE
        } else {
            when (statusCode) {
                null -> R12ContentManagementPhase.OFFLINE
                403 -> R12ContentManagementPhase.FORBIDDEN
                404 -> R12ContentManagementPhase.NOT_FOUND
                else -> R12ContentManagementPhase.ERROR
            }
        },
        retryAfterSeconds = retryAfterSeconds,
    )

data class R12ContentManagementState(
    val phase: R12ContentManagementPhase = R12ContentManagementPhase.LOADING,
    val content: ContentResource? = null,
    val analytics: ContentPageResource? = null,
    val analyticsLoading: Boolean = false,
    val analyticsUnavailable: Boolean = false,
    val actionInFlight: Boolean = false,
    val notice: String? = null,
    val failure: R12ContentManagementFailure? = null,
) {
    val statistics: ContentStatisticsResource?
        get() = analytics?.items?.firstOrNull { it.id == content?.id }?.statistics
            ?: analytics?.items?.firstOrNull()?.statistics
            ?: content?.statistics

    fun loading(): R12ContentManagementState = copy(
        phase = if (content == null) R12ContentManagementPhase.LOADING else phase,
        notice = null,
        failure = null,
    )

    fun loaded(value: ContentResource): R12ContentManagementState = copy(
        phase = R12ContentManagementPhase.CONTENT,
        content = value,
        failure = null,
        notice = null,
    )

    fun loadFailed(value: R07CallResult.Failure): R12ContentManagementState {
        val mapped = value.toR12ContentManagementFailure(content != null)
        return copy(phase = mapped.phase, failure = mapped)
    }

    fun analyticsStarted(): R12ContentManagementState = copy(
        analyticsLoading = true,
        analyticsUnavailable = false,
    )

    fun analyticsLoaded(value: ContentPageResource): R12ContentManagementState = copy(
        analytics = value,
        analyticsLoading = false,
        analyticsUnavailable = false,
    )

    fun analyticsFailed(): R12ContentManagementState = copy(
        analyticsLoading = false,
        analyticsUnavailable = true,
    )
}

class R12CopyIntentKeys {
    private val values = mutableMapOf<String, Pair<String, String>>()

    fun forRequest(contentId: String, expectedVersion: Long, reason: String?): String {
        val fingerprint = "$contentId:$expectedVersion:${reason.orEmpty()}"
        return values[contentId]?.takeIf { it.first == fingerprint }?.second
            ?: UUID.randomUUID().toString().also { values[contentId] = fingerprint to it }
    }

    fun consume(contentId: String) {
        values.remove(contentId)
    }
}

fun ContentResource.canCopy(currentUserId: String, identityVerified: Boolean): Boolean =
    identityVerified && publisher?.userId == currentUserId

fun ContentResource.secureMediaUrl(): String? = media.asSequence()
    .flatMap { sequenceOf(it.thumbnailUrl, it.url) }
    .filterNotNull()
    .firstOrNull(::isSecureMediaUrl)

fun contentTypeLabel(value: String): String = when (value) {
    "PROJECT" -> "项目"
    "APP" -> "App"
    "GROUP_CHAT" -> "群聊"
    "TEAM_LEADER" -> "团队长"
    else -> "内容"
}

fun contentStatusLabel(value: String): String = when (value) {
    "DRAFT" -> "草稿"
    "PENDING", "PENDING_REVIEW", "REVIEWING" -> "审核中"
    "ONLINE", "PUBLISHED" -> "已上架"
    "OFFLINE" -> "已下架"
    "REJECTED" -> "未通过"
    else -> "状态已更新"
}

private fun isSecureMediaUrl(value: String): Boolean = runCatching {
    val uri = URI.create(value)
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)
