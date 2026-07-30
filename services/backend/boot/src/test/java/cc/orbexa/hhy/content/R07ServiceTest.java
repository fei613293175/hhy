package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.R07Contracts.ContactAccessRequest;
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
class R07ServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T17:45:00Z");
    private static final String KEY = "r07-contact-key-0001";

    @Mock
    private R07Store store;
    private ContentContactCipher cipher;
    private R07Service service;

    @BeforeEach
    void setUp() {
        cipher = new ContentContactCipher("r07-test-contact-root-secret-at-least-32-characters");
        service = new R07Service(store, cipher, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void searchOnlyMapsStoreRowsAndRecordsTheAuthenticatedUsersHistory() {
        when(store.search(any())).thenReturn(new R07Store.SearchRows(List.of(
                new R07Store.SearchRow(42, 7, "GROUP", "合伙群", "可信合作",
                        NOW.minusSeconds(60), "发布者", "avatar", "bio", true,
                        "PRO", true, 3.0)), 1, false));

        var result = service.search(11, "合作", "GROUP_CHAT", null, null,
                1, 20, null, "relevance:desc");

        assertEquals("42", result.items().getFirst().id());
        assertEquals("GROUP_CHAT", result.items().getFirst().contentType());
        assertEquals(List.of("VERIFIED", "PRO"), result.items().getFirst().badges());
        assertEquals("1", result.page().total());
        ArgumentCaptor<R07Store.SearchQuery> query = ArgumentCaptor.forClass(R07Store.SearchQuery.class);
        verify(store).search(query.capture());
        assertEquals(11, query.getValue().viewerId());
        assertEquals("GROUP", query.getValue().contentType());
        verify(store).recordSearch(11, "合作", NOW);
    }

    @Test
    void unsupportedSearchSortIsRejectedBeforeStorage() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.search(
                11, "合作", null, null, null, 1, 20, null, "title:asc"));

        assertEquals("COMMON-400-VALIDATION", error.code());
        verify(store, never()).search(any());
        verify(store, never()).recordSearch(anyLong(), anyString(), any());
    }

    @Test
    void searchCursorRequiresStableIdDescendingOrder() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.search(
                11, "合作", null, null, null, 1, 20, "42", "relevance:desc"));

        assertEquals("COMMON-400-VALIDATION", error.code());
        verify(store, never()).search(any());
        verify(store, never()).recordSearch(anyLong(), anyString(), any());
    }

    @Test
    void repeatedSearchesReturnStableResultsAndADeDuplicatedHistoryProjection() {
        var rows = new R07Store.SearchRows(List.of(
                new R07Store.SearchRow(42, 7, "PROJECT", "合作项目", "摘要",
                        NOW, "发布者", null, null, false, null, false, 2.0)), 1, false);
        when(store.search(any())).thenReturn(rows);
        when(store.history(eq(11L), any())).thenReturn(new R07Store.TermRows(List.of(
                new R07Store.TermRow(91, "合作", NOW)), 1, false));

        var first = service.search(11, "合作", "PROJECT", null, null,
                1, 20, null, "relevance:desc");
        var retry = service.search(11, "合作", "PROJECT", null, null,
                1, 20, null, "relevance:desc");
        var history = service.history(11, 1, 20, null, null, "createdAt:desc");

        assertEquals(first, retry);
        assertEquals(1, history.items().size());
        assertEquals("合作", history.items().getFirst().keyword());
        verify(store, times(2)).recordSearch(11, "合作", NOW);
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void searchStorageFailureDoesNotRecordHistoryOrReturnPartialResults() {
        when(store.search(any())).thenThrow(new IllegalStateException("database timeout"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.search(
                11, "合作", null, null, null, 1, 20, null, "relevance:desc"));

        assertEquals("database timeout", error.getMessage());
        verify(store, never()).recordSearch(anyLong(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void publisherReturnsOnlyPublicProfileFieldsWithoutReadingContactData() {
        when(store.publisher(7, 11, NOW)).thenReturn(Optional.of(new R07Store.PublisherRow(
                7, "公开发布者", "avatar", "公开简介", true, "PRO", true)));

        var result = service.publisher(11, "7");

        assertEquals("7", result.userId());
        assertEquals("公开发布者", result.nickname());
        assertEquals("公开简介", result.bio());
        assertTrue(result.verified());
        assertEquals("PRO", result.memberBadge());
        assertTrue(result.followed());
        verify(store, never()).contact(anyLong(), anyString());
        verify(store, never()).contactAudit(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void publisherProjectionPreservesTwoHundredFiftyFiveSupplementaryCharacters() {
        String value = "😀".repeat(255);
        when(store.publisher(7, 11, NOW)).thenReturn(Optional.of(new R07Store.PublisherRow(
                7, value, null, value, false, null, false)));

        var result = service.publisher(11, "7");

        assertEquals(value, result.nickname());
        assertEquals(value, result.bio());
        assertEquals(255, result.nickname().codePointCount(0, result.nickname().length()));
        assertEquals(255, result.bio().codePointCount(0, result.bio().length()));
    }

    @Test
    void repeatedPublisherReadsAreStableAndHaveNoWriteSideEffects() {
        var row = new R07Store.PublisherRow(7, "公开发布者", null, null,
                false, null, false);
        when(store.publisher(7, 11, NOW)).thenReturn(Optional.of(row));

        var first = service.publisher(11, "7");
        var retry = service.publisher(11, "7");

        assertEquals(first, retry);
        verify(store, times(2)).publisher(7, 11, NOW);
        verify(store, never()).recordSearch(anyLong(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void invalidOrUnavailablePublisherIsRejectedWithoutPrivateLookup() {
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.publisher(11, "0"));
        assertEquals("COMMON-400-VALIDATION", invalid.code());
        verify(store, never()).publisher(anyLong(), anyLong(), any());

        when(store.publisher(7, 11, NOW)).thenReturn(Optional.empty());
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.publisher(11, "7"));
        assertEquals("COMMON-404-NOT_FOUND", missing.code());
        assertFalse(missing.getMessage().contains("7"));
        verify(store, never()).contact(anyLong(), anyString());
    }

    @Test
    void contactAccessDecryptsOnceAndReplaysEncryptedSnapshotWithoutDuplicateOutbox() {
        String plain = "contact@example.com";
        String encryptedContact = cipher.encrypt(42, "EMAIL", plain);
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<String> snapshot = new AtomicReference<>();
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            boolean replay = claims.getAndIncrement() > 0;
            return new R07Store.IdempotencyClaim(71, invocation.getArgument(2),
                    replay ? "r07.contact-access.v1:ok" : null,
                    replay ? "r07.contact-access.v1" : null,
                    replay ? snapshot.get() : null, replay);
        });
        when(store.contact(42, "EMAIL"))
                .thenReturn(Optional.of(new R07Store.ContactRow(42, "EMAIL", encryptedContact)));
        org.mockito.Mockito.doAnswer(invocation -> {
            snapshot.set(invocation.getArgument(3));
            return null;
        }).when(store).complete(eq(71L), anyString(), eq("r07.contact-access.v1"), anyString());

        ContactAccessRequest request = new ContactAccessRequest(Map.of("action", "COPY"));
        var first = service.contact(11, "42", "email", request, KEY);
        var replay = service.contact(11, "42", "EMAIL", request, KEY);

        assertEquals(plain, first.value());
        assertEquals(first, replay);
        assertFalse(snapshot.get().contains(plain));
        verify(store).contactAudit(11, 42, "EMAIL", "COPY", NOW);
        verify(store).contactAudit(11, 42, "EMAIL", "REPLAY", NOW);
        verify(store, times(1)).outbox(
                11, "CONTENT", "content.contact.accessed.v1", "42", "COPY", NOW);
        verify(store, times(1)).complete(eq(71L), anyString(),
                eq("r07.contact-access.v1"), anyString());
    }

    @Test
    void joinPasswordAccessDecryptsAuditsAndReplaysWithoutLeakingSecret() {
        String plain = "合伙云入群口令-2026";
        String encryptedContact = cipher.encrypt(42, "JOIN_PASSWORD", plain);
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<String> snapshot = new AtomicReference<>();
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            boolean replay = claims.getAndIncrement() > 0;
            return new R07Store.IdempotencyClaim(81, invocation.getArgument(2),
                    replay ? "r07.contact-access.v1:ok" : null,
                    replay ? "r07.contact-access.v1" : null,
                    replay ? snapshot.get() : null, replay);
        });
        when(store.contact(42, "JOIN_PASSWORD"))
                .thenReturn(Optional.of(new R07Store.ContactRow(42, "JOIN_PASSWORD", encryptedContact)));
        org.mockito.Mockito.doAnswer(invocation -> {
            snapshot.set(invocation.getArgument(3));
            return null;
        }).when(store).complete(eq(81L), anyString(), eq("r07.contact-access.v1"), anyString());

        var request = new ContactAccessRequest(Map.of("action", "COPY"));
        var first = service.contact(11, "42", "join_password", request, KEY);
        var replay = service.contact(11, "42", "JOIN_PASSWORD", request, KEY);

        assertEquals("JOIN_PASSWORD", first.channel());
        assertEquals(plain, first.value());
        assertEquals(first, replay);
        assertFalse(snapshot.get().contains(plain));
        verify(store).contactAudit(11, 42, "JOIN_PASSWORD", "COPY", NOW);
        verify(store).contactAudit(11, 42, "JOIN_PASSWORD", "REPLAY", NOW);
        verify(store, times(1)).outbox(
                11, "CONTENT", "content.contact.accessed.v1", "42", "COPY", NOW);
        verify(store, times(1)).complete(eq(81L), anyString(),
                eq("r07.contact-access.v1"), anyString());
    }

    @Test
    void unreadableLegacyContactIsRejectedAndAuditedWithoutLeakingItsStoredValue() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R07Store.IdempotencyClaim(72, invocation.getArgument(2),
                        null, null, null, false));
        when(store.contact(42, "EMAIL"))
                .thenReturn(Optional.of(new R07Store.ContactRow(42, "EMAIL", "12345")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.contact(
                11, "42", "EMAIL", new ContactAccessRequest(Map.of()), KEY));

        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
        verify(store).contactRejected(11, 42, "EMAIL", "REJECTED_UNAVAILABLE", NOW);
        verify(store).abandon(72);
        verify(store, never()).outbox(
                anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(store, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void pendingDuplicateContactRequestReturnsRetryableConflictWithoutSideEffects() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R07Store.IdempotencyClaim(74, invocation.getArgument(2),
                        null, null, null, true));

        BusinessException error = assertThrows(BusinessException.class, () -> service.contact(
                11, "42", "EMAIL", new ContactAccessRequest(Map.of("action", "VIEW")), KEY));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
        assertTrue(error.retryable());
        verify(store, never()).contact(anyLong(), anyString());
        verify(store, never()).contactAudit(anyLong(), anyLong(), anyString(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sameContactKeyWithDifferentIntentIsRejectedBeforeSensitiveLookup() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new R07Store.IdempotencyClaim(75, "f".repeat(64),
                        "r07.contact-access.v1:ok", "r07.contact-access.v1", "ciphertext", true));

        BusinessException error = assertThrows(BusinessException.class, () -> service.contact(
                11, "42", "EMAIL", new ContactAccessRequest(Map.of("action", "COPY")), KEY));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        verify(store, never()).contact(anyLong(), anyString());
        verify(store, never()).contactAudit(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void contactStorageTimeoutAbandonsClaimBeforeAuditMessageOrSnapshot() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R07Store.IdempotencyClaim(76, invocation.getArgument(2),
                        null, null, null, false));
        when(store.contact(42, "EMAIL")).thenThrow(new IllegalStateException("contact store timeout"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.contact(
                11, "42", "EMAIL", new ContactAccessRequest(Map.of("action", "VIEW")), KEY));

        assertEquals("contact store timeout", error.getMessage());
        verify(store).abandon(76);
        verify(store, never()).contactAudit(anyLong(), anyLong(), anyString(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(store, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void unsupportedContactChannelIsRejectedBeforeIdempotencyOrStorage() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.contact(
                11, "42", "FAX", new ContactAccessRequest(Map.of()), KEY));

        assertEquals("COMMON-400-VALIDATION", error.code());
        verify(store, never()).claim(anyString(), anyString(), anyString(), any());
        verify(store, never()).contact(anyLong(), anyString());
    }

    @Test
    void clearHistoryIsScopedToTheAuthenticatedUserAndProducesOneMessage() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R07Store.IdempotencyClaim(73, invocation.getArgument(2),
                        null, null, null, false));
        when(store.clearHistory(11)).thenReturn(3);

        var result = service.clearHistory(11, KEY);

        assertEquals("CLEARED", result.status());
        assertEquals(3, result.version());
        verify(store).clearHistory(11);
        verify(store).outbox(
                11, "SEARCH_HISTORY", "search.history.cleared.v1", "11", "CLEARED", NOW);
        verify(store).complete(eq(73L), anyString(), eq("r07.search-history-clear.v1"), anyString());
    }

    @Test
    void clearHistoryRetryReplaysFirstResponseWithoutDuplicateDeleteOrMessage() {
        var snapshot = new AtomicReference<String>();
        var claims = new AtomicInteger();
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            boolean replay = claims.getAndIncrement() > 0;
            return new R07Store.IdempotencyClaim(77, invocation.getArgument(2),
                    replay ? "r07.search-history-clear.v1:ok" : null,
                    replay ? "r07.search-history-clear.v1" : null,
                    replay ? snapshot.get() : null, replay);
        });
        when(store.clearHistory(11)).thenReturn(4);
        org.mockito.Mockito.doAnswer(invocation -> {
            snapshot.set(invocation.getArgument(3));
            return null;
        }).when(store).complete(eq(77L), anyString(), eq("r07.search-history-clear.v1"), anyString());

        var first = service.clearHistory(11, KEY);
        var retry = service.clearHistory(11, KEY);

        assertEquals(first, retry);
        verify(store, times(1)).clearHistory(11);
        verify(store, times(1)).outbox(
                11, "SEARCH_HISTORY", "search.history.cleared.v1", "11", "CLEARED", NOW);
        verify(store, times(1)).complete(eq(77L), anyString(),
                eq("r07.search-history-clear.v1"), anyString());
    }
}
