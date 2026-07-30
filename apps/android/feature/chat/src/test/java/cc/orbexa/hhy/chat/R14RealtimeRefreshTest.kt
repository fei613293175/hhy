package cc.orbexa.hhy.chat

import cc.orbexa.hhy.network.ChatConversationPageResource
import cc.orbexa.hhy.network.ChatConversationResource
import cc.orbexa.hhy.network.ChatMessagePageResource
import cc.orbexa.hhy.network.ChatPostConversationsByIdReadRequest
import cc.orbexa.hhy.network.ChatSendMessageRequest
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContractR14Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R14RealtimeRefreshTest {
    @Test
    fun authoritativeGapFillExhaustsEveryConversationAndMessagePage() = runBlocking {
        val api = PagingR14Api()

        val result = refreshR14ChatAuthoritatively(api, "access")

        assertEquals(R14AuthoritativeRefreshResult.Success, result)
        assertEquals(listOf(null, "conversation-page-2"), api.conversationCursors)
        assertEquals(
            listOf(
                "conversation-1" to null,
                "conversation-1" to "message-page-2",
                "conversation-2" to null,
            ),
            api.messageCursors,
        )
    }

    @Test
    fun failedAuthoritativePagePreventsGapCompletionSignal() = runBlocking {
        val api = PagingR14Api(failMessages = true)

        val result = refreshR14ChatAuthoritatively(api, "access")

        assertTrue(result is R14AuthoritativeRefreshResult.Failure)
        assertEquals(503, (result as R14AuthoritativeRefreshResult.Failure).statusCode)
    }

    private class PagingR14Api(
        private val failMessages: Boolean = false,
    ) : ContractR14Api {
        val conversationCursors = mutableListOf<String?>()
        val messageCursors = mutableListOf<Pair<String, String?>>()

        override suspend fun conversations(
            accessToken: String,
            page: Int,
            pageSize: Int,
            cursor: String?,
            status: String?,
            keyword: String?,
            sort: String?,
        ): R07CallResult<ChatConversationPageResource> {
            conversationCursors += cursor
            val response = if (cursor == null) {
                ChatConversationPageResource(
                    items = listOf(ChatConversationResource("conversation-1", unreadCount = 0, version = 1)),
                    page = R07PageMeta(pageSize = 100, nextCursor = "conversation-page-2", hasMore = "true"),
                )
            } else {
                ChatConversationPageResource(
                    items = listOf(ChatConversationResource("conversation-2", unreadCount = 0, version = 1)),
                    page = R07PageMeta(pageSize = 100, hasMore = "false"),
                )
            }
            return R07CallResult.Success(response, "request-conversations")
        }

        override suspend fun messages(
            accessToken: String,
            conversationId: String,
            page: Int,
            pageSize: Int,
            cursor: String?,
            status: String?,
            keyword: String?,
            sort: String?,
        ): R07CallResult<ChatMessagePageResource> {
            messageCursors += conversationId to cursor
            if (failMessages) return R07CallResult.Failure(503)
            val hasMore = conversationId == "conversation-1" && cursor == null
            return R07CallResult.Success(
                ChatMessagePageResource(
                    items = emptyList(),
                    page = R07PageMeta(
                        pageSize = 100,
                        nextCursor = if (hasMore) "message-page-2" else null,
                        hasMore = hasMore.toString(),
                    ),
                ),
                "request-messages",
            )
        }

        override suspend fun send(
            accessToken: String,
            conversationId: String,
            idempotencyKey: String,
            request: ChatSendMessageRequest,
        ): R07CallResult<ChatMessageResource> = error("unused")

        override suspend fun read(
            accessToken: String,
            conversationId: String,
            idempotencyKey: String,
            request: ChatPostConversationsByIdReadRequest,
        ): R07CallResult<CommandResultResource> = error("unused")
    }
}
