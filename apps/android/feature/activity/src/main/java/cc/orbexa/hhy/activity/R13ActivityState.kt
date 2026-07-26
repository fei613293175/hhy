package cc.orbexa.hhy.activity

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

enum class R13ListPhase {
    LOADING, CONTENT, EMPTY, REFRESHING, APPENDING, PARTIAL_ERROR,
    ERROR, OFFLINE, FORBIDDEN, NOT_FOUND,
}

data class R13ActivityListState(
    val phase: R13ListPhase = R13ListPhase.LOADING,
    val items: List<ContentResource> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val total: String? = null,
    val requestId: String? = null,
    val retryAfterSeconds: Long? = null,
    val requestGeneration: Long = 0,
) {
    fun loadStarted(refresh: Boolean = false, append: Boolean = false): R13ActivityListState = copy(
        phase = when {
            append && items.isNotEmpty() -> R13ListPhase.APPENDING
            refresh && items.isNotEmpty() -> R13ListPhase.REFRESHING
            else -> R13ListPhase.LOADING
        },
        requestGeneration = requestGeneration + 1,
        requestId = null,
        retryAfterSeconds = null,
    )

    fun loaded(page: ContentPageResource, append: Boolean = false): R13ActivityListState {
        val values = if (append) (items + page.items).distinctBy(ContentResource::id) else page.items.distinctBy(ContentResource::id)
        return copy(
            phase = if (values.isEmpty()) R13ListPhase.EMPTY else R13ListPhase.CONTENT,
            items = values,
            nextCursor = page.page.nextCursor,
            hasMore = page.page.canLoadMore(),
            total = page.page.total,
            requestId = null,
            retryAfterSeconds = null,
        )
    }

    fun failed(failure: R07CallResult.Failure): R13ActivityListState = copy(
        phase = if (items.isNotEmpty()) R13ListPhase.PARTIAL_ERROR else failure.phase(),
        requestId = failure.requestId,
        retryAfterSeconds = failure.retryAfterSeconds,
    )

    fun remove(id: String): R13ActivityListState {
        val remaining = items.filterNot { it.id == id }
        return copy(
            phase = if (remaining.isEmpty()) R13ListPhase.EMPTY else R13ListPhase.CONTENT,
            items = remaining,
            total = total?.toLongOrNull()?.let { (it - 1).coerceAtLeast(0).toString() } ?: total,
        )
    }
}

class R13IntentKeys {
    private val values = mutableMapOf<String, String>()

    fun key(operation: String, fingerprint: String): String =
        values.getOrPut("$operation:$fingerprint") { "r13-${UUID.randomUUID()}" }

    fun complete(operation: String, fingerprint: String) {
        values.remove("$operation:$fingerprint")
    }
}

internal fun R07CallResult.Failure.phase(): R13ListPhase = when (statusCode) {
    null -> R13ListPhase.OFFLINE
    403 -> R13ListPhase.FORBIDDEN
    404 -> R13ListPhase.NOT_FOUND
    else -> R13ListPhase.ERROR
}

internal fun contentTypeLabel(value: String): String = when (value) {
    "PROJECT" -> "项目"
    "APP" -> "APP"
    "GROUP_CHAT" -> "群聊"
    "TEAM_LEADER" -> "团队长"
    else -> "内容"
}

internal fun secureActivityMediaUrl(value: String?): String? = value?.takeIf { candidate ->
    runCatching {
        val uri = URI.create(candidate)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}

internal fun activityTime(value: ContentResource): Instant? =
    value.updatedAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: value.createdAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

internal fun activityDateGroup(value: ContentResource, now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): String {
    val date = activityTime(value)?.atZone(zoneId)?.toLocalDate() ?: return "更早"
    val today = now.atZone(zoneId).toLocalDate()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "更早"
    }
}

internal fun activityTimeLabel(value: ContentResource, zoneId: ZoneId = ZoneId.systemDefault()): String? =
    activityTime(value)?.atZone(zoneId)?.let { time ->
        "%02d:%02d".format(time.hour, time.minute)
    }
