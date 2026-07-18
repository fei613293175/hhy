package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.admin.DomainConfigService.AuditEvent;
import cc.orbexa.hhy.access.admin.DomainConfigService.DnsAction;
import cc.orbexa.hhy.access.admin.DomainConfigService.DomainAggregate;
import cc.orbexa.hhy.access.admin.DomainConfigService.DomainStatus;
import cc.orbexa.hhy.access.admin.DomainConfigService.Store;
import cc.orbexa.hhy.access.admin.DomainConfigService.UpdateReceipt;
import cc.orbexa.hhy.access.admin.DomainConfigService.VerificationReceipt;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.ProbeOutcome;
import cc.orbexa.hhy.access.admin.DomainVerificationCoordinator.VerificationResult;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DomainConfigServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T15:30:00Z"), ZoneOffset.UTC);

    @Test
    void updateUsesExpectedVersionResetsHealthAndCreatesOwnedDnsAction() {
        InMemoryStore store = seededStore(healthyDomain());
        DomainConfigService service = service(store, new AtomicInteger(), true);

        DomainAggregate updated = service.update(
                "api", "api.orbexa.cc", "EXTERNAL", 5L,
                "domain-update-0001", 99L);

        assertEquals(6L, updated.version());
        assertEquals(DomainStatus.PENDING_DNS, updated.status());
        assertNull(updated.lastVerification());
        assertNull(updated.verifiedAt());
        assertEquals("EXTERNAL", updated.config().certificateMode().name());
        assertEquals(1, store.actions.size());
        assertEquals("PROJECT_OWNER", store.actions.getFirst().owner());
        assertTrue(store.actions.getFirst().userActionRequired());
    }

    @Test
    void staleUpdateIsRejectedWithoutMutation() {
        InMemoryStore store = seededStore(pendingDomain());
        DomainConfigService service = service(store, new AtomicInteger(), true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(
                "api", "api.orbexa.cc", "MANAGED", 44L,
                "domain-update-0002", 99L));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
        assertEquals(5L, store.domains.get("api").version());
        assertTrue(store.actions.isEmpty());
    }

    @Test
    void exactVerificationReplayDoesNotProbeOrIncrementTwice() {
        InMemoryStore store = seededStore(pendingDomain());
        AtomicInteger probeRuns = new AtomicInteger();
        DomainConfigService service = service(store, probeRuns, true);

        DomainAggregate first = service.verify(
                "api", false, "domain-verify-0001", 99L);
        DomainAggregate replay = service.verify(
                "api", false, "domain-verify-0001", 99L);

        assertEquals(first, replay);
        assertEquals(6L, replay.version());
        assertEquals(DomainStatus.HEALTHY, replay.status());
        assertEquals(1, probeRuns.get());
        assertEquals(1, store.verifications.size());
    }

    @Test
    void reusedVerificationKeyWithChangedForceIsRejected() {
        InMemoryStore store = seededStore(pendingDomain());
        DomainConfigService service = service(store, new AtomicInteger(), true);
        service.verify("api", false, "domain-verify-0002", 99L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verify("api", true, "domain-verify-0002", 99L));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
    }

    @Test
    void dnsPassButServiceFailureNeverSetsHealthyOrVerifiedAt() {
        InMemoryStore store = seededStore(pendingDomain());
        AtomicInteger probeRuns = new AtomicInteger();
        DomainConfigService service = service(store, probeRuns, false);

        DomainAggregate result = service.verify(
                "api", false, "domain-verify-0003", 99L);

        assertEquals(DomainStatus.SERVICE_UNHEALTHY, result.status());
        assertNull(result.verifiedAt());
        assertEquals("PASSED", result.lastVerification().dns().status().name());
        assertEquals("FAILED", result.lastVerification().service().status().name());
    }

    @Test
    void healthyDomainRequiresExplicitForceForAnotherProbe() {
        InMemoryStore store = seededStore(healthyDomain());
        DomainConfigService service = service(store, new AtomicInteger(), true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verify("api", false, "domain-verify-0004", 99L));

        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
    }

    private static DomainConfigService service(
            InMemoryStore store, AtomicInteger probeRuns, boolean healthy) {
        DomainVerificationCoordinator coordinator = new DomainVerificationCoordinator(
                hostname -> ProbeOutcome.passed("A_FOUND"),
                hostname -> ProbeOutcome.passed("CERTIFICATE_VALID"),
                target -> {
                    probeRuns.incrementAndGet();
                    return healthy
                            ? ProbeOutcome.passed("HTTP_200_SCHEMA_VALID")
                            : ProbeOutcome.failed("HTTP_503");
                }, CLOCK);
        return new DomainConfigService(
                new DomainConfigPolicy(), coordinator, store,
                new DomainConfigService.TransactionRunner() {
                    @Override
                    public <T> T inTransaction(Supplier<T> work) {
                        return work.get();
                    }
                }, CLOCK);
    }

    private static InMemoryStore seededStore(DomainAggregate domain) {
        InMemoryStore store = new InMemoryStore();
        store.domains.put(domain.config().code(), domain);
        return store;
    }

    private static DomainAggregate pendingDomain() {
        DomainConfigPolicy policy = new DomainConfigPolicy();
        return DomainAggregate.pending(policy.validate(
                "api", "PRODUCTION", "api.orbexa.cc", "MANAGED"), 5L);
    }

    private static DomainAggregate healthyDomain() {
        DomainConfigPolicy policy = new DomainConfigPolicy();
        var config = policy.validate(
                "api", "PRODUCTION", "api.orbexa.cc", "MANAGED");
        var coordinator = new DomainVerificationCoordinator(
                hostname -> ProbeOutcome.passed("A_FOUND"),
                hostname -> ProbeOutcome.passed("CERTIFICATE_VALID"),
                target -> ProbeOutcome.passed("HTTP_200_SCHEMA_VALID"), CLOCK);
        VerificationResult result = coordinator.verify(config);
        return new DomainAggregate(config, DomainStatus.HEALTHY, result,
                result.verifiedAt(), 5L);
    }

    private static final class InMemoryStore implements Store {
        private final Map<String, DomainAggregate> domains = new HashMap<>();
        private final Map<String, UpdateReceipt> updates = new HashMap<>();
        private final Map<String, VerificationReceipt> verifications = new HashMap<>();
        private final List<DnsAction> actions = new ArrayList<>();
        private final List<AuditEvent> audits = new ArrayList<>();

        @Override
        public Optional<DomainAggregate> findForUpdate(String code) {
            return Optional.ofNullable(domains.get(code));
        }

        @Override
        public Optional<UpdateReceipt> updateReceiptForUpdate(
                String code, String idempotencyKey) {
            return Optional.ofNullable(updates.get(code + ":" + idempotencyKey));
        }

        @Override
        public Optional<VerificationReceipt> verificationReceiptForUpdate(
                String code, String idempotencyKey) {
            return Optional.ofNullable(verifications.get(code + ":" + idempotencyKey));
        }

        @Override
        public void saveUpdate(
                DomainAggregate previous, DomainAggregate updated,
                DnsAction action, UpdateReceipt receipt, AuditEvent audit) {
            requireCurrent(previous);
            domains.put(updated.config().code(), updated);
            updates.put(updated.config().code() + ":" + receipt.idempotencyKey(), receipt);
            actions.add(action);
            audits.add(audit);
        }

        @Override
        public void saveVerification(
                DomainAggregate previous, DomainAggregate updated,
                VerificationResult verification, VerificationReceipt receipt,
                AuditEvent audit) {
            requireCurrent(previous);
            domains.put(updated.config().code(), updated);
            verifications.put(
                    updated.config().code() + ":" + receipt.idempotencyKey(), receipt);
            audits.add(audit);
        }

        private void requireCurrent(DomainAggregate expected) {
            DomainAggregate current = domains.get(expected.config().code());
            if (current == null || current.version() != expected.version()) {
                throw new AssertionError("in-memory optimistic lock mismatch");
            }
        }
    }
}
