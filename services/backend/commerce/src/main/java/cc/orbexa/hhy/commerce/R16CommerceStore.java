package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R16CommerceContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderResource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface R16CommerceStore {
    PageSlice<ProductResource> products(PageQuery query);

    PageSlice<ProductSkuResource> skus(PageQuery query);

    PageSlice<OrderResource> orders(Long ownerUserId, PageQuery query);

    Optional<OrderResource> order(Long ownerUserId, String orderNo);

    ProductResource createProduct(
            CommandContext context, ProductCreateRequest request, String requestHash);

    ProductResource patchProduct(
            CommandContext context, long productId, ProductPatchRequest request, String requestHash);

    ProductSkuResource createSku(
            CommandContext context, ProductSkuCreateRequest request, String requestHash);

    ProductSkuResource patchSku(
            CommandContext context, long skuId, ProductSkuPatchRequest request, String requestHash);

    record PageQuery(
            int page,
            int pageSize,
            long offset,
            String status,
            String keyword,
            String sort) { }

    record PageSlice<T>(List<T> items, long total) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }

    record CommandContext(
            long adminId,
            long sessionId,
            String username,
            String operationId,
            String resourceKey,
            String scope,
            String idempotencyKey,
            String requestId,
            String ip) { }

    interface Codec {
        byte[] canonicalBytes(Map<String, Object> value);

        String json(Object value);

        List<BenefitResource> benefits(String json);

        List<String> strings(String json);

        String encrypt(
                String scope, String key, String requestHash, String responseType, byte[] plaintext);

        byte[] decrypt(
                String scope, String key, String requestHash, String responseType, String envelope);

        ProductResource product(byte[] json);

        ProductSkuResource sku(byte[] json);
    }

    @FunctionalInterface
    interface FaultInjector {
        FaultInjector NONE = point -> { };

        void at(String point);
    }

    final class StoreException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Kind kind;

        public StoreException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public StoreException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }

    enum Kind {
        NOT_FOUND,
        CONFLICT,
        BUSINESS_RULE,
        INVALID_DATA,
        INTERNAL
    }
}

