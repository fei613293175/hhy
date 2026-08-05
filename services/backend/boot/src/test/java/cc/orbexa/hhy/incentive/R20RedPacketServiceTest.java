package cc.orbexa.hhy.incentive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.incentive.R20RedPacketContracts.CommandResultResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CreateRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.PatchRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.QuoteRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class R20RedPacketServiceTest {
    private R20RedPacketStore store;
    private R20RedPacketService service;

    @BeforeEach
    void setUp() {
        store = mock(R20RedPacketStore.class);
        R20RedPacketContracts.Codec codec = mock(R20RedPacketContracts.Codec.class);
        when(codec.canonicalBytes(any())).thenReturn(new byte[] { 1, 2, 3 });
        service = new R20RedPacketService(store, codec);
        when(store.userCampaigns(anyLong(), any()))
                .thenReturn(new R20RedPacketStore.PageSlice<>(List.of(), 0));
        when(store.adminCampaigns(any()))
                .thenReturn(new R20RedPacketStore.PageSlice<>(List.of(), 0));
    }

    @Test
    void createRejectsInvalidWindowAndIdempotencyKey() {
        CreateRequest request = new CreateRequest(
                "10", 10L, 100L,
                Instant.parse("2026-08-05T00:00:00Z"),
                Instant.parse("2026-08-04T00:00:00Z"), Map.of());
        assertCode("COMMON-400-VALIDATION", () -> service.create(1, request, "short", "r20-request"));
    }

    @Test
    void quoteReturnsACommandResourceForTheOrderFlow() {
        CommandResultResource expected = new CommandResultResource(
                "22", "R20-QUOTE-22", "QUOTED", 3,
                Instant.parse("2026-08-05T00:00:00Z"));
        when(store.quote(any(), anyLong(), any(), anyString())).thenReturn(expected);
        CommandResultResource actual = service.quote(
                7, "22", new QuoteRequest(10L, 100L, 2L),
                "r20-quote-key-00001", "r20-request-00001");
        assertEquals(expected, actual);
    }

    @Test
    void mapsStoreVersionConflictsToFrozenErrorCode() {
        when(store.quote(any(), anyLong(), any(), anyString()))
                .thenThrow(new R20RedPacketStore.StoreException(
                        R20RedPacketStore.Kind.CONFLICT, "红包活动版本已变化"));
        BusinessException failure = assertThrows(BusinessException.class, () -> service.quote(
                7, "22", new QuoteRequest(10L, 100L, 2L),
                "r20-quote-key-00002", "r20-request-00002"));
        assertEquals("COMMON-409-VERSION_CONFLICT", failure.code());
    }

    @Test
    void patchRejectsValuesAboveCreateLimits() {
        PatchRequest request = new PatchRequest(
                1_000_001L, null, null, null, null, 0L);
        assertCode("COMMON-400-VALIDATION", () -> service.patch(
                7, "22", request, "r20-patch-key-00001", "r20-request-00003"));
    }

    @Test
    void reviewRejectsMoreThanOneHundredEvidenceIdsBeforeStoreCall() {
        AdminReviewRequest request = new AdminReviewRequest(
                "APPROVE", "checked", 0L,
                java.util.stream.IntStream.range(0, 101).mapToObj(Integer::toString).toList());
        AdminCommand actor = new AdminCommand(
                9L, 11L, "reviewer", "adminRedPacketPostRedPacketCampaignsByIdReview",
                "r20-admin-request", "127.0.0.1", "r20-review-key-00001");
        assertCode("COMMON-400-VALIDATION", () -> service.review(
                actor, "22", request, "r20-review-key-00001"));
    }

    @Test
    void adminReviewQueueAcceptsAndPassesTheFrozenCompositeSort() {
        service.adminCampaigns(
                2, 20, null, "PRE_REVIEWING", "content-20",
                "priority:desc,createdAt:asc");

        ArgumentCaptor<R20RedPacketStore.PageQuery> query =
                ArgumentCaptor.forClass(R20RedPacketStore.PageQuery.class);
        verify(store).adminCampaigns(query.capture());
        assertEquals(2, query.getValue().page());
        assertEquals(20L, query.getValue().offset());
        assertEquals("PRE_REVIEWING", query.getValue().status());
        assertEquals("content-20", query.getValue().keyword());
        assertEquals("priority:desc,createdAt:asc", query.getValue().sort());
    }

    private static void assertCode(String code, Runnable action) {
        BusinessException failure = assertThrows(BusinessException.class, action::run);
        assertEquals(code, failure.code());
    }
}
