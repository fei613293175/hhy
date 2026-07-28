package cc.orbexa.hhy.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractR14ApiTest {
    @Test
    fun fourFrozenRequestsEncodeWithTheirDiscriminator() {
        val requests = listOf(
            ChatTextMessageRequest("client-text", payload = ChatTextPayload("真实消息")),
            ChatImageMessageRequest("client-image", payload = ChatImagePayload("media_1", "https://media.orbexa.cc/chat/1")),
            ChatContentCardMessageRequest("client-content", payload = ChatContentCardPayload("content_1", "PROJECT", "真实项目")),
            ChatContactCardMessageRequest("client-contact", payload = ChatContactCardPayload(listOf(ChatContactField("WECHAT", value = "real-id")))),
        )

        assertEquals(listOf("TEXT", "IMAGE", "CONTENT_CARD", "CONTACT_CARD"), requests.map { it.messageType })
        requests.forEach { request ->
            val json = encodeChatSendRequest(request)
            assertTrue(json.contains("\"clientMessageId\":\"${request.clientMessageId}\""))
            assertTrue(json.contains("\"messageType\":\"${request.messageType}\""))
            assertFalse(json.contains("requestId"))
            assertFalse(json.contains("idempotencyKey"))
        }
    }

    @Test
    fun responsePayloadIsDecodedByTheFrozenSiblingDiscriminator() {
        val message = decodeChatMessageResource(
            HhyNetworkJson.value.parseToJsonElement(
                """{
                  "id":"100","conversationId":"42",
                  "sender":{"userId":"7","nickname":"真实用户","verified":false},
                  "clientMessageId":"client-content","messageType":"CONTENT_CARD",
                  "payload":{"contentId":"88","contentType":"PROJECT","title":"真实项目"},
                  "status":"DELIVERED","serverSequence":100,"createdAt":"2026-07-28T01:00:00Z"
                }""",
            ).jsonObject,
        )

        assertEquals("CONTENT_CARD", message.messageType)
        assertTrue(message.payload is ChatContentCardPayload)
        assertEquals("真实项目", (message.payload as ChatContentCardPayload).title)
        assertEquals(100L, message.serverSequence)
    }

    @Test
    fun readReceiptDistinguishesMissingNullAndRealTimestamp() {
        fun decode(readAt: String): ChatMessageResource = decodeChatMessageResource(
            HhyNetworkJson.value.parseToJsonElement(
                """{
                  "id":"100","conversationId":"42",
                  "sender":{"userId":"7","nickname":"真实用户","verified":false},
                  "clientMessageId":"client-text","messageType":"TEXT",
                  "payload":{"text":"真实消息"},"status":"SENT",
                  "createdAt":"2026-07-28T01:00:00Z"$readAt
                }""",
            ).jsonObject,
        )

        assertEquals(null, decode("").readAt)
        assertEquals(null, decode(""","readAt":null""").readAt)
        assertEquals(
            "2026-07-28T01:01:00Z",
            decode(""","readAt":"2026-07-28T01:01:00Z"""").readAt,
        )
    }

    @Test
    fun responseRejectsUnknownMessageDiscriminatorAndExtraFields() {
        val unknown = HhyNetworkJson.value.parseToJsonElement(
            """{"id":"1","conversationId":"2","sender":{"userId":"7","nickname":"用户","verified":false},"clientMessageId":"c","messageType":"VOICE","payload":{},"status":"SENT","createdAt":"2026-07-28T01:00:00Z"}""",
        ).jsonObject
        assertThrows(IllegalArgumentException::class.java) { decodeChatMessageResource(unknown) }

        val extra = HhyNetworkJson.value.parseToJsonElement(
            """{"id":"1","conversationId":"2","sender":{"userId":"7","nickname":"用户","verified":false},"clientMessageId":"c","messageType":"TEXT","payload":{"text":"消息"},"status":"SENT","createdAt":"2026-07-28T01:00:00Z","technical":"hidden"}""",
        ).jsonObject
        assertThrows(IllegalArgumentException::class.java) { decodeChatMessageResource(extra) }
    }

    @Test
    fun conversationPageStrictlyDecodesRealPeerPreviewUnreadAndPage() {
        val page = decodeChatConversationPage(
            HhyNetworkJson.value.parseToJsonElement(
                """{
                  "items":[{
                    "id":"conversation_42",
                    "peer":{"userId":"user_7","nickname":"真实用户","avatarUrl":"https://media.orbexa.cc/avatar/7","verified":true},
                    "lastMessage":{"messageId":"message_8","messageType":"TEXT","preview":"真实消息","senderId":"user_7","createdAt":"2026-07-28T01:00:00Z"},
                    "unreadCount":3,"lastReadMessageId":"message_6","updatedAt":"2026-07-28T01:00:00Z","version":4
                  }],
                  "page":{"page":1,"pageSize":20,"total":"1","hasMore":"false"}
                }""",
            ).jsonObject,
        )

        assertEquals("conversation_42", page.items.single().id)
        assertEquals("真实用户", page.items.single().peer?.nickname)
        assertEquals("真实消息", page.items.single().lastMessage?.preview)
        assertEquals(3L, page.items.single().unreadCount)
        assertFalse(page.page.canLoadMore())
    }

    @Test
    fun conversationDecoderRejectsNegativeUnreadAndUnknownFields() {
        val negative = HhyNetworkJson.value.parseToJsonElement(
            """{"id":"conversation_1","unreadCount":-1,"version":0}""",
        ).jsonObject
        assertThrows(IllegalArgumentException::class.java) { decodeChatConversationResource(negative) }

        val extra = HhyNetworkJson.value.parseToJsonElement(
            """{"id":"conversation_1","unreadCount":0,"version":0,"requestId":"hidden"}""",
        ).jsonObject
        assertThrows(IllegalArgumentException::class.java) { decodeChatConversationResource(extra) }
    }

    @Test
    fun conversationRouteEncodesTheFrozenSearchAndCursorParameters() {
        val route = r14ConversationRoute(
            page = 2,
            pageSize = 50,
            cursor = "next/cursor",
            keyword = "真实 联系人",
            sort = "updatedAt:desc",
        )

        assertTrue(route.startsWith("/api/v1/conversations?"))
        assertTrue(route.contains("page=2"))
        assertTrue(route.contains("pageSize=50"))
        assertTrue(route.contains("cursor=next%2Fcursor"))
        assertTrue(route.contains("keyword=%E7%9C%9F%E5%AE%9E+%E8%81%94%E7%B3%BB%E4%BA%BA"))
        assertThrows(IllegalArgumentException::class.java) { r14ConversationRoute(keyword = "x".repeat(101)) }
    }

    @Test
    fun transportRejectsUnsafeRootsAndInvalidConversationIds() {
        assertThrows(IllegalArgumentException::class.java) { UrlConnectionContractR14Api("http://api.orbexa.cc") }
        assertThrows(IllegalArgumentException::class.java) { UrlConnectionContractR14Api("https://api.invalid") }
        assertThrows(IllegalArgumentException::class.java) { requireFrozenR14Id("../conversation") }
    }

    @Test
    fun safetyActionRequestsFollowTheFrozenSchema() {
        val report = ChatReportRequest("SPAM", "", listOf("media_1"), listOf("message_1"), 3)
        assertEquals(
            "{\"reasonCode\":\"SPAM\",\"description\":\"\",\"evidenceMediaIds\":[\"media_1\"],\"messageIds\":[\"message_1\"],\"expectedVersion\":3}",
            HhyNetworkJson.value.encodeToString(report),
        )
        assertEquals("{}", HhyNetworkJson.value.encodeToString(ChatBlockRequest()))
        assertThrows(IllegalArgumentException::class.java) { ChatReportRequest("", "") }
        assertThrows(IllegalArgumentException::class.java) { ChatBlockRequest("x".repeat(2_001)) }
        assertThrows(IllegalArgumentException::class.java) {
            ChatReportRequest("SPAM", "", messageIds = List(101) { "m_$it" })
        }
    }
}
