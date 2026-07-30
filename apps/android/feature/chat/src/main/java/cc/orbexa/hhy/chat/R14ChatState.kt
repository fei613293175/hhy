package cc.orbexa.hhy.chat

import cc.orbexa.hhy.network.ChatContactCardMessageRequest
import cc.orbexa.hhy.network.ChatContentCardMessageRequest
import cc.orbexa.hhy.network.ChatImageMessageRequest
import cc.orbexa.hhy.network.ChatMessagePageResource
import cc.orbexa.hhy.network.ChatMessagePayload
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatSendMessageRequest
import cc.orbexa.hhy.network.ChatTextMessageRequest
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class R14ChatPhase {
    LOADING,
    CONNECTING,
    CONTENT,
    EMPTY,
    REFRESHING,
    APPENDING,
    PARTIAL_ERROR,
    OFFLINE,
    BLOCKED,
    FORBIDDEN,
    NOT_FOUND,
    ERROR,
}

enum class R14OutgoingDelivery { SENDING, FAILED }

data class R14OutgoingMessage(
    val request: ChatSendMessageRequest,
    val idempotencyKey: String,
    val delivery: R14OutgoingDelivery = R14OutgoingDelivery.SENDING,
    val failure: R07CallResult.Failure? = null,
) {
    val clientMessageId: String get() = request.clientMessageId
    val messageType: String get() = request.messageType
    val payload: ChatMessagePayload get() = when (request) {
        is ChatTextMessageRequest -> request.payload
        is ChatImageMessageRequest -> request.payload
        is ChatContentCardMessageRequest -> request.payload
        is ChatContactCardMessageRequest -> request.payload
    }
}

data class R14ChatState(
    val phase: R14ChatPhase = R14ChatPhase.LOADING,
    val messages: List<ChatMessageResource> = emptyList(),
    val outgoing: List<R14OutgoingMessage> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val failure: R07CallResult.Failure? = null,
    val requestGeneration: Long = 0,
) {
    fun loadStarted(refresh: Boolean = false, append: Boolean = false): R14ChatState = copy(
        phase = when {
            append && messages.isNotEmpty() -> R14ChatPhase.APPENDING
            refresh && messages.isNotEmpty() -> R14ChatPhase.REFRESHING
            messages.isNotEmpty() -> R14ChatPhase.CONNECTING
            else -> R14ChatPhase.LOADING
        },
        failure = null,
        requestGeneration = requestGeneration + 1,
    )

    fun loaded(page: ChatMessagePageResource, append: Boolean = false): R14ChatState {
        val combined = if (append) messages + page.items else page.items
        val merged = combined.distinctBy(ChatMessageResource::id).sortedWith(
            compareBy<ChatMessageResource> { it.serverSequence ?: Long.MAX_VALUE }.thenBy { it.createdAt },
        )
        return copy(
            phase = if (merged.isEmpty()) R14ChatPhase.EMPTY else R14ChatPhase.CONTENT,
            messages = merged,
            nextCursor = page.page.nextCursor,
            hasMore = page.page.canLoadMore(),
            failure = null,
        )
    }

    fun loadFailed(value: R07CallResult.Failure): R14ChatState = copy(
        phase = if (messages.isNotEmpty()) R14ChatPhase.PARTIAL_ERROR else value.toR14Phase(),
        failure = value,
    )

    fun sendStarted(request: ChatSendMessageRequest, idempotencyKey: String): R14ChatState {
        val item = R14OutgoingMessage(request, idempotencyKey)
        return copy(
            phase = if (messages.isEmpty()) R14ChatPhase.EMPTY else R14ChatPhase.CONTENT,
            outgoing = outgoing.filterNot { it.clientMessageId == request.clientMessageId } + item,
            failure = null,
        )
    }

    fun sendSucceeded(clientMessageId: String, message: ChatMessageResource): R14ChatState = copy(
        phase = R14ChatPhase.CONTENT,
        messages = (messages + message).distinctBy(ChatMessageResource::id).sortedWith(
            compareBy<ChatMessageResource> { it.serverSequence ?: Long.MAX_VALUE }.thenBy { it.createdAt },
        ),
        outgoing = outgoing.filterNot { it.clientMessageId == clientMessageId },
        failure = null,
    )

    fun sendFailed(clientMessageId: String, value: R07CallResult.Failure): R14ChatState = copy(
        phase = if (value.isR14Blocked()) R14ChatPhase.BLOCKED else phase.contentFallback(),
        outgoing = outgoing.map {
            if (it.clientMessageId == clientMessageId) it.copy(delivery = R14OutgoingDelivery.FAILED, failure = value) else it
        },
        failure = value,
    )

    fun removeFailed(clientMessageId: String): R14ChatState = copy(
        outgoing = outgoing.filterNot { it.clientMessageId == clientMessageId },
    )

    fun peer(currentUserId: String): PublisherSummaryResource? = messages
        .asSequence()
        .map(ChatMessageResource::sender)
        .firstOrNull { it.userId != currentUserId }

    fun canSend(): Boolean = phase !in setOf(
        R14ChatPhase.OFFLINE,
        R14ChatPhase.BLOCKED,
        R14ChatPhase.FORBIDDEN,
        R14ChatPhase.NOT_FOUND,
    )
}

