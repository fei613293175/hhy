package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

data class R12ReviewTimelineEntry(
    val reviewId: String,
    val decision: String?,
    val reason: String?,
    val createdAt: String?,
) {
    companion object {
        fun from(resource: ContentResource): R12ReviewTimelineEntry? {
            val review = runCatching { resource.attributes?.get("review")?.jsonObject }.getOrNull() ?: return null
            val reviewId = review["id"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
                ?: return null
            return R12ReviewTimelineEntry(
                reviewId = reviewId,
                decision = review["decision"]?.jsonPrimitive?.contentOrNull,
                reason = review["reason"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                createdAt = review["createdAt"]?.jsonPrimitive?.contentOrNull ?: resource.updatedAt,
            )
        }
    }
}

data class R12ReviewHistoryState(
    val contentId: String,
    val generation: Long = 0,
    val summary: ContentResource? = null,
    val entries: List<R12ReviewTimelineEntry> = emptyList(),
    val page: R07PageMeta? = null,
    val summaryLoading: Boolean = true,
    val timelineLoading: Boolean = true,
    val refreshing: Boolean = false,
    val appending: Boolean = false,
    val summaryFailure: R12ContentListFailure? = null,
    val timelineFailure: R12ContentListFailure? = null,
) {
    val phase: R12ContentListPhase
        get() = when {
            summary != null -> R12ContentListPhase.CONTENT
            summaryLoading -> R12ContentListPhase.LOADING
            summaryFailure != null -> summaryFailure.phase
            else -> R12ContentListPhase.ERROR
        }

    val canLoadMore: Boolean
        get() = summary != null && page?.canLoadMore() == true && !refreshing && !appending && !timelineLoading

    val partialFailure: Boolean
        get() = summary != null && (summaryFailure != null || timelineFailure != null)

    fun accepts(requestContentId: String, requestGeneration: Long): Boolean =
        contentId == requestContentId && generation == requestGeneration

    fun reloadStarted(requestGeneration: Long, refresh: Boolean): R12ReviewHistoryState = copy(
        generation = requestGeneration,
        summary = if (refresh) summary else null,
        entries = if (refresh) entries else emptyList(),
        page = null,
        summaryLoading = true,
        timelineLoading = true,
        refreshing = refresh,
        appending = false,
        summaryFailure = null,
        timelineFailure = null,
    )

    fun summaryLoaded(value: ContentResource): R12ReviewHistoryState = copy(
        summary = value,
        summaryLoading = false,
        refreshing = refreshing && timelineLoading,
        summaryFailure = null,
    )

    fun summaryFailed(value: R07CallResult.Failure): R12ReviewHistoryState = copy(
        summaryLoading = false,
        refreshing = refreshing && timelineLoading,
        summaryFailure = value.toContentListFailure(),
    )

    fun timelineLoaded(value: ContentPageResource, append: Boolean = false): R12ReviewHistoryState {
        val parsed = value.items.mapNotNull(R12ReviewTimelineEntry::from)
        val invalidRecordPresent = parsed.size != value.items.size
        val merged = if (append) (entries + parsed).distinctBy(R12ReviewTimelineEntry::reviewId) else parsed
        return copy(
            entries = merged,
            page = value.page,
            timelineLoading = false,
            refreshing = refreshing && summaryLoading,
            appending = false,
            timelineFailure = if (invalidRecordPresent) {
                R12ContentListFailure(R12ContentListPhase.ERROR)
            } else {
                null
            },
        )
    }

    fun timelineFailed(value: R07CallResult.Failure): R12ReviewHistoryState = copy(
        timelineLoading = false,
        refreshing = refreshing && summaryLoading,
        appending = false,
        timelineFailure = value.toContentListFailure(),
    )

    fun appendStarted(): R12ReviewHistoryState = if (canLoadMore) {
        copy(appending = true, timelineFailure = null)
    } else {
        this
    }
}

private fun R07CallResult.Failure.toContentListFailure(): R12ContentListFailure {
    val phase = when (statusCode) {
        null -> R12ContentListPhase.OFFLINE
        403 -> R12ContentListPhase.FORBIDDEN
        404 -> R12ContentListPhase.NOT_FOUND
        else -> R12ContentListPhase.ERROR
    }
    return R12ContentListFailure(phase, retryAfterSeconds)
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

fun ContentResource.updatedTimeLabel(): String = (updatedAt ?: createdAt).r12BusinessTimeLabel()

private val r12BusinessTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val r12BusinessZone = ZoneId.of("Asia/Shanghai")

fun String?.r12BusinessTimeLabel(): String {
    val raw = this?.takeIf(String::isNotBlank) ?: return "时间待同步"
    val instant = runCatching { Instant.parse(raw) }
        .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
        .getOrNull()
        ?: return "时间待同步"
    return r12BusinessTimeFormatter.format(instant.atZone(r12BusinessZone))
}
