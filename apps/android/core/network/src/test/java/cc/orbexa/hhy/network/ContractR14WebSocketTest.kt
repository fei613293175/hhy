package cc.orbexa.hhy.network

import java.net.URI
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractR14WebSocketTest {
    private val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl"

    @Test
    fun safeWssValidationRejectsCredentialsQueriesFragmentsPathsAndPlaceholders() {
        assertEquals("wss://ws.orbexa.cc", validateR14WebSocketBaseUrl("wss://ws.orbexa.cc/"))
        listOf(
            "ws://ws.orbexa.cc",
            "wss://user:pass@ws.orbexa.cc",
            "wss://ws.orbexa.cc/path",
            "wss://ws.orbexa.cc?token=x",
            "wss://ws.orbexa.cc/#fragment",
            "wss://ws.example.invalid",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                validateR14WebSocketBaseUrl(value)
            }
        }
    }

    @Test
    fun handshakeUsesOnlyTheTwoFrozenSubprotocolsAndNeverPlacesTokenInUrl() {
        val request = r14WebSocketRequest("wss://ws.orbexa.cc/ws", token, 42)

        assertEquals(
            "hhy.v1, hhy.access.$token",
            request.header("Sec-WebSocket-Protocol"),
        )
        assertEquals("42", request.url.queryParameter("lastServerSequence"))
        assertFalse(request.url.toString().contains(token))
        assertNull(request.header("Authorization"))
        // OkHttp normalizes ws/wss to http/https internally; the source base was
        // validated as wss before the Request is created.
        assertEquals("https", URI(request.url.toString()).scheme)
    }

    @Test
    fun compactCredentialRejectsPaddingWhitespaceAndMalformedValuesWithoutEchoingIt() {
        listOf(
            "aaa.bbb.ccc=",
            "aaa.bbb. ccc",
            "aaa.bbb",
            "token",
        ).forEach { value ->
            val failure = assertThrows(IllegalArgumentException::class.java) {
                r14WebSocketRequest("wss://ws.orbexa.cc/ws", value, 0)
            }
            assertFalse(failure.message.orEmpty().contains(value))
        }
    }

    @Test
    fun serverMustSelectExactlyTheFrozenPublicSubprotocol() {
        assertTrue(isR14SelectedSubprotocol("hhy.v1"))
        assertFalse(isR14SelectedSubprotocol(null))
        assertFalse(isR14SelectedSubprotocol("hhy.access.$token"))
        assertFalse(isR14SelectedSubprotocol("hhy.v1, hhy.access.$token"))
        assertFalse(isR14SelectedSubprotocol(" hhy.v1"))
    }

    @Test
    fun typedChatEventAdvancesContiguousWatermarkAndBuildsDeliveryAck() {
        val tracker = R14SequenceTracker()
        val envelope = decodeR14RealtimeEnvelope(chatEvent(sequence = 1, ackRequired = true))

        val decision = tracker.accept(envelope) as R14SequenceDecision.Deliver

        assertEquals(1, tracker.highestContiguousSequence)
        assertEquals("conversation-1", (decision.event as R14RealtimeEvent.ChatChanged).conversationId)
        val acknowledgement = decision.acknowledgement
        assertNotNull(acknowledgement)
        val ack = HhyNetworkJson.value.parseToJsonElement(requireNotNull(acknowledgement)).jsonObject
        assertEquals("system.delivery.ack", ack["eventType"]?.jsonPrimitive?.content)
        assertEquals(envelope.eventId, ack["payload"]?.jsonObject?.get("eventId")?.jsonPrimitive?.content)
        assertEquals(1L, ack["payload"]?.jsonObject?.get("serverSequence")?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun nonPersistedTypingDoesNotTriggerAnAuthoritativeRestRefresh() {
        val typing = decodeR14RealtimeEnvelope(
            """
                {
                  "eventId":"00000000-0000-0000-0000-000000000003",
                  "eventType":"chat.typing",
                  "occurredAt":"2026-07-28T01:00:00Z",
                  "conversationId":"conversation-1",
                  "ackRequired":false,
                  "payload":{
                    "conversationId":"conversation-1",
                    "userId":"user-1",
                    "typing":true,
                    "expiresAt":"2026-07-28T01:00:10Z"
                  }
                }
            """.trimIndent(),
        )

        val tracker = R14SequenceTracker()
        val decision = tracker.accept(typing) as R14SequenceDecision.Deliver

        assertNull(decision.event)
        assertEquals(0, tracker.highestContiguousSequence)
    }

    @Test
    fun duplicateIsNotDeliveredButIsAcknowledgedAgain() {
        val tracker = R14SequenceTracker()
        val envelope = decodeR14RealtimeEnvelope(chatEvent(sequence = 1, ackRequired = true))
        tracker.accept(envelope)

        val duplicate = tracker.accept(envelope) as R14SequenceDecision.Duplicate

        assertNotNull(duplicate.acknowledgement)
        assertEquals(1, tracker.highestContiguousSequence)
    }

    @Test
    fun nonContiguousSequenceRequiresReconnectFromLastCompletedWatermark() {
        val tracker = R14SequenceTracker()

        val gap = tracker.accept(
            decodeR14RealtimeEnvelope(chatEvent(sequence = 2, ackRequired = true)),
        ) as R14SequenceDecision.Gap

        assertEquals(1, gap.expected)
        assertEquals(2, gap.received)
        assertEquals(0, tracker.highestContiguousSequence)
    }

    @Test
    fun replayCompletesOnlyAtTheContiguousHighWatermark() {
        val tracker = R14SequenceTracker()
        tracker.accept(decodeR14RealtimeEnvelope(chatEvent(sequence = 1, ackRequired = false)))

        assertTrue(tracker.accept(decodeR14RealtimeEnvelope(resumeEvent("REPLAY_COMPLETE", 1, emptyList()))) is R14SequenceDecision.ReplayComplete)
        assertTrue(tracker.accept(decodeR14RealtimeEnvelope(resumeEvent("REPLAY_COMPLETE", 2, emptyList()))) is R14SequenceDecision.Gap)
    }

    @Test
    fun chatGapCanCompleteAfterAuthoritativeRefreshButNotificationsCannotBeFaked() {
        val chatTracker = R14SequenceTracker()
        val chatGap = chatTracker.accept(
            decodeR14RealtimeEnvelope(resumeEvent("REST_GAP_FILL", 17, listOf("CHAT"))),
        ) as R14SequenceDecision.RestGap
        assertEquals(setOf(R14RealtimeScope.CHAT), chatGap.gap.affectedScopes)
        assertFalse(chatTracker.completeGap(17, emptySet()))
        assertTrue(chatTracker.completeGap(17, setOf(R14RealtimeScope.CHAT)))
        assertEquals(17, chatTracker.highestContiguousSequence)

        val notificationTracker = R14SequenceTracker()
        notificationTracker.accept(
            decodeR14RealtimeEnvelope(
                resumeEvent("REST_GAP_FILL", 21, listOf("CHAT", "NOTIFICATIONS")),
            ),
        )
        assertFalse(
            notificationTracker.completeGap(
                21,
                setOf(R14RealtimeScope.CHAT, R14RealtimeScope.NOTIFICATIONS),
            ),
        )
        assertEquals(0, notificationTracker.highestContiguousSequence)
    }

    @Test
    fun reconnectUsesBoundedFullJitterExponentialBackoff() {
        assertEquals(500, r14ReconnectDelayMillis(0) { upper -> upper - 1 })
        assertEquals(1_000, r14ReconnectDelayMillis(1) { upper -> upper - 1 })
        assertEquals(30_000, r14ReconnectDelayMillis(20) { upper -> upper - 1 })
        assertEquals(0, r14ReconnectDelayMillis(20) { 0 })
    }

    private fun chatEvent(sequence: Long, ackRequired: Boolean): String = """
        {
          "eventId":"00000000-0000-0000-0000-000000000001",
          "eventType":"chat.message.new",
          "occurredAt":"2026-07-28T01:00:00Z",
          "serverSequence":$sequence,
          "conversationId":"conversation-1",
          "ackRequired":$ackRequired,
          "payload":{"conversationId":"conversation-1"}
        }
    """.trimIndent()

    private fun resumeEvent(mode: String, watermark: Long, scopes: List<String>): String {
        val encodedScopes = scopes.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        return """
            {
              "eventId":"00000000-0000-0000-0000-000000000002",
              "eventType":"system.resume",
              "occurredAt":"2026-07-28T01:00:00Z",
              "ackRequired":false,
              "payload":{
                "mode":"$mode",
                "requestedLastServerSequence":0,
                "serverHighWatermark":$watermark,
                "resumeFromServerSequence":$watermark,
                "affectedScopes":$encodedScopes
              }
            }
        """.trimIndent()
    }
}
