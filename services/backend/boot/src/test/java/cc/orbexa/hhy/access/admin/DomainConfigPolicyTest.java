package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.admin.DomainConfigPolicy.CertificateMode;
import cc.orbexa.hhy.access.admin.DomainConfigPolicy.Environment;
import cc.orbexa.hhy.access.admin.DomainConfigPolicy.ValidatedDomain;
import cc.orbexa.hhy.shared.api.BusinessException;
import org.junit.jupiter.api.Test;

class DomainConfigPolicyTest {
    private final DomainConfigPolicy policy = new DomainConfigPolicy();

    @Test
    void acceptsFrozenProductionAndStagingMappings() {
        ValidatedDomain production = policy.validate(
                "API", "production", "API.ORBEXA.CC.", null);
        ValidatedDomain staging = policy.validate(
                "stg_download", "STAGING", "stg-download.orbexa.cc", "external");

        assertEquals("api", production.code());
        assertEquals(Environment.PRODUCTION, production.environment());
        assertEquals("api.orbexa.cc", production.hostname());
        assertEquals(CertificateMode.MANAGED, production.certificateMode());
        assertEquals(CertificateMode.EXTERNAL, staging.certificateMode());
    }

    @Test
    void rejectsUnknownCodeEnvironmentDriftAndPlanHostnameDrift() {
        assertValidation(() -> policy.validate(
                "billing", "PRODUCTION", "billing.orbexa.cc", "MANAGED"));
        assertValidation(() -> policy.validate(
                "api", "STAGING", "api.orbexa.cc", "MANAGED"));
        assertValidation(() -> policy.validate(
                "api", "PRODUCTION", "other.orbexa.cc", "MANAGED"));
    }

    @Test
    void rejectsExternalOrAmbiguousHostInput() {
        assertValidation(() -> policy.validate(
                "api", "PRODUCTION", "api.orbexa.cc.attacker.example", "MANAGED"));
        assertValidation(() -> policy.validate(
                "api", "PRODUCTION", "https://api.orbexa.cc", "MANAGED"));
        assertValidation(() -> policy.validate(
                "api", "PRODUCTION", "user@api.orbexa.cc", "MANAGED"));
        assertValidation(() -> policy.validate(
                "api", "PRODUCTION", "*.orbexa.cc", "MANAGED"));
    }

    private static void assertValidation(Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals("COMMON-400-VALIDATION", error.code());
        assertEquals(400, error.httpStatus());
    }
}
