package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R12MembershipContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipBenefitsPutRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipGrantRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipSkuPatchRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface R18MembershipStore {
    PageSlice<MembershipResource> skus(PageQuery query);

    Optional<MembershipResource> current(long userId);

    CommandResultResource createPurchase(
            UserCommand context, long skuId, String paymentChannel, String requestHash);

    MembershipResource createUpgradeQuote(
            UserCommand context, long targetSkuId, String requestHash);

    CommandResultResource createUpgradeOrder(
            UserCommand context, long quoteId, String paymentChannel, String requestHash);

    PageSlice<MembershipResource> userMemberships(PageQuery query);

    MembershipResource patchSku(
            AdminCommand context,
            long membershipSkuId,
            MembershipSkuPatchRequest request,
            String requestHash);

    MembershipResource replaceBenefits(
            AdminCommand context,
            long membershipSkuId,
            MembershipBenefitsPutRequest request,
            String requestHash);

    MembershipResource grant(
            AdminCommand context, MembershipGrantRequest request, String requestHash);

    record PageQuery(
            int page, int pageSize, long offset, String status, String keyword, String sort) { }

    record PageSlice<T>(List<T> items, long total) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }

    record UserCommand(
            long userId,
            String operationId,
            String idempotencyKey,
            String requestId) { }

    record AdminCommand(
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

        Object value(String json);

        List<BenefitResource> benefits(String json);

        String encrypt(
                String scope, String key, String requestHash, String responseType, byte[] plaintext);

        byte[] decrypt(
                String scope, String key, String requestHash, String responseType, String envelope);

        MembershipResource membership(byte[] json);
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
