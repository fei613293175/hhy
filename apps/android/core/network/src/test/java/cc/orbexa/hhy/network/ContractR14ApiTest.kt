package cc.orbexa.hhy.network

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
    fun transportRejectsUnsafeRootsAndInvalidConversationIds() {
        assertThrows(IllegalArgumentException::class.java) { UrlConnectionContractR14Api("http://api.orbexa.cc") }
        assertThrows(IllegalArgumentException::class.java) { UrlConnectionContractR14Api("https://api.invalid") }
        assertThrows(IllegalArgumentException::class.java) { requireFrozenR14Id("../conversation") }
    }
}
