package cc.orbexa.hhy.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.commerce.R16CommerceContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R16CommerceContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuCreateRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class R16CommerceServiceTest {
    private R16CommerceStore store;
    private R16CommerceService service;

    @BeforeEach
    void setUp() {
        store = mock(R16CommerceStore.class);
        service = new R16CommerceService(store, codec());
        when(store.products(any())).thenReturn(new R16CommerceStore.PageSlice<>(List.of(), 0));
        when(store.skus(any())).thenReturn(new R16CommerceStore.PageSlice<>(List.of(), 0));
        when(store.orders(any(), any())).thenReturn(new R16CommerceStore.PageSlice<>(List.of(), 0));
    }

    @Test
    void fixedScopeIsStableBoundedAndStronglyIsolated() {
        String longestOperation = "o".repeat(64);
        String longestResource = "r".repeat(64);
        String baseline = R16CommerceService.scope(Long.MAX_VALUE, longestOperation, longestResource);

        assertTrue(baseline.startsWith("r16adm:"));
        assertTrue(baseline.length() < 64);
        assertEquals(baseline,
                R16CommerceService.scope(Long.MAX_VALUE, longestOperation, longestResource));
        assertNotEquals(baseline,
                R16CommerceService.scope(Long.MAX_VALUE - 1, longestOperation, longestResource));
        assertNotEquals(baseline,
                R16CommerceService.scope(Long.MAX_VALUE, "p" + longestOperation.substring(1), longestResource));
        assertNotEquals(baseline,
                R16CommerceService.scope(Long.MAX_VALUE, longestOperation, "s" + longestResource.substring(1)));
    }

    @Test
    void queryRejectsUnknownSortOversizedPageAndCursorPageMixing() {
        assertCode("COMMON-400-VALIDATION",
                () -> service.products(1, 101, null, null, null, "updatedAt:desc"));
        assertCode("COMMON-400-VALIDATION",
                () -> service.products(1, 20, null, null, null, "sql:desc"));
        assertCode("COMMON-400-VALIDATION",
                () -> service.products(2, 20, "cjE2CnVwZGF0ZWRBdDpkZXNjCjIw",
                        null, null, "updatedAt:desc"));
    }

    @Test
    void skuValidationRejectsUnsafeCommissionWindowAndOversizedBenefits() {
        ProductSkuCreateRequest invalidCommission = new ProductSkuCreateRequest(
                "1", "SKU-1", "商品SKU", 100L, null, 30L,
                List.of(new BenefitResource("B1", "权益", true, null)),
                false, 1, 0, null, null, "ACTIVE");
        assertCode("COMMON-400-VALIDATION", () -> service.createSku(
                actor("adminProductsPostSkus"), invalidCommission, "r16-test-key-000001"));

        ProductSkuCreateRequest invalidWindow = new ProductSkuCreateRequest(
                "1", "SKU-2", "商品SKU", 100L, null, 30L,
                List.of(new BenefitResource("B1", "权益", true, null)),
                true, 100, 100, Instant.parse("2026-07-29T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z"), "ACTIVE");
        assertCode("COMMON-400-VALIDATION", () -> service.createSku(
                actor("adminProductsPostSkus"), invalidWindow, "r16-test-key-000002"));

        List<BenefitResource> tooMany = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(index -> new BenefitResource("B" + index, "权益" + index, index, null))
                .toList();
        ProductSkuCreateRequest invalidBenefits = new ProductSkuCreateRequest(
                "1", "SKU-3", "商品SKU", 100L, null, 30L,
                tooMany, true, 0, 0, null, null, "ACTIVE");
        assertCode("COMMON-400-VALIDATION", () -> service.createSku(
                actor("adminProductsPostSkus"), invalidBenefits, "r16-test-key-000003"));
    }

    @Test
    void frozenBenefitAndDurationMaximumBoundariesReachTheStore() {
        ProductSkuCreateRequest maximum = new ProductSkuCreateRequest(
                "1", "SKU-MAX", "商品SKU", 0L, 0L, 0L,
                List.of(new BenefitResource(
                        "B1", "名".repeat(255), Map.of("enabled", true), "U".repeat(64))),
                true, 5000, 5000, null, null, "ACTIVE");
        when(store.createSku(any(), any(), any())).thenReturn(
                new R16CommerceContracts.ProductSkuResource(
                        "1", "1", "SKU-MAX", "商品SKU", 0, 0L, 0L,
                        maximum.benefits(), true, 5000, 5000,
                        null, null, "ACTIVE", 0));

        service.createSku(
                actor("adminProductsPostSkus"), maximum, "r16-test-key-000005");
    }

    @Test
    void administratorOperationIdCannotBeSubstituted() {
        ProductCreateRequest request =
                new ProductCreateRequest("P-1", "商品", "APP", null, 0, "ACTIVE");
        assertCode("COMMON-401-UNAUTHENTICATED", () -> service.createProduct(
                actor("adminProductsPatchProductsById"), request, "r16-test-key-000004"));
    }

    private static AdminActorContext actor(String operationId) {
        return new AdminActorContext(
                9, 10, "r16-admin", operationId, "r16-request-1", "127.0.0.1");
    }

    private static void assertCode(String code, Runnable action) {
        BusinessException failure = assertThrows(BusinessException.class, action::run);
        assertEquals(code, failure.code());
    }

    private static R16CommerceStore.Codec codec() {
        ObjectMapper mapper =
                new ObjectMapper().findAndRegisterModules()
                        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return new R16CommerceStore.Codec() {
            @Override
            public byte[] canonicalBytes(Map<String, Object> value) {
                try {
                    return mapper.writeValueAsBytes(value);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }

            @Override public String json(Object value) { throw new UnsupportedOperationException(); }
            @Override public List<BenefitResource> benefits(String json) { throw new UnsupportedOperationException(); }
            @Override public List<String> strings(String json) { throw new UnsupportedOperationException(); }
            @Override public String encrypt(
                    String scope, String key, String hash, String type, byte[] plaintext) {
                throw new UnsupportedOperationException();
            }
            @Override public byte[] decrypt(
                    String scope, String key, String hash, String type, String envelope) {
                throw new UnsupportedOperationException();
            }
            @Override public R16CommerceContracts.ProductResource product(byte[] json) {
                throw new UnsupportedOperationException();
            }
            @Override public R16CommerceContracts.ProductSkuResource sku(byte[] json) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
