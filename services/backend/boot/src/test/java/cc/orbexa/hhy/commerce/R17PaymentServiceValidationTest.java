package cc.orbexa.hhy.commerce;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.commerce.R17PaymentContracts.CreatePaymentRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.UserCommandContext;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class R17PaymentServiceValidationTest {
    private final R17PaymentStore store = mock(R17PaymentStore.class);
    private final R17PaymentStore.Codec codec = mock(R17PaymentStore.Codec.class);
    private final R17PaymentService service = new R17PaymentService(
            store, codec, Set.of("h5.orbexa.cc"));
    private final UserCommandContext context = new UserCommandContext(
            17L, "paymentPostOrdersByOrdernoPayments", "req-r17", "idem-0123456789abcdef");

    @Test
    void rejectsUnsupportedGatewayAndUnsafeReturnUrls() {
        assertThrows(BusinessException.class, () -> service.createPayment(
                context, "ORD-17", new CreatePaymentRequest("CARD", "https://h5.orbexa.cc/payment/result")));
        assertThrows(BusinessException.class, () -> service.createPayment(
                context, "ORD-17", new CreatePaymentRequest("ALIPAY", "http://h5.orbexa.cc/payment/result")));
        assertThrows(BusinessException.class, () -> service.createPayment(
                context, "ORD-17", new CreatePaymentRequest("ALIPAY", "https://evil.example/payment/result")));
        assertThrows(BusinessException.class, () -> service.createPayment(
                context, "ORD-17", new CreatePaymentRequest("ALIPAY", "https://h5.orbexa.cc:8443/payment/result")));
    }

    @Test
    void acceptsWhitelistedHttpsReturnUrl() {
        when(codec.canonicalBytes(any())).thenReturn(new byte[] { 1, 2, 3 });
        when(store.createPayment(any(), anyString(), any(), anyString()))
                .thenReturn(mock(PaymentResource.class));

        service.createPayment(context, "ORD-17",
                new CreatePaymentRequest("WECHAT_PAY", "https://h5.orbexa.cc/payment/result?orderNo=ORD-17"));
    }
}
