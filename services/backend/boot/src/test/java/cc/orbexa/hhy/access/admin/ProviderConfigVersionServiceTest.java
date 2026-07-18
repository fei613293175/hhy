package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.admin.ProviderConfigLifecycle.Status;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.Approval;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.ApprovalStatus;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.AuditEvent;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.Store;
import cc.orbexa.hhy.access.admin.ProviderConfigVersionService.VersionAggregate;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.ProbeResult;
import cc.orbexa.hhy.access.admin.ProviderConnectionTestCoordinator.TestOutcome;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ProviderConfigVersionServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T14:30:00Z"), ZoneOffset.UTC);

    @Test
    void fullFlowCreatesTestsActivatesSupersedesAndRollsBackAtomically() {
        InMemoryStore store = new InMemoryStore();
        ProviderConfigVersionService service = service(store);
        store.approvals.put("APR-1", approved("APR-1", 10L, 20L));
        store.approvals.put("APR-2", approved("APR-2", 30L, 40L));
        store.approvals.put("APR-R", approved("APR-R", 50L, 60L));

        VersionAggregate first = service.create(
                "sms", "STAGING", smsValues("签名一"), smsSecrets(), "首个版本", 10L);
        first = service.testConnection("sms", first.lifecycle().versionId(),
                "13800000000", first.lifecycle().version(), 10L);
        first = service.activate("sms", first.lifecycle().versionId(),
                "APR-1", first.lifecycle().version(), 20L);

        VersionAggregate second = service.create(
                "sms", "STAGING", smsValues("签名二"), smsSecrets(), "轮换版本", 30L);
        second = service.testConnection("sms", second.lifecycle().versionId(),
                null, second.lifecycle().version(), 30L);
        second = service.activate("sms", second.lifecycle().versionId(),
                "APR-2", second.lifecycle().version(), 40L);

        assertEquals(Status.SUPERSEDED,
                store.versions.get("sms-v1").lifecycle().status());
        assertEquals(Status.ACTIVE, second.lifecycle().status());

        VersionAggregate restored = service.rollback(
                "sms", "sms-v1", "APR-R", "短信发送异常，恢复上一版本",
                second.lifecycle().version(), 60L);

        assertEquals(Status.ACTIVE, restored.lifecycle().status());
        assertEquals(Status.ROLLED_BACK,
                store.versions.get("sms-v2").lifecycle().status());
        assertEquals("APR-R", restored.lifecycle().approvalId());
        assertTrue(store.audits.stream().anyMatch(
                event -> "PROVIDER_CONFIG_ROLLED_BACK".equals(event.action())));
    }

    @Test
    void activationRejectsPendingOrSelfReviewedApproval() {
        InMemoryStore store = new InMemoryStore();
        ProviderConfigVersionService service = service(store);
        VersionAggregate version = service.create(
                "sms", "STAGING", smsValues("合伙云"), smsSecrets(), null, 10L);
        version = service.testConnection("sms", version.lifecycle().versionId(),
                null, version.lifecycle().version(), 10L);
        store.approvals.put("APR-P", new Approval("APR-P", 10L, 0L, ApprovalStatus.PENDING));
        store.approvals.put("APR-S", new Approval("APR-S", 10L, 10L, ApprovalStatus.APPROVED));
        long expected = version.lifecycle().version();

        BusinessException pending = assertThrows(BusinessException.class,
                () -> service.activate("sms", "sms-v1", "APR-P", expected, 20L));
        BusinessException selfReviewed = assertThrows(BusinessException.class,
                () -> service.activate("sms", "sms-v1", "APR-S", expected, 20L));

        assertEquals(422, pending.httpStatus());
        assertEquals(422, selfReviewed.httpStatus());
        assertEquals(Status.CONNECTION_TESTED,
                store.versions.get("sms-v1").lifecycle().status());
    }

    @Test
    void staleConnectionTestIsRejectedBeforeConnectorExecution() {
        InMemoryStore store = new InMemoryStore();
        ProviderConfigVersionService service = service(store);
        VersionAggregate version = service.create(
                "sms", "STAGING", smsValues("合伙云"), smsSecrets(), null, 10L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.testConnection("sms", version.lifecycle().versionId(),
                        null, 99L, 10L));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
        assertEquals(Status.VALIDATED,
                store.versions.get("sms-v1").lifecycle().status());
    }

    private static ProviderConfigVersionService service(InMemoryStore store) {
        var connector = ProviderConnectorAdapters.sms((command, secrets) -> ProbeResult.success());
        var tests = new ProviderConnectionTestCoordinator(List.of(connector),
                reference -> "resolved".toCharArray(), CLOCK);
        return new ProviderConfigVersionService(
                new ProviderConfigValidator(), tests, store,
                new ProviderConfigVersionService.TransactionRunner() {
                    @Override
                    public <T> T inTransaction(Supplier<T> work) {
                        return work.get();
                    }
                }, CLOCK);
    }

    private static Approval approved(String id, long requester, long reviewer) {
        return new Approval(id, requester, reviewer, ApprovalStatus.APPROVED);
    }

    private static ObjectNode smsValues(String sign) {
        return JSON.createObjectNode()
                .put("sms.active_provider", "ALIYUN")
                .put("sms.aliyun.region_id", "cn-hangzhou")
                .put("sms.aliyun.endpoint", "dysmsapi.aliyuncs.com")
                .put("sms.aliyun.sign_name", sign);
    }

    private static ObjectNode smsSecrets() {
        return JSON.createObjectNode()
                .put("sms.aliyun.access_key_id", "vault://staging/sms/id")
                .put("sms.aliyun.access_key_secret", "vault://staging/sms/secret");
    }

    private static final class InMemoryStore implements Store {
        private final Map<String, VersionAggregate> versions = new HashMap<>();
        private final Map<String, Approval> approvals = new HashMap<>();
        private final List<AuditEvent> audits = new ArrayList<>();
        private int sequence;

        @Override
        public String nextVersionId(String provider) {
            return provider + "-v" + (++sequence);
        }

        @Override
        public Optional<VersionAggregate> findForUpdate(String provider, String versionId) {
            VersionAggregate value = versions.get(versionId);
            return value != null && value.config().provider().equals(provider)
                    ? Optional.of(value) : Optional.empty();
        }

        @Override
        public Optional<VersionAggregate> activeForUpdate(String provider) {
            return versions.values().stream().filter(value ->
                    value.config().provider().equals(provider)
                            && value.lifecycle().status() == Status.ACTIVE).findFirst();
        }

        @Override
        public Optional<Approval> approvalForUpdate(String approvalId) {
            return Optional.ofNullable(approvals.get(approvalId));
        }

        @Override
        public void insert(VersionAggregate aggregate, AuditEvent audit) {
            versions.put(aggregate.lifecycle().versionId(), aggregate);
            audits.add(audit);
        }

        @Override
        public void saveConnectionTest(
                VersionAggregate previous, VersionAggregate updated,
                TestOutcome test, long actorId, AuditEvent audit) {
            requireCurrent(previous);
            versions.put(updated.lifecycle().versionId(), updated);
            audits.add(audit);
        }

        @Override
        public void activateAtomically(
                VersionAggregate previousActive, VersionAggregate updatedPreviousActive,
                VersionAggregate target, VersionAggregate activatedTarget, AuditEvent audit) {
            requireCurrent(target);
            if (previousActive != null) {
                requireCurrent(previousActive);
                versions.put(updatedPreviousActive.lifecycle().versionId(), updatedPreviousActive);
            }
            versions.put(activatedTarget.lifecycle().versionId(), activatedTarget);
            audits.add(audit);
        }

        @Override
        public void rollbackAtomically(
                VersionAggregate current, VersionAggregate rolledBackCurrent,
                VersionAggregate target, VersionAggregate restoredTarget, AuditEvent audit) {
            requireCurrent(current);
            requireCurrent(target);
            versions.put(rolledBackCurrent.lifecycle().versionId(), rolledBackCurrent);
            versions.put(restoredTarget.lifecycle().versionId(), restoredTarget);
            audits.add(audit);
        }

        private void requireCurrent(VersionAggregate expected) {
            VersionAggregate current = versions.get(expected.lifecycle().versionId());
            if (current == null || current.lifecycle().version() != expected.lifecycle().version()) {
                throw new AssertionError("in-memory optimistic lock mismatch");
            }
        }
    }
}