class R14IntentKeys {
    private val values = mutableMapOf<String, String>()

    fun key(operation: String, fingerprint: String): String =
        values.getOrPut("$operation:$fingerprint") { "r14-${UUID.randomUUID()}" }

    fun complete(operation: String, fingerprint: String) {
        values.remove("$operation:$fingerprint")
    }
}

internal fun R07CallResult.Failure.toR14Phase(): R14ChatPhase = when {
    statusCode == null -> R14ChatPhase.OFFLINE
    statusCode == 403 -> R14ChatPhase.FORBIDDEN
    statusCode == 404 -> R14ChatPhase.NOT_FOUND
    isR14Blocked() -> R14ChatPhase.BLOCKED
    else -> R14ChatPhase.ERROR
}

internal fun R07CallResult.Failure.isR14Blocked(): Boolean =
    statusCode == 422 && errorCode.orEmpty().contains("BLOCK", ignoreCase = true)

internal fun R07CallResult.Failure.r14UserMessage(): String = when (statusCode) {
    null -> "网络不可用，已保留当前消息，请恢复网络后重试"
    400 -> fieldErrors.values.firstOrNull() ?: "消息内容不符合发送要求"
    401 -> "登录状态已失效，请重新登录"
    403 -> "当前无法访问此会话"
    404 -> "会话已失效或不存在"
    409 -> "会话状态已经变化，请刷新后再试"
    422 -> if (isR14Blocked()) "当前会话已限制发送消息" else "当前状态无法发送这条消息"
    429 -> retryAfterSeconds?.let { "发送较频繁，请 $it 秒后重试" } ?: "发送较频繁，请稍后重试"
    else -> "消息暂未发送，请稍后重试"
}

internal fun chatTimeGroup(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String? =
    runCatching {
        DateTimeFormatter.ofPattern("MM月dd日 HH:mm").format(OffsetDateTime.parse(value).atZoneSameInstant(zoneId))
    }.getOrNull()

internal fun chatTimeLabel(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String? =
    runCatching {
        DateTimeFormatter.ofPattern("HH:mm").format(OffsetDateTime.parse(value).atZoneSameInstant(zoneId))
    }.getOrNull()

internal fun deliveryLabel(status: String, readAt: String?): String = when {
    status == "READ" || readAt != null -> "已读"
    status == "DELIVERED" -> "已送达"
    else -> "已发送"
}

private fun R14ChatPhase.contentFallback(): R14ChatPhase = when {
    this == R14ChatPhase.EMPTY -> R14ChatPhase.EMPTY
    this in setOf(R14ChatPhase.OFFLINE, R14ChatPhase.BLOCKED, R14ChatPhase.FORBIDDEN, R14ChatPhase.NOT_FOUND) -> this
    else -> R14ChatPhase.CONTENT
}
