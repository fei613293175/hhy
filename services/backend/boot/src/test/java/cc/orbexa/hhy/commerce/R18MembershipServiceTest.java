package cc.orbexa.hhy.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.commerce.R18MembershipContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R18MembershipContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipGrantRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipOrderRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class R18MembershipServiceTest {
    private R18MembershipStore store;
    private R18MembershipService service;

    @BeforeEach
    void setUp() {
        store = mock(R18MembershipStore.class);
        R18MembershipStore.Codec codec = mock(R18MembershipStore.Codec.class);
        when(codec.canonicalBytes(any())).thenReturn(new byte[] { 1, 2, 3 });
        service = new R18MembershipService(store, codec);
        when(store.skus(any())).thenReturn(new R18MembershipStore.PageSlice<>(List.of(), 0));
        when(store.userMemberships(any()))
                .thenReturn(new R18MembershipStore.PageSlice<>(List.of(), 0));
    }

    @Test
    void scopeIsStableBoundedAndActorIsolated() {
        String operation = "o".repeat(64);
        String resource = "r".repeat(64);
        String baseline = R18MembershipService.scope(Long.MAX_VALUE, operation, resource);

        assertTrue(baseline.startsWith("r18adm:"));
        assertTrue(baseline.length() < 64);
        assertEquals(baseline, R18MembershipService.scope(Long.MAX_VALUE, operation, resource));
        assertNotEquals(baseline,
                R18MembershipService.scope(Long.MAX_VALUE - 1, operation, resource));
    }

    @Test
    void rejectsUnknownSortCursorAndOversizedPage() {
        assertCode("COMMON-400-VALIDATION",
                () -> service.skus(1, 1, 101, null, null, null, "priceCent:asc"));
        assertCode("COMMON-400-VALIDATION",
                () -> service.skus(1, 1, 20, "cursor", null, null, "priceCent:asc"));
        assertCode("COMMON-400-VALIDATION",
                () -> service.skus(1, 1, 20, null, null, null, "sql:desc"));
    }

    @Test
    void purchaseRejectsUnsafeChannelAndAcceptsFrozenChannels() {
        assertCode("COMMON-400-VALIDATION", () -> service.createPurchase(
                1, new MembershipOrderRequest("9", "CARD"),
                "r18-purchase-key-0001", "r18-request-1"));

        when(store.createPurchase(any(), anyLong(), anyString(), anyString()))
                .thenReturn(new CommandResultResource(
                        "8", "R18-ORDER", "PENDING_PAYMENT", 0,
                        Instant.parse("2026-08-04T00:00:00Z")));
        CommandResultResource result = service.createPurchase(
                1, new MembershipOrderRequest("9", "WECHAT_PAY"),
                "r18-purchase-key-0002", "r18-request-2");
        assertEquals("PENDING_PAYMENT", result.status());
    }

    @Test
    void grantRequiresReasonAndBoundedDuration() {
        assertCode("COMMON-400-VALIDATION", () -> service.grant(
                actor(), new MembershipGrantRequest("7", null, 0L, "赠送"),
                "r18-grant-key-000001"));
        assertCode("COMMON-400-VALIDATION", () -> service.grant(
                actor(), new MembershipGrantRequest("7", null, 30L, " "),
                "r18-grant-key-000002"));
    }

    @Test
    void storeVersionConflictMapsToFrozenError() {
        when(store.createPurchase(any(), anyLong(), anyString(), anyString()))
                .thenThrow(new R18MembershipStore.StoreException(
                        R18MembershipStore.Kind.CONFLICT, "幂等键冲突"));
        assertCode("COMMON-409-VERSION_CONFLICT", () -> service.createPurchase(
                1, new MembershipOrderRequest("9", "ALIPAY"),
                "r18-purchase-key-0003", "r18-request-3"));
    }

    private static AdminActorContext actor() {
        return new AdminActorContext(
                7, 8, "r18-admin", "adminMembershipPostUserMembershipsGrants",
                "r18-request-admin", "127.0.0.1");
    }

    private static void assertCode(String code, Runnable action) {
        BusinessException failure = assertThrows(BusinessException.class, action::run);
        assertEquals(code, failure.code());
    }
}
