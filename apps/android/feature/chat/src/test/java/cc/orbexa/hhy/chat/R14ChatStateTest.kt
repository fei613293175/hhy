package cc.orbexa.hhy.chat

import cc.orbexa.hhy.network.ChatMessagePageResource
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatTextMessageRequest
import cc.orbexa.hhy.network.ChatTextPayload
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R14ChatStateTest {
    @Test
    fun historyAppendDeduplicatesAndKeepsServerOrder() {
        val loaded = R14ChatState().loaded(page(message("2", 2), message("3", 3), more = true))
        val appended = loaded.loadStarted(append = true).loaded(page(message("1", 1), message("2", 2)), append = true)

        assertEquals(listOf("1", "2", "3"), appended.messages.map { it.id })
        assertEquals(R14ChatPhase.CONTENT, appended.phase)
    }

    @Test
    fun refreshAndHistoryFailureKeepExistingMessages() {
        val loaded = R14ChatState().loaded(page(message("1", 1)))
        val refreshFailure = loaded.loadStarted(refresh = true).loadFailed(R07CallResult.Failure(500))
        val historyFailure = loaded.loadStarted(append = true).loadFailed(R07CallResult.Failure(null))

        assertEquals(R14ChatPhase.PARTIAL_ERROR, refreshFailure.phase)
        assertEquals(R14ChatPhase.PARTIAL_ERROR, historyFailure.phase)
        assertEquals(listOf("1"), historyFailure.messages.map { it.id })
    }

    @Test
    fun failedSendRetainsRequestAndStableRetryIdentity() {
        val keys = R14IntentKeys()
        val request = ChatTextMessageRequest("client-1", payload = ChatTextPayload("保留消息"))
        val firstKey = keys.key("send", request.clientMessageId)
        val failed = R14ChatState().sendStarted(request, firstKey).sendFailed("client-1", R07CallResult.Failure(429, retryAfterSeconds = 8))

        assertEquals(firstKey, failed.outgoing.single().idempotencyKey)
        assertEquals("client-1", failed.outgoing.single().clientMessageId)
        assertEquals(R14OutgoingDelivery.FAILED, failed.outgoing.single().delivery)
        assertEquals(firstKey, keys.key("send", request.clientMessageId))
        keys.complete("send", request.clientMessageId)
        assertNotEquals(firstKey, keys.key("send", request.clientMessageId))
    }

    @Test
    fun blockedAndOfflineDisableWritingWithoutRemovingMessages() {
        val loaded = R14ChatState().loaded(page(message("1", 1)))
        val blocked = loaded.sendStarted(
            ChatTextMessageRequest("client-1", payload = ChatTextPayload("消息")),
            "r14-key-1234567890",
        ).sendFailed("client-1", R07CallResult.Failure(422, errorCode = "CHAT-422-PEER_BLOCKED"))
        val offline = loaded.loadFailed(R07CallResult.Failure(null))

        assertEquals(R14ChatPhase.BLOCKED, blocked.phase)
        assertTrue(!blocked.canSend())
        assertEquals(R14ChatPhase.PARTIAL_ERROR, offline.phase)
        assertEquals(1, offline.messages.size)
    }

    private fun page(vararg values: ChatMessageResource, more: Boolean = false) = ChatMessagePageResource(
        values.toList(),
        R07PageMeta(page = 1, pageSize = 20, total = values.size.toString(), nextCursor = if (more) "1" else null, hasMore = more.toString()),
    )

    private fun message(id: String, sequence: Long) = ChatMessageResource(
        id = id,
        conversationId = "42",
        sender = PublisherSummaryResource("7", "真实用户", verified = false),
        clientMessageId = "client-$id",
        messageType = "TEXT",
        payload = ChatTextPayload("消息$id"),
        status = "SENT",
        serverSequence = sequence,
        createdAt = "2026-07-28T01:00:0${sequence}Z",
    )
}
