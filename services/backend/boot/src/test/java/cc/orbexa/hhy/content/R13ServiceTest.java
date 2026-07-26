package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.R13Contracts.InvalidFeedbackRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
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
class R13ServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-26T12:45:00Z");
    private static final String KEY = "r13-idempotency-key-0001";
    @Mock private R13Store store;
    @Mock private R08Store shared;
    @Mock private ContentService content;
    private R13Service service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        service = new R13Service(store, shared, content,
                new ContentContactCipher("r13-test-root-secret-material-000000", new SecureRandom()),
                mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void favoritesUsesFrozenFiltersActivityCursorAndBatchMaterialization() {
        when(shared.activeUser(11)).thenReturn(true);
        R13Store.ActivityRow row = new R13Store.ActivityRow(91, 71, NOW.minusSeconds(5));
        when(store.favorites(any())).thenReturn(new R13Store.ActivityPage(List.of(row), 3, true));
        when(content.resourcesById(List.of(71L))).thenReturn(List.of(resource("71")));

        var result = service.favorites(11, 1, 1, null, "online", " 合作 ", "createdAt:desc");

        assertEquals("71", result.items().getFirst().id());
        assertEquals("3", result.page().total());
        assertEquals((NOW.minusSeconds(5).toEpochMilli()) + ":91", result.page().nextCursor());
        ArgumentCaptor<R13Store.ActivityQuery> query = ArgumentCaptor.forClass(R13Store.ActivityQuery.class);
        verify(store).favorites(query.capture());
        assertEquals("ONLINE", query.getValue().status());
        assertEquals("合作", query.getValue().keyword());
        assertEquals(R13Store.SortDirection.DESC, query.getValue().direction());
    }

    @Test
    void historyParsesAscendingCursorAndRejectsUnsupportedSorts() {
        when(shared.activeUser(11)).thenReturn(true);
        when(store.history(any())).thenReturn(new R13Store.ActivityPage(List.of(), 0, false));

        var result = service.history(11, 1, 20, NOW.toEpochMilli() + ":41",
                null, null, "createdAt:asc");

        assertNull(result.page().nextCursor());
        ArgumentCaptor<R13Store.ActivityQuery> query = ArgumentCaptor.forClass(R13Store.ActivityQuery.class);
        verify(store).history(query.capture());
        assertEquals(41, query.getValue().cursor().activityId());
        assertEquals(R13Store.SortDirection.ASC, query.getValue().direction());

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.history(11, 1, 20, null, null, null, "title:desc"));
        assertEquals("COMMON-400-VALIDATION", error.code());
    }

    @Test
    void unfavoriteReplaysEncryptedSnapshotWithoutDoubleDeleteOrOutbox() {
        when(shared.activeUser(11)).thenReturn(true);
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<String> hash = new AtomicReference<>();
        AtomicReference<String> type = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            String requestHash = invocation.getArgument(2);
            hash.set(requestHash);
            boolean replay = claims.getAndIncrement() > 0;
            return new R08Store.IdempotencyClaim(31, requestHash,
                    replay ? "stored" : null, replay ? type.get() : null,
                    replay ? payload.get() : null, replay);
        });
        doAnswer(invocation -> {
            type.set(invocation.getArgument(2));
            payload.set(invocation.getArgument(3));
            return null;
        }).when(shared).complete(eq(31L), anyString(), anyString(), anyString());
        when(shared.lockContent(71)).thenReturn(Optional.of(
                new R08Store.ContentRow(71, 7, "PROJECT", "ONLINE", 4)));
        when(store.unfavorite(11, 71, NOW)).thenReturn(true);

        var first = service.unfavorite(11, "71", KEY);
        var replay = service.unfavorite(11, "71", KEY);

        assertEquals(first, replay);
        assertEquals("UNFAVORITED", replay.status());
        verify(store, times(1)).unfavorite(11, 71, NOW);
        verify(shared, times(1)).outbox(11, "CONTENT", "content.unfavorited.v1", "71", "UNFAVORITED", NOW);
        verify(shared, times(1)).lockContent(71);
        assertEquals(64, hash.get().length());
    }

    @Test
    void invalidFeedbackPersistsPendingAuditAndOutbox() {
        when(shared.activeUser(11)).thenReturn(true);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(32, invocation.getArgument(2), null, null, null, false));
        when(shared.lockContent(71)).thenReturn(Optional.of(
                new R08Store.ContentRow(71, 7, "GROUP", "ONLINE", 4)));
        when(store.invalidFeedback(11, 71, "QR_EXPIRED", "二维码已失效", NOW)).thenReturn(51L);

        var result = service.invalidFeedback(11, "71",
                new InvalidFeedbackRequest("qr_expired", " 二维码已失效 "), KEY);

        assertEquals("51", result.resourceId());
        assertEquals("PENDING", result.status());
        verify(shared).outbox(11, "CONTENT_REPORT", "content.invalid-feedback.created.v1",
                "51", "PENDING", NOW);
        verify(shared).complete(eq(32L), anyString(), eq("r13.invalid-feedback-command.v1"), anyString());
    }

    @Test
    void invalidFeedbackReplayReturnsFrozenSnapshotWithoutDuplicateReportOrOutbox() {
        when(shared.activeUser(11)).thenReturn(true);
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<String> responseType = new AtomicReference<>();
        AtomicReference<String> responsePayload = new AtomicReference<>();
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            String requestHash = invocation.getArgument(2);
            boolean replay = claims.getAndIncrement() > 0;
            return new R08Store.IdempotencyClaim(34, requestHash,
                    replay ? "stored" : null, replay ? responseType.get() : null,
                    replay ? responsePayload.get() : null, replay);
        });
        doAnswer(invocation -> {
            responseType.set(invocation.getArgument(2));
            responsePayload.set(invocation.getArgument(3));
            return null;
        }).when(shared).complete(eq(34L), anyString(), anyString(), anyString());
        when(shared.lockContent(71)).thenReturn(Optional.of(
                new R08Store.ContentRow(71, 7, "GROUP", "ONLINE", 4)));
        when(store.invalidFeedback(11, 71, "QR_CODE", "二维码已失效", NOW)).thenReturn(52L);
        var request = new InvalidFeedbackRequest("qr_code", " 二维码已失效 ");

        var first = service.invalidFeedback(11, "71", request, KEY);
        var replay = service.invalidFeedback(11, "71", request, KEY);

        assertEquals(first, replay);
        assertEquals("PENDING", replay.status());
        verify(store, times(1)).invalidFeedback(11, 71, "QR_CODE", "二维码已失效", NOW);
        verify(shared, times(1)).lockContent(71);
        verify(shared, times(1)).outbox(11, "CONTENT_REPORT", "content.invalid-feedback.created.v1",
                "52", "PENDING", NOW);
        verify(shared, times(1)).complete(eq(34L), anyString(),
                eq("r13.invalid-feedback-command.v1"), anyString());
    }

    @Test
    void pendingFeedbackReplayReturnsVersionConflictBeforeAnyWrite() {
        when(shared.activeUser(11)).thenReturn(true);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(35, invocation.getArgument(2),
                        null, null, null, true));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.invalidFeedback(11, "71", new InvalidFeedbackRequest("LINK", null), KEY));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
        verify(shared, never()).lockContent(anyLong());
        verify(store, never()).invalidFeedback(anyLong(), anyLong(), anyString(), any(), any());
        verify(shared, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void invalidFeedbackStoreTimeoutLeavesNoOutboxOrCompletedSnapshot() {
        when(shared.activeUser(11)).thenReturn(true);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(36, invocation.getArgument(2), null, null, null, false));
        when(shared.lockContent(71)).thenReturn(Optional.of(
                new R08Store.ContentRow(71, 7, "GROUP", "ONLINE", 4)));
        when(store.invalidFeedback(11, 71, "PHONE", null, NOW))
                .thenThrow(new IllegalStateException("activity store timeout"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.invalidFeedback(11, "71", new InvalidFeedbackRequest("phone", null), KEY));

        assertEquals("activity store timeout", error.getMessage());
        verify(shared, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(shared, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void changedFeedbackBodyWithSameKeyIsRejectedBeforeInsert() {
        when(shared.activeUser(11)).thenReturn(true);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new R08Store.IdempotencyClaim(33, "different-hash", "stored",
                        "r13.invalid-feedback-command.v1", "ciphertext", true));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.invalidFeedback(11, "71",
                        new InvalidFeedbackRequest("LINK_EXPIRED", null), KEY));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        verify(store, never()).invalidFeedback(anyLong(), anyLong(), anyString(), any(), any());
        verify(shared, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void inactiveUserCannotReadOrWriteActivityData() {
        when(shared.activeUser(11)).thenReturn(false);

        BusinessException read = assertThrows(BusinessException.class, () ->
                service.favorites(11, 1, 20, null, null, null, null));
        BusinessException write = assertThrows(BusinessException.class, () ->
                service.unfavorite(11, "71", KEY));

        assertEquals("COMMON-403-FORBIDDEN", read.code());
        assertEquals("COMMON-403-FORBIDDEN", write.code());
        verify(store, never()).favorites(any());
        verify(shared, never()).claim(anyString(), anyString(), anyString(), any());
    }

    private static ContentResource resource(String id) {
        return new ContentResource(id, "PROJECT", "项目", "摘要", "说明", "COOP", "CN-11",
                List.of(), null, List.of(), "ONLINE", "APPROVED", null,
                NOW, NOW, 4, Map.of());
    }
}
