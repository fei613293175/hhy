package cc.orbexa.hhy.chat

import cc.orbexa.hhy.network.ChatConversationPageResource
import cc.orbexa.hhy.network.ChatConversationResource
import cc.orbexa.hhy.network.R07CallResult
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

enum class R14ConversationPhase {
    CONNECTING,
    CONTENT,
    EMPTY,
    OFFLINE,
    SYNCING,
    ERROR,
}

data class R14ConversationListState(
    val phase: R14ConversationPhase = R14ConversationPhase.CONNECTING,
    val items: List<ChatConversationResource> = emptyList(),
    val activeQuery: String = "",
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val failure: R07CallResult.Failure? = null,
    val requestGeneration: Long = 0,
    val appending: Boolean = false,
) {
    fun loadStarted(query: String, append: Boolean = false): R14ConversationListState = copy(
        phase = if (items.isEmpty() && !append) R14ConversationPhase.CONNECTING else R14ConversationPhase.SYNCING,
        activeQuery = query.trim(),
        failure = null,
        requestGeneration = requestGeneration + 1,
        appending = append,
    )

    fun loaded(
        generation: Long,
        query: String,
        page: ChatConversationPageResource,
        append: Boolean = false,
    ): R14ConversationListState {
        if (generation != requestGeneration || query.trim() != activeQuery) return this
        val merged = if (append) mergeInServerOrder(items, page.items) else mergeInServerOrder(emptyList(), page.items)
        return copy(
            phase = if (merged.isEmpty()) R14ConversationPhase.EMPTY else R14ConversationPhase.CONTENT,
            items = merged,
            nextCursor = page.page.nextCursor,
            hasMore = page.page.canLoadMore(),
            failure = null,
            appending = false,
        )
    }

    fun loadFailed(generation: Long, failure: R07CallResult.Failure): R14ConversationListState {
        if (generation != requestGeneration) return this
        return copy(
            phase = when {
                items.isNotEmpty() -> R14ConversationPhase.CONTENT
                failure.statusCode == null -> R14ConversationPhase.OFFLINE
                else -> R14ConversationPhase.ERROR
            },
            failure = failure,
            appending = false,
        )
    }

    fun syncStarted(): R14ConversationListState = copy(
        phase = if (items.isEmpty()) R14ConversationPhase.CONNECTING else R14ConversationPhase.SYNCING,
        failure = null,
    )

    fun synced(updates: List<ChatConversationResource>): R14ConversationListState {
        val merged = mergeInServerOrder(items, updates)
        return copy(
            phase = if (merged.isEmpty()) R14ConversationPhase.EMPTY else R14ConversationPhase.CONTENT,
            items = merged,
            failure = null,
        )
    }

    fun removed(conversationId: String): R14ConversationListState {
        val remaining = items.filterNot { it.id == conversationId }
        return copy(
            phase = if (remaining.isEmpty()) R14ConversationPhase.EMPTY else R14ConversationPhase.CONTENT,
            items = remaining,
            failure = null,
        )
    }

    fun canAppend(): Boolean = hasMore && !appending && !nextCursor.isNullOrBlank()
}

internal fun mergeInServerOrder(
    current: List<ChatConversationResource>,
    incoming: List<ChatConversationResource>,
): List<ChatConversationResource> {
    val merged = LinkedHashMap<String, ChatConversationResource>()
    current.forEach { merged[it.id] = it }
    incoming.forEach { merged[it.id] = it }
    return merged.values.toList()
}

internal suspend fun <T> r14CallWithRetry(
    call: suspend () -> R07CallResult<T>,
    pause: suspend (Long) -> Unit = { delay(it) },
): R07CallResult<T> {
    var retry = 0
    while (true) {
        val result = call()
        val retryable = result is R07CallResult.Failure && (result.statusCode == null || result.retryable)
        if (!retryable || retry >= 2) return result
        pause(250L shl retry)
        retry += 1
    }
}

internal fun R07CallResult.Failure.r14ConversationMessage(): String = when (statusCode) {
    null -> "网络不可用，请检查连接后重试"
    400 -> fieldErrors.values.firstOrNull() ?: "搜索条件不符合要求"
    401 -> "登录状态已失效，请重新登录"
    403 -> "当前无法访问会话列表"
    404 -> "会话列表暂不可用"
    409 -> "会话状态已经变化，请刷新后重试"
    422 -> "当前条件无法查询会话"
    429 -> retryAfterSeconds?.let { "请求较频繁，请 $it 秒后重试" } ?: "请求较频繁，请稍后重试"
    else -> "会话暂时无法加载，请稍后重试"
}

internal fun conversationTimeLabel(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId),
): String? = value?.let {
    runCatching {
        val time = OffsetDateTime.parse(it).atZoneSameInstant(zoneId)
        when (time.toLocalDate()) {
            today -> DateTimeFormatter.ofPattern("HH:mm").format(time)
            today.minusDays(1) -> "昨天"
            else -> if (time.year == today.year) {
                DateTimeFormatter.ofPattern("MM-dd").format(time)
            } else {
                DateTimeFormatter.ofPattern("yyyy-MM-dd").format(time)
            }
        }
    }.getOrNull()
}
