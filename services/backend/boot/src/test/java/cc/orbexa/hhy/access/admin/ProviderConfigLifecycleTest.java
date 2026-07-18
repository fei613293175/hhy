package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.admin.ProviderConfigLifecycle.Status;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProviderConfigLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-07-18T13:45:00Z");

    @Test
    void followsValidatedTestedApprovedActivationPath() {
        var draft = ProviderConfigLifecycle.draft("sms", "sms-v1", 11L);
        var validated = ProviderConfigLifecycle.validated(draft, 0L);
        var tested = ProviderConfigLifecycle.connectionTested(
                validated, true, "aliyun sms endpoint reachable", NOW, 1L);
        var pending = ProviderConfigLifecycle.approvalRequested(tested, "APR-101", 11L, 2L);
        var approved = ProviderConfigLifecycle.approved(pending, "APR-101", 22L, 3L);
        var active = ProviderConfigLifecycle.activated(approved, "APR-101", NOW, 4L);

        assertEquals(Status.ACTIVE, active.status());
        assertEquals(5L, active.version());
        assertEquals(22L, active.approvalReviewerId());
        assertEquals(NOW, active.activatedAt());
    }

    @Test
    void failedConnectionTestCannotEnterApproval() {
        var validated = ProviderConfigLifecycle.validated(
                ProviderConfigLifecycle.draft("storage", "storage-v1", 11L), 0L);
        var failed = ProviderConfigLifecycle.connectionTested(
                validated, false, "endpoint timeout", NOW, 1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> ProviderConfigLifecycle.approvalRequested(
                        failed, "APR-102", 11L, 2L));
        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
        assertEquals(Status.VALIDATED, failed.status());
    }

    @Test
    void requesterCannotApproveOwnChange() {
        var pending = pending();
        BusinessException error = assertThrows(BusinessException.class,
                () -> ProviderConfigLifecycle.approved(pending, "APR-103", 11L, 3L));
        assertEquals(422, error.httpStatus());
    }

    @Test
    void staleExpectedVersionIsRejected() {
        var draft = ProviderConfigLifecycle.draft("identity", "identity-v1", 11L);
        BusinessException error = assertThrows(BusinessException.class,
                () -> ProviderConfigLifecycle.validated(draft, 7L));
        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
    }

    @Test
    void unapprovedVersionCannotActivate() {
        var pending = pending();
        BusinessException error = assertThrows(BusinessException.class,
                () -> ProviderConfigLifecycle.activated(pending, "APR-103", NOW, 3L));
        assertEquals(422, error.httpStatus());
    }

    @Test
    void activatedVersionCanBeSupersededAndRolledBackWithApproval() {
        var approved = ProviderConfigLifecycle.approved(pending(), "APR-103", 22L, 3L);
        var active = ProviderConfigLifecycle.activated(approved, "APR-103", NOW, 4L);
        var superseded = ProviderConfigLifecycle.superseded(active, 5L);
        var rolledBack = ProviderConfigLifecycle.rolledBack(superseded, "APR-103", 6L);

        assertEquals(Status.ROLLED_BACK, rolledBack.status());
        assertEquals(7L, rolledBack.version());
    }

    @Test
    void potentialSecretCannotBeStoredAsTestSummary() {
        var validated = ProviderConfigLifecycle.validated(
                ProviderConfigLifecycle.draft("sms", "sms-v1", 11L), 0L);
        BusinessException error = assertThrows(BusinessException.class,
                () -> ProviderConfigLifecycle.connectionTested(
                        validated, true, "Authorization: abc", NOW, 1L));
        assertEquals(400, error.httpStatus());
    }

    private static ProviderConfigLifecycle.Snapshot pending() {
        var draft = ProviderConfigLifecycle.draft("sms", "sms-v1", 11L);
        var validated = ProviderConfigLifecycle.validated(draft, 0L);
        var tested = ProviderConfigLifecycle.connectionTested(
                validated, true, "aliyun sms endpoint reachable", NOW, 1L);
        return ProviderConfigLifecycle.approvalRequested(tested, "APR-103", 11L, 2L);
    }
}
