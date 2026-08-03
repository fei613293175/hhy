package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R17PaymentContracts.AdminCommandContext;
import cc.orbexa.hhy.commerce.R17PaymentContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.CreatePaymentRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderCallbackContext;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderNotification;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ReconciliationRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ResolvePaymentExceptionRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.UserCommandContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface R17PaymentStore {
    Optional<PaymentResource> cashier(long userId, String orderNo);

    PageSlice payments(Long userId, PageQuery query);

    PaymentResource createPayment(
            UserCommandContext context, String orderNo, CreatePaymentRequest request, String requestHash);

    CallbackResult applyNotification(
            ProviderCallbackContext context,
            ProviderNotification notification,
            String payloadHash,
            String encryptedPayload);

    PaymentResource queryPayment(
            AdminCommandContext context, long paymentId, Long expectedVersion, String requestHash);

    PageSlice callbacks(PageQuery query);

    PageSlice exceptions(PageQuery query);

    CommandResultResource resolveException(
            AdminCommandContext context,
            long exceptionId,
            ResolvePaymentExceptionRequest request,
            String requestHash);

    CommandResultResource reconcile(
            AdminCommandContext context, ReconciliationRequest request, String requestHash);

    interface Codec {
        byte[] canonicalBytes(Object value);
        String json(Object value);
        String encrypt(String scope, String key, String requestHash, String type, byte[] plaintext);
        byte[] decrypt(String scope, String key, String requestHash, String type, String envelope);
        PaymentResource payment(byte[] json);
        Map<String, Object> map(String json);
    }

    record PageQuery(
            int page,
            int pageSize,
            long offset,
            String status,
            String keyword,
            String sort) { }

    record PageSlice(List<PaymentResource> items, long total) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }

    record CallbackResult(PaymentResource payment, boolean amountMismatch, boolean replay) { }

    enum Kind { NOT_FOUND, CONFLICT, BUSINESS_RULE, AMOUNT_MISMATCH, INTERNAL }

    final class StoreException extends RuntimeException {
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
}
