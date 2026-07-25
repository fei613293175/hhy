package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.ContentContracts.CommandResult;
import cc.orbexa.hhy.content.ContentContracts.ContentPage;
import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.ContentContracts.PublisherSummary;
import cc.orbexa.hhy.content.ContentContracts.StatusRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class R12PublishingServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-25T03:00:00Z");
    private static final String KEY = "r12-publishing-key-0001";
    @Mock private R12PublishingStore store;
    @Mock private R08Store shared;
    @Mock private ContentService content;
    private ContentContactCipher cipher;
    private R12PublishingService service;

    @BeforeEach
    void setUp() {
        cipher = new ContentContactCipher("r12-publishing-test-root-secret-at-least-32-characters");
        service = new R12PublishingService(store, shared, content, cipher,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submitPersistsVersionedSnapshotHistoryAndOutbox() {
        newClaim(21);
        when(store.lockOwned(71)).thenReturn(Optional.of(row(71, "DRAFT", 3)));
        when(shared.identityVerified(11)).thenReturn(true);
        when(shared.countOwnedInStatus(11, "PENDING_REVIEW")).thenReturn(0L);
        when(shared.integerConfig("content.limit.normal.pending")).thenReturn(3);
        when(shared.integerConfig("content.limit.normal.daily_submissions")).thenReturn(10);
        when(store.submissionsSince(eq(11L), any())).thenReturn(0L);
        when(store.transitionOwned(71, 11, 3, "DRAFT", "PENDING_REVIEW", "PENDING", NOW))
                .thenReturn(true);

        CommandResult result = service.submit(11, "71", new StatusRequest(3L, "资料完整"), KEY);

        assertEquals("PENDING_REVIEW", result.status());
        assertEquals(4, result.version());
        verify(store).submissionSnapshot(71, 11, NOW);
        verify(store).statusLog(71, "DRAFT", "PENDING_REVIEW", "资料完整", 11, 4);
        verify(store).outbox(eq(11L), eq("content.submitted.v1"), eq(71L), eq("DRAFT"),
                eq("PENDING_REVIEW"), eq(4L), eq("资料完整"), any(), eq(NOW));
        verify(shared).complete(eq(21L), anyString(), anyString(), anyString());
    }

    @Test
    void submitRequiresVerifiedOwnerBeforeBusinessWrites() {
        newClaim(22);
        when(store.lockOwned(71)).thenReturn(Optional.of(row(71, "DRAFT", 3)));
        when(shared.identityVerified(11)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(11, "71", new StatusRequest(3L, null), KEY));

        assertEquals("IDENTITY-422-NOT_VERIFIED", error.code());
        verify(store, never()).transitionOwned(anyLong(), anyLong(), anyLong(),
                anyString(), anyString(), any(), any());
        verify(shared, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void offlineContentCannotReturnOnlineAfterApprovedSnapshotChanged() {
        newClaim(23);
        when(store.lockOwned(71)).thenReturn(Optional.of(row(71, "OFFLINE_BY_OWNER", 8)));
        when(shared.identityVerified(11)).thenReturn(true);
        when(store.approvedSnapshotIsCurrent(71)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.online(11, "71", new StatusRequest(8L, null), KEY));

        assertEquals("CONTENT-409-STATUS_TRANSITION", error.code());
        verify(store, never()).transitionOwned(anyLong(), anyLong(), anyLong(),
                anyString(), anyString(), any(), any());
    }

    @Test
    void copyCreatesIndependentDraftAndReencryptsContactForTargetAad() {
        newClaim(24);
        when(shared.identityVerified(11)).thenReturn(true);
        when(store.lockOwned(71)).thenReturn(Optional.of(row(71, "ONLINE", 7)));
        when(shared.countOwnedInStatus(11, "DRAFT")).thenReturn(0L);
        when(shared.integerConfig("content.limit.normal.drafts")).thenReturn(10);
        String sourceEnvelope = cipher.encrypt(71, "WECHAT", "owner-contact");
        when(store.activeContacts(71)).thenReturn(List.of(
                new R12PublishingStore.ContactEnvelope("WECHAT", sourceEnvelope, "ow***ct", 0)));
        when(store.copySkeleton(71, 11, NOW)).thenReturn(72L);

        CommandResult result = service.copy(11, "71", new StatusRequest(7L, "继续发布"), KEY);

        assertEquals("72", result.resourceId());
        assertEquals("DRAFT", result.status());
        assertEquals(0, result.version());
        ArgumentCaptor<List<R12PublishingStore.ContactEnvelope>> contacts = ArgumentCaptor.forClass(List.class);
        verify(store).copyContacts(eq(72L), contacts.capture(), eq(NOW));
        String copiedEnvelope = contacts.getValue().getFirst().valueCipher();
        assertFalse(sourceEnvelope.equals(copiedEnvelope));
        assertEquals("owner-contact", cipher.decrypt(72, "WECHAT", copiedEnvelope));
        verify(store).outbox(eq(11L), eq("content.copied.v1"), eq(72L), isNull(), eq("DRAFT"),
                eq(0L), eq("继续发布"), any(), eq(NOW));
        verify(shared).complete(eq(24L), anyString(), anyString(), anyString());
    }

    @Test
    void reusedKeyWithDifferentRequestIsRejectedBeforeLockingContent() {
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new R08Store.IdempotencyClaim(25, "f".repeat(64), null, null, null, true));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.offline(11, "71", new StatusRequest(7L, null), KEY));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        verify(store, never()).lockOwned(anyLong());
    }

    @Test
    void ownerListUsesPublisherScopeAndMapsOnlyReturnedIds() {
        when(store.ownerPage(any())).thenReturn(new R12PublishingStore.PageIds(
                List.of(new R12PublishingStore.PageItem(71L, NOW)), 1, false));
        when(content.detail("71")).thenReturn(resource("71", "ONLINE", 7));

        ContentPage result = service.mine(11, 1, 20, null, "ONLINE", null,
                "createdAt:desc", "PROJECT", null, null);

        assertEquals(List.of("71"), result.items().stream().map(ContentResource::id).toList());
        assertEquals("1", result.page().total());
    }

    private void newClaim(long id) {
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(
                        id, invocation.getArgument(2), null, null, null, false));
    }

    private static R12PublishingStore.OwnedContent row(long id, String status, long version) {
        return new R12PublishingStore.OwnedContent(id, 11, "PROJECT", status, version);
    }

    private static ContentResource resource(String id, String status, long version) {
        return new ContentResource(id, "PROJECT", "项目", "摘要", "说明", "COOP", "CN-11",
                List.of(), new PublisherSummary("11", "发布者", null, null, true, null, null),
                List.of(), status, null, null, NOW, NOW, version, Map.of());
    }
}
