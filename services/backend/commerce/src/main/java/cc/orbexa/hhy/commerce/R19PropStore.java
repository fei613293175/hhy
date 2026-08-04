package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R19PropContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R19PropContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R19PropContracts.HeadlineSlotRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPage;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPatchRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropResource;
import cc.orbexa.hhy.commerce.R19PropContracts.PropUseRequest;
import java.util.List;
import java.util.Map;

public interface R19PropStore {
    PageSlice<PropResource> store(PageQuery query);

    PageSlice<PropResource> userProps(long userId, PageQuery query);

    CommandResultResource order(UserCommand context, long propSkuId, long quantity,
            String paymentChannel, String requestHash);

    CommandResultResource use(UserCommand context, long userPropId, PropUseRequest request,
            String requestHash);

    PageSlice<PropResource> adminProps(PageQuery query);

    PropResource patch(AdminCommand context, long id, PropPatchRequest request, String requestHash);

    PageSlice<PropResource> headlineSlots(PageQuery query);

    PropResource createHeadlineSlot(
            AdminCommand context, HeadlineSlotRequest request, String requestHash);

    PageSlice<PropResource> executions(PageQuery query);

    record PageQuery(
            int page, int pageSize, long offset, String status, String keyword, String sort) { }

    record PageSlice<T>(List<T> items, long total) {
        public PageSlice { items = List.copyOf(items); }
    }

    record UserCommand(long userId, String operationId, String idempotencyKey, String requestId) { }

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
        byte[] canonicalBytes(Object value);

        String json(Object value);

        Map<String, Object> object(String json);
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

        public Kind kind() { return kind; }
    }

    enum Kind { NOT_FOUND, CONFLICT, BUSINESS_RULE, INVALID_DATA, INTERNAL }
}
