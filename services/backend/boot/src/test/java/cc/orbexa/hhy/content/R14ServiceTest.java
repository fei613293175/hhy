package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.R14Contracts.BlockRequest;
import cc.orbexa.hhy.content.R14Contracts.ReadRequest;
import cc.orbexa.hhy.content.R14Contracts.ReportRequest;
import cc.orbexa.hhy.content.R14Contracts.SendMessageRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class R14ServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-28T01:00:00Z");
    private static final String KEY = "r14-test-key-0001";
    @Mock private R14Store store;
    @Mock private R08Store shared;
    @Mock private R08Service r08;
    @Mock private R14RealtimeService realtime;
    private ContentContactCipher cipher;
    private R14Service service;

    @BeforeEach
    void setUp() {
        cipher = new ContentContactCipher("r14-test-contact-root-secret-at-least-32-characters");
        service = new R14Service(store, shared, r08, realtime, cipher,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void textMessageChecksMembershipRateAndWritesOneOutboxEvent() {
        arrangeSender();
        arrangePublisher();
        SendMessageRequest request = new SendMessageRequest(
                "client-message-1", "TEXT", Map.of("text", "你好"));
        when(store.insertMessage(eq(42L), eq(11L), eq("client-message-1"), eq("TEXT"), anyString(), eq(NOW)))
                .thenAnswer(invocation -> new R14Store.MessageRow(
                        100, 42, 11, "client-message-1", "TEXT", invocation.getArgument(4),
                        "SENT", NOW, null));

        var result = service.send(11, "42", request, KEY);

        assertEquals("100", result.id());
        assertEquals("你好", result.payload().get("text"));
        verify(store).advanceConversation(42, 11, 100, NOW);
        verify(store).outbox(11, "CHAT_MESSAGE", "chat.message.sent.v1", "100", "SENT", NOW);
        verify(realtime).messagePersisted(eq(11L), eq(7L), any());
        verify(shared).complete(eq(1L), anyString(), eq("r14.chat-message.v1"), anyString());
    }

    @Test
    void completedSendReplayReturnsFrozenSnapshotWithoutDuplicateMessageOutboxOrRealtime() {
        arrangeSender();
        arrangePublisher();
        SendMessageRequest request = new SendMessageRequest(
                "client-replay-1", "TEXT", Map.of("text", "网络超时后重试"));
        when(store.insertMessage(eq(42L), eq(11L), eq("client-replay-1"), eq("TEXT"), anyString(), eq(NOW)))
                .thenAnswer(invocation -> new R14Store.MessageRow(
                        104, 42, 11, "client-replay-1", "TEXT", invocation.getArgument(4),
                        "SENT", NOW, null));
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<String> requestHash = new AtomicReference<>();
        AtomicReference<String> responseType = new AtomicReference<>();
        AtomicReference<String> responsePayload = new AtomicReference<>();
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            String currentHash = invocation.getArgument(2);
            if (claims.getAndIncrement() == 0) requestHash.set(currentHash);
            boolean replay = claims.get() > 1;
            return new R08Store.IdempotencyClaim(12, requestHash.get(), replay ? "stored" : null,
                    replay ? responseType.get() : null, replay ? responsePayload.get() : null, replay);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            responseType.set(invocation.getArgument(2));
            responsePayload.set(invocation.getArgument(3));
            return null;
        }).when(shared).complete(eq(12L), anyString(), anyString(), anyString());

        var first = service.send(11, "42", request, KEY);
        var replay = service.send(11, "42", request, KEY);

        assertEquals(first, replay);
        verify(store, times(1)).insertMessage(
                eq(42L), eq(11L), eq("client-replay-1"), eq("TEXT"), anyString(), eq(NOW));
        verify(store, times(1)).outbox(11, "CHAT_MESSAGE", "chat.message.sent.v1", "104", "SENT", NOW);
        verify(realtime, times(1)).messagePersisted(eq(11L), eq(7L), any());
        verify(shared, times(1)).complete(eq(12L), anyString(), eq("r14.chat-message.v1"), anyString());
    }

    @Test
    void changedSendBodyWithSameIdempotencyKeyIsRejectedBeforeBusinessWrites() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        when(shared.blockedEitherWay(11, 7)).thenReturn(false);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new R08Store.IdempotencyClaim(13, "different-request-hash", "stored",
                        "r14.chat-message.v1", "encrypted", true));

        BusinessException error = assertThrows(BusinessException.class, () -> service.send(
                11, "42", new SendMessageRequest("client-conflict-1", "TEXT", Map.of("text", "已变化")), KEY));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        verify(store, never()).messageByClient(anyLong(), anyString());
        verify(store, never()).insertMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(realtime, never()).messagePersisted(anyLong(), anyLong(), any());
    }

    @Test
    void duplicateClientMessageWithAnotherKeyIsRejectedWithoutSecondBusinessWrite() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        when(shared.blockedEitherWay(11, 7)).thenReturn(false);
        arrangeClaim();
        when(store.messageByClient(11, "client-duplicate-1")).thenReturn(Optional.of(
                new R14Store.MessageRow(105, 42, 11, "client-duplicate-1", "TEXT",
                        "{\"text\":\"原消息\"}", "SENT", NOW, null)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.send(
                11, "42", new SendMessageRequest("client-duplicate-1", "TEXT", Map.of("text", "重复消息")), KEY));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        verify(store, never()).insertMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(shared, never()).complete(anyLong(), anyString(), anyString(), anyString());
        verify(realtime, never()).messagePersisted(anyLong(), anyLong(), any());
    }

    @Test
    void messageStorageTimeoutDoesNotCompleteOutboxOrRealtimeDelivery() {
        arrangeSender();
        when(store.insertMessage(eq(42L), eq(11L), eq("client-timeout-1"), eq("TEXT"), anyString(), eq(NOW)))
                .thenThrow(new IllegalStateException("chat storage timeout"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.send(
                11, "42", new SendMessageRequest("client-timeout-1", "TEXT", Map.of("text", "等待重试")), KEY));

        assertEquals("chat storage timeout", error.getMessage());
        verify(store, never()).advanceConversation(anyLong(), anyLong(), anyLong(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(shared, never()).complete(anyLong(), anyString(), anyString(), anyString());
        verify(realtime, never()).messagePersisted(anyLong(), anyLong(), any());
    }

    @Test
    void rateConfigurationTimeoutDoesNotStartMessageBusinessWrites() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        when(shared.blockedEitherWay(11, 7)).thenReturn(false);
        arrangeClaim();
        when(store.messageByClient(11, "client-config-timeout-1")).thenReturn(Optional.empty());
        when(shared.integerConfig("chat.message.per_minute_limit"))
                .thenThrow(new IllegalStateException("configuration provider timeout"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.send(
                11, "42", new SendMessageRequest(
                        "client-config-timeout-1", "TEXT", Map.of("text", "等待配置恢复")), KEY));

        assertEquals("configuration provider timeout", error.getMessage());
        verify(store, never()).messagesSentSince(anyLong(), anyLong(), any());
        verify(store, never()).insertMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(shared, never()).complete(anyLong(), anyString(), anyString(), anyString());
        verify(realtime, never()).messagePersisted(anyLong(), anyLong(), any());
    }

    @Test
    void mediaStorageTimeoutDoesNotInsertAttachmentMessageOrRealtimeDelivery() {
        arrangeSender();
        when(store.privateChatMedia(11, 88))
                .thenThrow(new IllegalStateException("media storage timeout"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.send(
                11, "42", new SendMessageRequest(
                        "client-media-timeout-1", "IMAGE", Map.of("mediaId", "88")), KEY));

        assertEquals("media storage timeout", error.getMessage());
        verify(store, never()).insertMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
        verify(store, never()).insertAttachment(anyLong(), anyLong(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(shared, never()).complete(anyLong(), anyString(), anyString(), anyString());
        verify(realtime, never()).messagePersisted(anyLong(), anyLong(), any());
    }

    @Test
    void contactCardIsEncryptedBeforeDatabaseAndDecryptedForAuthorizedResponse() {
        arrangeSender();
        arrangePublisher();
        String contact = "联".repeat(256);
        SendMessageRequest request = new SendMessageRequest("contact-1", "CONTACT_CARD", Map.of(
                "fields", List.of(Map.of("type", "OTHER", "label", "联系", "value", contact)),
                "note", "请联系"));
        when(store.insertMessage(eq(42L), eq(11L), eq("contact-1"), eq("CONTACT_CARD"), anyString(), eq(NOW)))
                .thenAnswer(invocation -> new R14Store.MessageRow(
                        101, 42, 11, "contact-1", "CONTACT_CARD", invocation.getArgument(4),
                        "SENT", NOW, null));

        var result = service.send(11, "42", request, KEY);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(store).insertMessage(eq(42L), eq(11L), eq("contact-1"), eq("CONTACT_CARD"), stored.capture(), eq(NOW));
        assertFalse(stored.getValue().contains(contact));
        assertTrue(stored.getValue().length() > 256);
        @SuppressWarnings("unchecked")
        Map<String, Object> field = (Map<String, Object>) ((List<?>) result.payload().get("fields")).getFirst();
        assertEquals(contact, field.get("value"));
        verify(store).outbox(11, "CHAT_MESSAGE", "chat.message.sent.v1", "101", "SENT", NOW);
    }

    @Test
    void contactCardRejectsThe257thPlaintextCharacterBeforeClaimOrWrite() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        when(shared.blockedEitherWay(11, 7)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class, () -> service.send(
                11, "42", new SendMessageRequest("contact-oversized", "CONTACT_CARD", Map.of(
                        "fields", List.of(Map.of("type", "OTHER", "value", "联".repeat(257))))), KEY));

        assertEquals("COMMON-400-VALIDATION", error.code());
        verify(shared, never()).claim(anyString(), anyString(), anyString(), any());
        verify(store, never()).insertMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void contactCardRejectsStoredEnvelopeWhoseDecryptedPlaintextExceedsContract() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, false)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        String envelope = cipher.encrypt(42, "CHAT_CONTACT:stored-1:0", "联".repeat(257));
        when(store.messages(any())).thenReturn(new R14Store.MessagePageRow(List.of(
                new R14Store.MessageRow(102, 42, 7, "stored-1", "CONTACT_CARD",
                        "{\"fields\":[{\"type\":\"OTHER\",\"value\":\"" + envelope + "\"}]}",
                        "SENT", NOW, null)), 1, false));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.messages(11, "42", 1, 20, null, null, null, null));

        assertEquals("COMMON-400-VALIDATION", error.code());
    }

    @Test
    void contactCardRejectsInvalidStoredEnvelope() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, false)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        when(store.messages(any())).thenReturn(storedContactPage("stored-invalid", "not-an-envelope"));

        assertThrows(ContentContactCipher.ContactIntegrityException.class,
                () -> service.messages(11, "42", 1, 20, null, null, null, null));
    }

    @Test
    void contactCardRejectsStoredEnvelopeBeyondCiphertextCapacity() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, false)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        String oversized = "hhy-contact-v1." + "A".repeat(16) + "." + "B".repeat(2020);
        when(store.messages(any())).thenReturn(storedContactPage("stored-oversized", oversized));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.messages(11, "42", 1, 20, null, null, null, null));

        assertEquals("COMMON-400-VALIDATION", error.code());
    }

    @Test
    void eitherDirectionBlockRejectsMessageBeforeClaimOrWrite() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        when(shared.blockedEitherWay(11, 7)).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.send(
                11, "42", new SendMessageRequest("client-2", "TEXT", Map.of("text", "x")), KEY));

        assertEquals("COMMON-403-FORBIDDEN", error.code());
        verify(shared, never()).claim(anyString(), anyString(), anyString(), any());
        verify(store, never()).insertMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void imageMessageRequiresOwnedReadyPrivateChatMedia() {
        arrangeSender();
        when(store.privateChatMedia(11, 88)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.send(
                11, "42", new SendMessageRequest("image-1", "IMAGE", Map.of("mediaId", "88")), KEY));

        assertEquals("COMMON-403-FORBIDDEN", error.code());
        verify(store, never()).insertMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void oversizedPrivateChatImageIsRejectedByActiveConfiguration() {
        arrangeSender();
        when(store.privateChatMedia(11, 88)).thenReturn(Optional.of(
                new R14Store.MediaRow(88, 11L * 1024L * 1024L)));
        when(shared.integerConfig("chat.image.max_mb")).thenReturn(10);

        BusinessException error = assertThrows(BusinessException.class, () -> service.send(
                11, "42", new SendMessageRequest("image-2", "IMAGE", Map.of("mediaId", "88")), KEY));

        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
        verify(store, never()).insertMessage(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void readDelegatesOrderedStateTransitionAndWritesAuditOutbox() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 5)));
        when(store.message(42, 100)).thenReturn(Optional.of(
                new R14Store.MessageRow(100, 42, 7, "peer-1", "TEXT", "{\"text\":\"ok\"}", "SENT", NOW, null)));
        arrangeClaim();

        var result = service.read(11, "42", new ReadRequest("100"), KEY);

        assertEquals("READ", result.status());
        assertEquals(5L, result.version());
        verify(store).markRead(42, 11, 100, NOW);
        verify(store).outbox(11, "CONVERSATION", "chat.conversation.read.v1", "42", "READ", NOW);
    }

    @Test
    void hidingConversationOnlyChangesCurrentMemberView() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 5)));
        when(store.hideConversation(42, 11, NOW)).thenReturn(true);
        arrangeClaim();

        var result = service.hide(11, "42", KEY);

        assertEquals("HIDDEN", result.status());
        verify(store).hideConversation(42, 11, NOW);
        verify(store).outbox(11, "CONVERSATION_VIEW", "chat.conversation.hidden.v1", "42", "HIDDEN", NOW);
    }

    @Test
    void reportChecksVersionAndEvidenceBeforeCreatingPendingFact() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 5)));
        when(store.reportEvidenceValid(42, 11, List.of(100L), List.of(88L))).thenReturn(true);
        when(store.report(11, 7, 42, "HARASSMENT", "骚扰消息", List.of(100L), List.of(88L), NOW)).thenReturn(300L);
        arrangeClaim();

        var result = service.report(11, "42",
                new ReportRequest("HARASSMENT", "骚扰消息", List.of("88"), List.of("100"), 5L), KEY);

        assertEquals("PENDING", result.status());
        assertEquals("300", result.resourceId());
        verify(store).outbox(11, "CHAT_REPORT", "chat.report.created.v1", "300", "PENDING", NOW);
    }

    @Test
    void reportRejectsUnknownAndCaseChangedReasonsBeforeEvidenceOrPersistence() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 5)));

        for (String reason : List.of("SPAM", "harassment")) {
            BusinessException error = assertThrows(BusinessException.class, () -> service.report(
                    11, "42", new ReportRequest(reason, "说明", List.of(), List.of(), 5L), KEY));
            assertEquals("COMMON-400-VALIDATION", error.code());
        }
        verify(store, never()).reportEvidenceValid(anyLong(), anyLong(), anyList(), anyList());
        verify(store, never()).report(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyList(), anyList(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void selfBlockIsRejectedAsBusinessRule() {
        when(shared.activeUser(11)).thenReturn(true);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.block(11, "11", new BlockRequest(null), KEY));
        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
        verify(store, never()).block(anyLong(), anyLong(), any(), any());
    }

    @Test
    void completedBlockReplayReturnsEncryptedSnapshotWithoutDuplicateWriteOrOutbox() {
        when(shared.activeUser(11)).thenReturn(true);
        when(shared.activeUser(7)).thenReturn(true);
        when(store.block(11, 7, "骚扰", NOW)).thenReturn(true);
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<String> responseType = new AtomicReference<>();
        AtomicReference<String> responsePayload = new AtomicReference<>();
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            String requestHash = invocation.getArgument(2);
            boolean replay = claims.getAndIncrement() > 0;
            return new R08Store.IdempotencyClaim(9, requestHash, replay ? "stored" : null,
                    replay ? responseType.get() : null, replay ? responsePayload.get() : null, replay);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            responseType.set(invocation.getArgument(2));
            responsePayload.set(invocation.getArgument(3));
            return null;
        }).when(shared).complete(eq(9L), anyString(), anyString(), anyString());

        var first = service.block(11, "7", new BlockRequest("骚扰"), KEY);
        var replay = service.block(11, "7", new BlockRequest("骚扰"), KEY);

        assertEquals(first, replay);
        verify(store, times(1)).block(11, 7, "骚扰", NOW);
        verify(store, times(1)).outbox(11, "USER_BLOCK", "chat.user.blocked.v1", "7", "BLOCKED", NOW);
        verify(shared, times(1)).complete(eq(9L), anyString(), eq("r14.chat-command.v1"), anyString());
    }

    private void arrangeSender() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.membership(42, 11, true)).thenReturn(Optional.of(new R14Store.MembershipRow(42, 7, 2)));
        when(shared.blockedEitherWay(11, 7)).thenReturn(false);
        when(store.messageByClient(eq(11L), org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(shared.integerConfig("chat.message.per_minute_limit")).thenReturn(60);
        when(store.messagesSentSince(42, 11, NOW.minusSeconds(60))).thenReturn(0L);
        arrangeClaim();
    }

    private void arrangePublisher() {
        when(shared.publisher(11)).thenReturn(Optional.of(
                new R08Store.PublisherRow(11, "发送者", null, null, false, null)));
    }

    private static R14Store.MessagePageRow storedContactPage(String clientMessageId, String value) {
        return new R14Store.MessagePageRow(List.of(new R14Store.MessageRow(
                103, 42, 7, clientMessageId, "CONTACT_CARD",
                "{\"fields\":[{\"type\":\"OTHER\",\"value\":\"" + value + "\"}]}",
                "SENT", NOW, null)), 1, false);
    }

    private void arrangeClaim() {
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(1, invocation.getArgument(2), null, null, null, false));
    }
}
