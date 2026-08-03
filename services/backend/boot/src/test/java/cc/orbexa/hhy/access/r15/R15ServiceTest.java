package cc.orbexa.hhy.access.r15;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.r15.R15Contracts.DecideRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportCloseRequest;
import cc.orbexa.hhy.access.r15.R15Contracts.SupportMessageRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class R15ServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T04:00:00Z");
    private static final String KEY = "r15-test-key-0001";

    @Mock private R15Store store;
    private R15Service service;

    @BeforeEach
    void setUp() {
        service = new R15Service(store, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publicAgreementReturnsFrozenPublicPageShape() {
        when(store.publicAgreement("IDENTITY_VERIFICATION")).thenReturn(Optional.of(
                new R15Store.PublicPageRow(
                        31, "H5-010", "IDENTITY_VERIFICATION", null, "协议正文", 2026072001L)));

        var result = service.publicAgreement("IDENTITY_VERIFICATION");

        assertEquals("H5-010", result.code());
        assertEquals(2026072001L, result.version());
        assertEquals("RICH_TEXT", result.content().getFirst().blockType());
        assertEquals("协议正文", result.content().getFirst().body());
    }

    @Test
    void publicHelpArticleReturnsBodyInsteadOfTicketProjection() {
        when(store.publicHelpArticle(42)).thenReturn(Optional.of(
                new R15Store.PublicPageRow(42, "H5-011", "安装帮助", "HELP", "帮助正文", 0)));

        var result = service.publicHelpArticle("42");

        assertEquals("H5-011", result.code());
        assertEquals("安装帮助", result.title());
        assertEquals("帮助正文", result.content().getFirst().body());
    }

    @Test
    void userReplyPersistsOwnedAttachmentsAndPropagatesRequestId() {
        arrangeClaim();
        R15Store.SupportTicketRow ticket = ticket(42, "OPEN", 3);
        when(store.supportTicket(7L, 42)).thenReturn(Optional.of(ticket));
        when(store.attachmentsAvailable(List.of(51L, 52L), 7L)).thenReturn(true);

        var result = service.addUserMessage(7, "42",
                new SupportMessageRequest("补充截图", List.of("51", "52")),
                KEY, "request-r15-user-reply");

        assertEquals("42", result.id());
        verify(store).appendSupportMessage(
                42, "USER", 7, "补充截图", List.of(51L, 52L), 7L, NOW);
        verify(store).outbox(eq("SUPPORT_TICKET"), eq(42L),
                eq("support.ticket.message-added.v1"), eq("request-r15-user-reply"), anyString());
    }

    @Test
    void unavailableUserAttachmentIsRejectedBeforeMessageWrite() {
        arrangeClaim();
        when(store.supportTicket(7L, 42)).thenReturn(Optional.of(ticket(42, "OPEN", 3)));
        when(store.attachmentsAvailable(List.of(51L), 7L)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class, () -> service.addUserMessage(
                7, "42", new SupportMessageRequest("补充截图", List.of("51")),
                KEY, "request-r15-user-reply"));

        assertEquals("COMMON-403-FORBIDDEN", error.code());
        verify(store, never()).appendSupportMessage(
                anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void invalidChatDecisionIsRejectedBeforeIdempotencyClaim() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.decideChatReport(
                9, "81", new DecideRequest("approve", "大小写不符合冻结枚举", 2L, List.of()),
                KEY, "request-r15-chat", "127.0.0.1"));

        assertEquals("COMMON-400-VALIDATION", error.code());
        verify(store, never()).claim(anyString(), anyString(), anyString(), any());
    }

    @Test
    void decidedChatReportCannotBeChangedAgain() {
        arrangeClaim();
        when(store.lockChatReport(81)).thenReturn(Optional.of(
                new R15Store.ConversationRow(81, 42, "APPROVED", NOW, 2)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.decideChatReport(
                9, "81", new DecideRequest("REJECT", "尝试重复改判", 2L, List.of()),
                KEY, "request-r15-chat", "127.0.0.1"));

        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
        verify(store, never()).decideChatReport(anyLong(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void closedTicketCannotReceiveAnotherUserMessage() {
        arrangeClaim();
        when(store.supportTicket(7L, 42)).thenReturn(Optional.of(ticket(42, "CLOSED", 5)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.addUserMessage(
                7, "42", new SupportMessageRequest("再次回复", List.of()),
                KEY, "request-r15-user-reply"));

        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
        verify(store, never()).appendSupportMessage(
                anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void closeWithoutExpectedVersionUsesLockedCurrentVersion() {
        arrangeClaim();
        when(store.lockSupportTicket(42)).thenReturn(Optional.of(ticket(42, "RESOLVED", 4)));
        when(store.closeTicket(42, "已解决", 4, NOW)).thenReturn(true);
        when(store.supportTicket(null, 42)).thenReturn(Optional.of(ticket(42, "CLOSED", 5)));

        var result = service.closeTicket(9, "42",
                new SupportCloseRequest("已解决", null, Map.of()), KEY,
                "request-r15-close", "127.0.0.1");

        assertEquals("CLOSED", result.status());
        assertEquals(5, result.version());
        verify(store).closeTicket(42, "已解决", 4, NOW);
    }

    private void arrangeClaim() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R15Store.IdempotencyClaim(1, invocation.getArgument(2), null, false));
    }

    private static R15Store.SupportTicketRow ticket(long id, String status, long version) {
        return new R15Store.SupportTicketRow(
                id, "HHY-" + id, "QUESTION", "问题", status, null,
                NOW, NOW, NOW, version);
    }
}
