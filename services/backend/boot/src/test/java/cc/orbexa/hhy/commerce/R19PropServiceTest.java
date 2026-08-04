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

import cc.orbexa.hhy.commerce.R19PropContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R19PropContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R19PropContracts.PropOrderRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPatchRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropResource;
import cc.orbexa.hhy.commerce.R19PropContracts.PropUseRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class R19PropServiceTest {
    private R19PropStore store;
    private R19PropService service;

    @BeforeEach
    void setUp() {
        store = mock(R19PropStore.class);
        R19PropStore.Codec codec = mock(R19PropStore.Codec.class);
        when(codec.canonicalBytes(any())).thenReturn(new byte[] { 1, 2, 3 });
        service = new R19PropService(store, codec);
        when(store.store(any())).thenReturn(new R19PropStore.PageSlice<>(List.of(), 0));
        when(store.userProps(anyLong(), any()))
                .thenReturn(new R19PropStore.PageSlice<>(List.of(), 0));
        when(store.adminProps(any())).thenReturn(new R19PropStore.PageSlice<>(List.of(), 0));
        when(store.headlineSlots(any())).thenReturn(new R19PropStore.PageSlice<>(List.of(), 0));
        when(store.executions(any())).thenReturn(new R19PropStore.PageSlice<>(List.of(), 0));
    }

    @Test
    void scopeIsStableBoundedAndActorIsolated() {
        String operation = "o".repeat(64);
        String resource = "r".repeat(64);
        String baseline = R19PropService.scope(Long.MAX_VALUE, operation, resource);

        assertTrue(baseline.startsWith("r19adm:"));
        assertTrue(baseline.length() < 128);
        assertEquals(baseline, R19PropService.scope(Long.MAX_VALUE, operation, resource));
        assertNotEquals(baseline,
                R19PropService.scope(Long.MAX_VALUE - 1, operation, resource));
    }

    @Test
    void rejectsUnknownSortCursorAndOversizedPage() {
        assertCode("COMMON-400-VALIDATION",
                () -> service.propStore(1, 1, 101, null, null, null, "name:asc"));
        assertCode("COMMON-400-VALIDATION",
                () -> service.propStore(1, 1, 20, "cursor", null, null, "name:asc"));
        assertCode("COMMON-400-VALIDATION",
                () -> service.propStore(1, 1, 20, null, null, null, "sql:desc"));
    }

    @Test
    void orderRejectsUnsafeChannelAndAcceptsFrozenChannels() {
        assertCode("COMMON-400-VALIDATION", () -> service.order(
                1, new PropOrderRequest("9", 1L, "CARD"),
                "r19-order-key-000001", "r19-request-1"));

        when(store.order(any(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(new CommandResultResource(
                        "8", "R19-ORDER", "PENDING_PAYMENT", 0,
                        Instant.parse("2026-08-04T00:00:00Z")));
        CommandResultResource result = service.order(
                1, new PropOrderRequest("9", 2L, "WECHAT_PAY"),
                "r19-order-key-000002", "r19-request-2");
        assertEquals("PENDING_PAYMENT", result.status());
    }

    @Test
    void useRejectsNonNumericContentAndMissingVersionAsValidationErrors() {
        assertCode("COMMON-400-VALIDATION", () -> service.use(
                1, "8", new PropUseRequest("not-a-db-id", null, 0L),
                "r19-use-key-0000001", "r19-request-3"));
        assertCode("COMMON-400-VALIDATION", () -> service.use(
                1, "8", new PropUseRequest("9", null, null),
                "r19-use-key-0000002", "r19-request-4"));
    }

    @Test
    void patchKeepsExplicitNullPayloadValuesAndMapsVersionConflict() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", null);
        PropPatchRequest request = new PropPatchRequest("清空范围", 2L, payload);
        PropResource resource = new PropResource(
                "7", "TOP", "置顶道具", 0, "ACTIVE", null, Map.of(), 3);
        when(store.patch(any(), anyLong(), any(), anyString())).thenReturn(resource);
        assertEquals(resource, service.patch(
                actor(), "7", request, "r19-patch-key-00001"));

        when(store.patch(any(), anyLong(), any(), anyString()))
                .thenThrow(new R19PropStore.StoreException(
                        R19PropStore.Kind.CONFLICT, "版本已变化"));
        assertCode("COMMON-409-VERSION_CONFLICT", () -> service.patch(
                actor(), "7", request, "r19-patch-key-00002"));
    }

    private static AdminActorContext actor() {
        return new AdminActorContext(
                7, 8, "r19-admin", "adminPropsPatchPropsById",
                "r19-admin-request", "127.0.0.1");
    }

    private static void assertCode(String code, Runnable action) {
        BusinessException failure = assertThrows(BusinessException.class, action::run);
        assertEquals(code, failure.code());
    }
}
