package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.admin.DomainConfigPolicy.ValidatedDomain;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.LayerStatus;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.OverallStatus;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.ProbeOutcome;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.ServiceKind;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.ServiceTarget;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.VerificationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DomainVerificationCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-07-18T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DomainConfigPolicy policy = new DomainConfigPolicy();

    @Test
    void healthyRequiresAllThreeLayersInOrder() {
        List<String> calls = new ArrayList<>();
        DomainVerificationCoordinator coordinator = new DomainVerificationCoordinator(
                hostname -> {
                    calls.add("dns:" + hostname);
                    return ProbeOutcome.passed("A_OR_AAAA_FOUND");
                },
                hostname -> {
                    calls.add("tls:" + hostname);
                    return ProbeOutcome.passed("CERTIFICATE_VALID");
                },
                target -> {
                    calls.add("service:" + target.healthPath());
                    return ProbeOutcome.passed("HTTP_200_SCHEMA_VALID");
                }, CLOCK);

        VerificationResult result = coordinator.verify(api());

        assertTrue(result.healthy());
        assertEquals(OverallStatus.HEALTHY, result.overallStatus());
        assertEquals(LayerStatus.PASSED, result.dns().status());
        assertEquals(LayerStatus.PASSED, result.tls().status());
        assertEquals(LayerStatus.PASSED, result.service().status());
        assertEquals("VALID", result.certificateStatus());
        assertEquals(NOW, result.verifiedAt());
        assertEquals(List.of(
                "dns:api.orbexa.cc", "tls:api.orbexa.cc",
                "service:/public-api/v1/platform/status"), calls);
    }

    @Test
    void dnsSuccessNeverImpliesServiceHealth() {
        DomainVerificationCoordinator coordinator = new DomainVerificationCoordinator(
                hostname -> ProbeOutcome.passed("A_FOUND"),
                hostname -> ProbeOutcome.passed("CERTIFICATE_VALID"),
                target -> ProbeOutcome.failed("HTTP_503"), CLOCK);

        VerificationResult result = coordinator.verify(api());

        assertFalse(result.healthy());
        assertEquals(OverallStatus.SERVICE_UNHEALTHY, result.overallStatus());
        assertEquals(LayerStatus.PASSED, result.dns().status());
        assertEquals(LayerStatus.PASSED, result.tls().status());
        assertEquals(LayerStatus.FAILED, result.service().status());
        assertNull(result.verifiedAt());
    }

    @Test
    void dnsFailureShortCircuitsTlsAndService() {
        AtomicInteger tlsCalls = new AtomicInteger();
        AtomicInteger serviceCalls = new AtomicInteger();
        DomainVerificationCoordinator coordinator = new DomainVerificationCoordinator(
                hostname -> ProbeOutcome.failed("NXDOMAIN"),
                hostname -> {
                    tlsCalls.incrementAndGet();
                    return ProbeOutcome.passed("CERTIFICATE_VALID");
                },
                target -> {
                    serviceCalls.incrementAndGet();
                    return ProbeOutcome.passed("HTTP_200");
                }, CLOCK);

        VerificationResult result = coordinator.verify(api());

        assertEquals(OverallStatus.DNS_FAILED, result.overallStatus());
        assertEquals(LayerStatus.NOT_RUN, result.tls().status());
        assertEquals(LayerStatus.NOT_RUN, result.service().status());
        assertEquals(0, tlsCalls.get());
        assertEquals(0, serviceCalls.get());
    }

    @Test
    void tlsFailureBlocksApplicationProbeAndRedactsUnsafeDetail() {
        AtomicInteger serviceCalls = new AtomicInteger();
        DomainVerificationCoordinator coordinator = new DomainVerificationCoordinator(
                hostname -> ProbeOutcome.passed("A_FOUND"),
                hostname -> ProbeOutcome.failed("certificate for user@example.invalid"),
                target -> {
                    serviceCalls.incrementAndGet();
                    return ProbeOutcome.passed("HTTP_200");
                }, CLOCK);

        VerificationResult result = coordinator.verify(api());

        assertEquals(OverallStatus.TLS_FAILED, result.overallStatus());
        assertEquals("UNSAFE_DETAIL_REDACTED", result.tls().code());
        assertEquals("INVALID", result.certificateStatus());
        assertEquals(LayerStatus.NOT_RUN, result.service().status());
        assertEquals(0, serviceCalls.get());
    }

    @Test
    void buildsProtocolSpecificTargetsFromFrozenCode() {
        DomainVerificationCoordinator coordinator = new DomainVerificationCoordinator(
                hostname -> ProbeOutcome.passed("A_FOUND"),
                hostname -> ProbeOutcome.passed("CERTIFICATE_VALID"),
                target -> {
                    assertTarget(target);
                    return ProbeOutcome.passed("PROTOCOL_OK");
                }, CLOCK);

        coordinator.verify(policy.validate(
                "stg_ws", "STAGING", "stg-ws.orbexa.cc", "MANAGED"));
        coordinator.verify(policy.validate(
                "download", "PRODUCTION", "download.orbexa.cc", "MANAGED"));
    }

    private static void assertTarget(ServiceTarget target) {
        if (target.code().equals("stg_ws")) {
            assertEquals(ServiceKind.WEBSOCKET, target.kind());
            assertEquals("/ws", target.healthPath());
        } else {
            assertEquals("download", target.code());
            assertEquals(ServiceKind.STATIC_DOWNLOAD, target.kind());
            assertEquals("/", target.healthPath());
        }
    }

    private ValidatedDomain api() {
        return policy.validate("api", "PRODUCTION", "api.orbexa.cc", "MANAGED");
    }
}
