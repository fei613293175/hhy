package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import java.util.UUID

enum class R12ContentListPhase {
    LOADING,
    CONTENT,
    EMPTY,
    FORBIDDEN,
    NOT_FOUND,
    ERROR,
    OFFLINE,
}

enum class R12ContentListKind {
    CONTENTS,
    DRAFTS,
    REVIEWS,
    ANALYTICS,
}

data class R12ContentListFailure(
    val phase: R12ContentListPhase,
    val retryAfterSeconds: Long? = null,
)

data class R12ContentListState(
    val phase: R12ContentListPhase = R12ContentListPhase.LOADING,
    val items: List<ContentResource> = emptyList(),
    val page: R07PageMeta? = null,
    val selectedStatus: String? = null,
    val refreshing: Boolean = false,
    val appending: Boolean = false,
    val partialFailure: Boolean = false,
    val actionContentId: String? = null,
    val notice: String? = null,
    val failure: R12ContentListFailure? = null,
) {
    val canLoadMore: Boolean
        get() = page?.canLoadMore() == true && !refreshing && !appending

    fun loading(refresh: Boolean = false, append: Boolean = false): R12ContentListState = when {
        append -> copy(appending = true, partialFailure = false, notice = null)
        refresh && items.isNotEmpty() -> copy(refreshing = true, partialFailure = false, notice = null)
        else -> copy(
            phase = R12ContentListPhase.LOADING,
            refreshing = false,
            appending = false,
            partialFailure = false,
            notice = null,
            failure = null,
        )
    }

    fun loaded(value: ContentPageResource, append: Boolean = false): R12ContentListState {
        val merged = if (append) (items + value.items).distinctBy(ContentResource::id) else value.items
        return copy(
            phase = if (merged.isEmpty()) R12ContentListPhase.EMPTY else R12ContentListPhase.CONTENT,
            items = merged,
            page = value.page,
            refreshing = false,
            appending = false,
            partialFailure = false,
            notice = null,
            failure = null,
        )
    }

    fun failed(value: R07CallResult.Failure): R12ContentListState {
        if (items.isNotEmpty()) {
            return copy(
                refreshing = false,
                appending = false,
                partialFailure = true,
                failure = R12ContentListFailure(R12ContentListPhase.CONTENT, value.retryAfterSeconds),
            )
        }
        val mapped = when (value.statusCode) {
            null -> R12ContentListPhase.OFFLINE
            403 -> R12ContentListPhase.FORBIDDEN
            404 -> R12ContentListPhase.NOT_FOUND
            else -> R12ContentListPhase.ERROR
        }
        return copy(
            phase = mapped,
            refreshing = false,
            appending = false,
            partialFailure = false,
            failure = R12ContentListFailure(mapped, value.retryAfterSeconds),
        )
    }

    fun actionStarted(contentId: String): R12ContentListState = copy(
        actionContentId = contentId,
        notice = null,
        partialFailure = false,
    )

    fun actionSucceeded(contentId: String, message: String, remove: Boolean): R12ContentListState {
        val nextItems = if (remove) items.filterNot { it.id == contentId } else items
        return copy(
            phase = if (nextItems.isEmpty()) R12ContentListPhase.EMPTY else R12ContentListPhase.CONTENT,
            items = nextItems,
            actionContentId = null,
            notice = message,
            failure = null,
        )
    }

    fun actionFailed(value: R07CallResult.Failure): R12ContentListState = copy(
        actionContentId = null,
        partialFailure = true,
        notice = when (value.statusCode) {
            403 -> "当前账号不能执行此操作"
            404 -> "内容已不存在"
            409 -> "内容已更新，请刷新后重试"
            422 -> "当前内容状态不支持此操作"
            429 -> "操作频繁，请稍后重试"
            null -> "网络不可用，请恢复网络后重试"
            else -> "操作未完成，请稍后重试"
        },
        failure = R12ContentListFailure(R12ContentListPhase.CONTENT, value.retryAfterSeconds),
    )
}

class R12ContentActionKeys {
    private val values = mutableMapOf<String, Pair<String, String>>()

    fun forRequest(action: String, contentId: String, expectedVersion: Long, reason: String? = null): String {
        val slot = "$action:$contentId"
        val fingerprint = "$action:$contentId:$expectedVersion:${reason.orEmpty()}"
        return values[slot]?.takeIf { it.first == fingerprint }?.second
            ?: UUID.randomUUID().toString().also { values[slot] = fingerprint to it }
    }

    fun consume(action: String, contentId: String) {
        values.remove("$action:$contentId")
    }
}

fun ContentResource.canGoOnline(identityVerified: Boolean): Boolean =
    identityVerified && status in setOf("APPROVED", "OFFLINE_BY_OWNER")

fun ContentResource.canGoOffline(): Boolean = status == "ONLINE"

fun ContentResource.canDelete(): Boolean = status !in setOf("DELETED", "BANNED")

fun ContentResource.updatedTimeLabel(): String = updatedAt ?: createdAt ?: "时间待同步"
