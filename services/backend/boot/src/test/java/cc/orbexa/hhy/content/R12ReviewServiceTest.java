package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.R12ReviewContracts.ReviewAssignRequest;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewActorContext;
import cc.orbexa.hhy.content.R12ReviewContracts.ReviewDecisionRequest;
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
class R12ReviewServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-25T03:00:00Z");
    private static final String KEY = "r12-review-key-0001";
    private static final ReviewActorContext ACTOR = new ReviewActorContext(
            9, 19, "reviewer-9", "review.decide", "request-2", "127.0.0.1");
    private static final R12ReviewStore.SnapshotRow SNAPSHOT =
            new R12ReviewStore.SnapshotRow(91, "3");

    @Mock private R12ReviewStore store;
    private R12ReviewService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().findAndRegisterModules();
        service = new R12ReviewService(store,
                new ContentContactCipher("r12-review-test-root-secret-at-least-32-characters"),
                mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void queueMapsFrozenResourceAndCursorPage() {
        when(store.reviews(any())).thenReturn(new R12ReviewStore.ReviewPageRows(List.of(
                row("PENDING_REVIEW", 3, null, null, null)), 21, true));

        var page = service.queue(1, 1, null, "PENDING_REVIEW", "项目", "createdAt:desc");

        assertEquals("CONTENT", page.items().getFirst().subjectType());
        assertEquals("71", page.items().getFirst().subjectId());
        assertNotEquals("71", page.page().nextCursor());
        assertEquals("21", page.page().total());
        assertEquals("true", page.page().hasMore());

        service.queue(2, 1, page.page().nextCursor(), "PENDING_REVIEW", "项目", "createdAt:desc");
        ArgumentCaptor<R12ReviewStore.PageQuery> queries =
                ArgumentCaptor.forClass(R12ReviewStore.PageQuery.class);
        verify(store, times(2)).reviews(queries.capture());
        assertEquals(71, queries.getAllValues().get(1).cursor().id());
        assertEquals(NOW.minusSeconds(3600), queries.getAllValues().get(1).cursor().sortValue());
    }

    @Test
    void defaultPriorityQueueUsesPagePaginationAndNeverEmitsCursor() {
        when(store.reviews(any())).thenReturn(new R12ReviewStore.ReviewPageRows(List.of(
                row("PENDING_REVIEW", 3, null, null, null)), 21, true));

        var page = service.queue(2, 20, null, null, null,
                "priority:desc,createdAt:asc");

        assertEquals(2, page.page().page());
        assertEquals("true", page.page().hasMore());
        assertEquals(null, page.page().nextCursor());
        ArgumentCaptor<R12ReviewStore.PageQuery> query =
                ArgumentCaptor.forClass(R12ReviewStore.PageQuery.class);
        verify(store).reviews(query.capture());
        assertEquals("priority:desc,createdAt:asc", query.getValue().sort());
        assertEquals(null, query.getValue().cursor());
    }

    @Test
    void assigningQueuedReviewWritesImmutableAssignAndClaimAuditAndOutbox() {
        ReviewAssignRequest request = new ReviewAssignRequest("12", "轮值分配", 3L);
        firstClaim();
        when(store.lockReview(71)).thenReturn(Optional.of(row(
                "PENDING_REVIEW", 3, null, null, null)));
        when(store.activeAdmin(12)).thenReturn(true);
        when(store.submittedSnapshot(71, 3)).thenReturn(Optional.of(SNAPSHOT));
        when(store.sessionDevice(9, 19)).thenReturn(Optional.of("trusted-device"));
        when(store.advance(71, 3, "REVIEWING", NOW)).thenReturn(true);
        when(store.review(71)).thenReturn(Optional.of(row(
                "REVIEWING", 4, 12L, null, null)));

        ReviewActorContext actor = new ReviewActorContext(
                9, 19, "reviewer-9", "review.assign", "request-1", "127.0.0.1");
        var result = service.assign(actor, "71", request, KEY);

        assertEquals("12", result.assigneeId());
        assertEquals(4, result.version());
        ArgumentCaptor<String> actions = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> commands = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).reviewRecord(eq(71L), eq(SNAPSHOT), actions.capture(),
                eq("轮值分配"), eq(12L), commands.capture(), eq(NOW));
        assertEquals(List.of("ASSIGN", "CLAIM"), actions.getAllValues());
        assertNotEquals(commands.getAllValues().get(0), commands.getAllValues().get(1));
        verify(store).statusLog(71, "PENDING_REVIEW", "REVIEWING", "轮值分配", 4,
                "admin:9", NOW);
        verify(store).audit(eq(9L), eq("REVIEW_ASSIGNED"), eq(71L), anyString(), anyString(),
                eq("127.0.0.1"), eq(NOW));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(store).outbox(eq("content.review.assigned.v1"), eq(71L), eq("request-1"), payload.capture());
        assertEquals("trusted-device", json(payload.getValue()).get("device"));
        verify(store).complete(eq(1L), eq("r12.review-resource.v1:ok"),
                eq("r12.review-resource.v1"), anyString());
    }

    @Test
    void approvingReviewBindsSnapshotVersionAuditAndTransactionalOutbox() {
        ReviewDecisionRequest request = new ReviewDecisionRequest(
                "APPROVE", "材料真实完整", 4L, List.of("evidence-1"));
        firstClaim();
        when(store.lockReview(71)).thenReturn(Optional.of(row(
                "REVIEWING", 4, 12L, null, null)));
        when(store.submittedSnapshot(71, 4)).thenReturn(Optional.of(SNAPSHOT));
        when(store.secondReviewState(91)).thenReturn(new R12ReviewStore.SecondReviewState(false, null));
        when(store.sessionDevice(9, 19)).thenReturn(Optional.of("trusted-device"));
        when(store.advance(71, 4, "APPROVED", NOW)).thenReturn(true);
        when(store.review(71)).thenReturn(Optional.of(row(
                "APPROVED", 5, 12L, "APPROVE", "材料真实完整")));

        var result = service.decide(ACTOR, "71", request, KEY);

        assertEquals("APPROVE", result.decision());
        assertEquals("APPROVED", result.status());
        verify(store).reviewRecord(eq(71L), eq(SNAPSHOT), eq("APPROVE"),
                eq("材料真实完整"), eq(9L), anyString(), eq(NOW));
        verify(store).statusLog(71, "REVIEWING", "APPROVED", "材料真实完整", 5,
                "admin:9", NOW);
        verify(store).audit(eq(9L), eq("REVIEW_DECIDED"), eq(71L), anyString(), anyString(),
                eq("127.0.0.1"), eq(NOW));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(store).outbox(eq("content.review.decided.v1"), eq(71L), eq("request-2"), payload.capture());
        assertEquals("review.decide", json(payload.getValue()).get("permission"));
    }

    @Test
    void staleExpectedVersionHasNoReviewAuditOrOutboxSideEffects() {
        ReviewDecisionRequest request = new ReviewDecisionRequest(
                "REJECT", "材料无法核实", 3L, List.of());
        firstClaim();
        when(store.lockReview(71)).thenReturn(Optional.of(row(
                "REVIEWING", 4, 12L, null, null)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.decide(ACTOR, "71", request, KEY));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
        verify(store, never()).advance(anyLong(), anyLong(), anyString(), any());
        verify(store, never()).reviewRecord(anyLong(), any(), anyString(), any(), anyLong(), anyString(), any());
        verify(store, never()).audit(anyLong(), anyString(), anyLong(), any(), any(), any(), any());
        verify(store, never()).outbox(anyString(), anyLong(), anyString(), anyString());
        verify(store, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void sameIdempotencyKeyWithDifferentIntentIsRejectedBeforeLock() {
        when(store.claim(anyString(), eq(KEY), anyString(), any(), any())).thenReturn(
                new R12ReviewStore.IdempotencyClaim(
                        7, "f".repeat(64), "r12.review-resource.v1:ok",
                        "r12.review-resource.v1", "ciphertext", true));
        ReviewAssignRequest request = new ReviewAssignRequest("12", null, 3L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assign(ACTOR, "71", request, KEY));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        verify(store, never()).lockReview(anyLong());
    }

    @Test
    void escalationKeepsReviewingWritesNoStatusLogAndPersistsExactEvidence() {
        ReviewDecisionRequest request = new ReviewDecisionRequest(
                "ESCALATE", "申请二审", 4L, List.of("evidence-b", "evidence-a", "evidence-a"));
        firstClaim();
        when(store.lockReview(71)).thenReturn(Optional.of(row("REVIEWING", 4, 12L, null, null)));
        when(store.submittedSnapshot(71, 4)).thenReturn(Optional.of(SNAPSHOT));
        when(store.secondReviewState(91)).thenReturn(new R12ReviewStore.SecondReviewState(false, null));
        when(store.sessionDevice(9, 19)).thenReturn(Optional.of("trusted-device"));
        when(store.advance(71, 4, "REVIEWING", NOW)).thenReturn(true);
        when(store.review(71)).thenReturn(Optional.of(
                row("REVIEWING", 5, 12L, "ESCALATE", "申请二审")));

        var result = service.decide(ACTOR, "71", request, KEY);

        assertEquals("REVIEWING", result.status());
        assertEquals("ESCALATE", result.decision());
        verify(store, never()).statusLog(anyLong(), anyString(), anyString(), any(), anyLong(), anyString(), any());
        verify(store).reviewRecord(eq(71L), eq(SNAPSHOT), eq("ESCALATE"), eq("申请二审"),
                eq(9L), anyString(), eq(NOW));
        ArgumentCaptor<String> auditAfter = ArgumentCaptor.forClass(String.class);
        verify(store).audit(eq(9L), eq("REVIEW_DECIDED"), eq(71L), anyString(),
                auditAfter.capture(), eq("127.0.0.1"), eq(NOW));
        Map<String, Object> audit = json(auditAfter.getValue());
        assertEquals(List.of("evidence-a", "evidence-b"), audit.get("evidenceIds"));
        assertEquals("trusted-device", audit.get("device"));
        assertEquals("SUCCESS", audit.get("result"));
        ArgumentCaptor<String> outbox = ArgumentCaptor.forClass(String.class);
        verify(store).outbox(eq("content.review.escalated.v1"), eq(71L),
                eq("request-2"), outbox.capture());
        assertEquals(audit.get("commandId"), json(outbox.getValue()).get("commandId"));
    }

    @Test
    void escalationRequiresAssignmentAndTheAssignedSecondReviewer() {
        ReviewDecisionRequest request = new ReviewDecisionRequest(
                "REJECT", "二审不通过", 5L, List.of());
        firstClaim();
        when(store.lockReview(71)).thenReturn(Optional.of(row("REVIEWING", 5, 12L, null, null)));
        when(store.submittedSnapshot(71, 5)).thenReturn(Optional.of(SNAPSHOT));
        when(store.secondReviewState(91)).thenReturn(new R12ReviewStore.SecondReviewState(true, null));

        BusinessException unassigned = assertThrows(BusinessException.class,
                () -> service.decide(ACTOR, "71", request, KEY));
        assertEquals("COMMON-422-BUSINESS_RULE", unassigned.code());
        verify(store, never()).advance(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void wrongSecondReviewerIsRejectedButAssignedReviewerCanDecide() {
        ReviewDecisionRequest request = new ReviewDecisionRequest(
                "REJECT", "二审不通过", 5L, List.of());
        firstClaim();
        when(store.lockReview(71)).thenReturn(Optional.of(row("REVIEWING", 5, 12L, null, null)));
        when(store.submittedSnapshot(71, 5)).thenReturn(Optional.of(SNAPSHOT));
        when(store.secondReviewState(91)).thenReturn(new R12ReviewStore.SecondReviewState(true, 12L));

        assertEquals("COMMON-422-BUSINESS_RULE", assertThrows(BusinessException.class,
                () -> service.decide(ACTOR, "71", request, KEY)).code());

        ReviewActorContext secondReviewer = new ReviewActorContext(
                12, 22, "reviewer-12", "review.decide", "request-6", "127.0.0.2");
        when(store.sessionDevice(12, 22)).thenReturn(Optional.of("second-device"));
        when(store.advance(71, 5, "REJECTED", NOW)).thenReturn(true);
        when(store.review(71)).thenReturn(Optional.of(
                row("REJECTED", 6, 12L, "REJECT", "二审不通过")));

        var result = service.decide(secondReviewer, "71", request, KEY);

        assertEquals("REJECTED", result.status());
        verify(store).statusLog(71, "REVIEWING", "REJECTED", "二审不通过", 6,
                "admin:12", NOW);
    }

    @Test
    void invalidSortAndCrossSortCursorAreRejectedBeforeQuery() {
        assertEquals("COMMON-400-VALIDATION", assertThrows(BusinessException.class,
                () -> service.queue(1, 20, null, null, null, "title:desc")).code());
        assertEquals("COMMON-400-VALIDATION", assertThrows(BusinessException.class,
                () -> service.queue(1, 20, "71", null, null, "id:asc")).code());
        assertEquals("COMMON-400-VALIDATION", assertThrows(BusinessException.class,
                () -> service.queue(1, 20, "dGVzdA", null, null,
                        "priority:desc,createdAt:asc")).code());
        verify(store, never()).reviews(any());
    }

    private void firstClaim() {
        when(store.claim(anyString(), eq(KEY), anyString(), any(), any())).thenAnswer(invocation ->
                new R12ReviewStore.IdempotencyClaim(
                        1, invocation.getArgument(2), null, null, null, false));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(String value) {
        try {
            return mapper.readValue(value, Map.class);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static R12ReviewStore.ReviewRow row(
            String status, long version, Long assigneeId, String decision, String reason) {
        return new R12ReviewStore.ReviewRow(
                71, status, version, NOW.minusSeconds(3600), NOW.minusSeconds(1800),
                "LOW", assigneeId, decision, reason);
    }
}
