package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.shared.api.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class R03ConfigurationQueryStoreTest {
    @Test
    void secretReferencesExposeOnlyTheStorageScheme() {
        assertEquals("vault://***", R03ConfigurationQueryStore.maskSecretRef(
                "vault://secret/data/payment/alipay-private-key"));
        assertEquals("kms://***", R03ConfigurationQueryStore.maskSecretRef(
                "kms://cn-shanghai/key/credential"));
        assertEquals("***", R03ConfigurationQueryStore.maskSecretRef("unexpected-value"));
    }

    @Test
    void dynamicSortRejectsAnythingOutsideTheWhitelist() {
        Map<String, String> allowed = Map.of("createdAt:desc", "created_at DESC");
        assertEquals("created_at DESC", R03ConfigurationQueryStore.order(
                allowed, "createdAt:desc", "createdAt:desc"));
        assertThrows(BusinessException.class, () -> R03ConfigurationQueryStore.order(
                allowed, "created_at; DROP TABLE hhy.admin_users", "createdAt:desc"));
    }

    @Test
    void certificateIdsHaveAStableOpaqueApiForm() {
        assertEquals("cert_42", R03ProviderCertificatePostgresStore.externalId(42L));
        assertEquals(42L, R03ProviderCertificatePostgresStore.internalId("cert_42"));
        assertThrows(BusinessException.class,
                () -> R03ProviderCertificatePostgresStore.internalId("42"));
    }
}
