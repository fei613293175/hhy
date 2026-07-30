package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.admin.ProviderCertificateService.Approval;
import cc.orbexa.hhy.access.admin.ProviderCertificateService.ApprovalStatus;
import cc.orbexa.hhy.access.admin.ProviderCertificateService.AuditEvent;
import cc.orbexa.hhy.access.admin.ProviderCertificateService.Status;
import cc.orbexa.hhy.access.admin.ProviderCertificateService.StoredCertificate;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ProviderCertificateServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T15:10:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CONTENT = Base64.getEncoder().encodeToString(
            "encrypted-private-key-material".getBytes(StandardCharsets.UTF_8));

    @Test
    void uploadPersistsOnlyOpaqueReferenceAndZeroizesDecodedMaterial() {
        InMemoryStore store = new InMemoryStore();
        AtomicReference<byte[]> observed = new AtomicReference<>();
        var service = service(store, (descriptor, material, passwordRef) -> {
            observed.set(material);
            assertEquals("vault://prod/payout/password", passwordRef);
            assertFalse(allZero(material));
            return "vault://prod/payout/cert-1";
        });

        var result = service.upload(
                "payout", "alipay_private_key", "主私钥", CONTENT,
                "vault://prod/payout/password", NOW.plusSeconds(86_400), 11L);

        assertEquals(Status.ACTIVE, result.status());
        assertEquals(64, result.fingerprint().length());
        assertTrue(allZero(observed.get()));
        StoredCertificate persisted = store.rows.get(result.id());
        assertEquals("vault://prod/payout/cert-1", persisted.secretRef());
        assertFalse(store.lastAudit.detail().contains("vault://"));
        assertFalse(result.toString().contains("secretRef"));
        assertFalse(result.toString().contains("private-key-material"));
    }

    @Test
    void secondCertificateIsStagedAndApprovedRotationActivatesItAtomically() {
        InMemoryStore store = new InMemoryStore();
        var service = service(store, (descriptor, material, passwordRef) ->
                "vault://prod/payout/" + descriptor.alias().hashCode());
        var current = service.upload(
                "payout", "alipay_private_key", "旧证书", CONTENT,
                null, NOW.plusSeconds(86_400), 11L);
        var replacement = service.upload(
                "payout", "alipay_private_key", "新证书", CONTENT,
                null, NOW.plusSeconds(172_800), 12L);
        store.approvals.put("approval-1",
                new Approval("approval-1", "PROVIDER_CERTIFICATE_ROTATE", "cert-1",
                        21L, 22L, ApprovalStatus.APPROVED));

        var activated = service.rotate(
                current.id(), replacement.id(), "approval-1", "到期前轮换", 0L, 23L);

        assertEquals(Status.ACTIVE, activated.status());
        assertEquals(1L, activated.version());
        assertEquals(Status.ROTATED, store.rows.get(current.id()).status());
        assertEquals(Status.ACTIVE, store.rows.get(replacement.id()).status());
        assertTrue(store.lastAudit.detail().contains("approval=approval-1"));
    }

    @Test
    void rotationRejectsSelfApprovalAndStaleVersion() {
        InMemoryStore selfApprovalStore = preparedRotationStore();
        selfApprovalStore.approvals.put("approval-1",
                new Approval("approval-1", "PROVIDER_CERTIFICATE_ROTATE", "cert-1",
                        21L, 21L, ApprovalStatus.APPROVED));
        var selfApproval = service(selfApprovalStore,
                (descriptor, material, passwordRef) -> "vault://unused/material");

        BusinessException selfApprovalError = assertThrows(BusinessException.class,
                () -> selfApproval.rotate("cert-1", "cert-2", "approval-1", "轮换", 2L, 23L));
        assertEquals("COMMON-422-BUSINESS_RULE", selfApprovalError.code());

        InMemoryStore staleStore = preparedRotationStore();
        staleStore.approvals.put("approval-1",
                new Approval("approval-1", "PROVIDER_CERTIFICATE_ROTATE", "cert-1",
                        21L, 22L, ApprovalStatus.APPROVED));
        var stale = service(staleStore,
                (descriptor, material, passwordRef) -> "vault://unused/material");
        BusinessException staleError = assertThrows(BusinessException.class,
                () -> stale.rotate("cert-1", "cert-2", "approval-1", "轮换", 1L, 23L));
        assertEquals("COMMON-409-VERSION_CONFLICT", staleError.code());
    }

    @Test
    void rotationRejectsApprovalBoundToAnotherCertificate() {
        InMemoryStore store = preparedRotationStore();
        store.approvals.put("approval-1",
                new Approval("approval-1", "PROVIDER_CERTIFICATE_ROTATE", "cert-9",
                        21L, 22L, ApprovalStatus.APPROVED));
        var service = service(store,
                (descriptor, material, passwordRef) -> "vault://unused/material");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.rotate("cert-1", "cert-2", "approval-1", "轮换", 2L, 23L));

        assertEquals("COMMON-422-BUSINESS_RULE", error.code());
    }

    @Test
    void uploadRejectsRawPasswordAndInvalidBase64BeforeVaultCall() {
        var service = service(new InMemoryStore(), (descriptor, material, passwordRef) -> {
            throw new AssertionError("invalid upload must not reach vault");
        });

        BusinessException passwordFailure = assertThrows(BusinessException.class,
                () -> service.upload("payout", "alipay_private_key", "私钥", CONTENT,
                        "plaintext-password", NOW.plusSeconds(60), 11L));
        assertEquals("COMMON-400-VALIDATION", passwordFailure.code());

        BusinessException content = assertThrows(BusinessException.class,
                () -> service.upload("payout", "alipay_private_key", "私钥", "not-base64",
                        null, NOW.plusSeconds(60), 11L));
        assertEquals("COMMON-400-VALIDATION", content.code());
    }

    @Test
    void metadataFailureCompensatesPreviouslyStoredVaultMaterial() {
        InMemoryStore store = new InMemoryStore();
        store.rejectInsert = true;
        AtomicReference<String> deleted = new AtomicReference<>();
        var vault = new ProviderCertificateService.MaterialVault() {
            @Override
            public String store(
                    ProviderCertificateService.MaterialDescriptor descriptor,
                    byte[] material, String passwordSecretRef) {
                return "vault://prod/payout/orphan-candidate";
            }

            @Override
            public void delete(String secretReference) {
                deleted.set(secretReference);
            }
        };

        assertThrows(IllegalStateException.class, () -> service(store, vault).upload(
                "payout", "alipay_private_key", "私钥", CONTENT,
                null, NOW.plusSeconds(60), 11L));
        assertEquals("vault://prod/payout/orphan-candidate", deleted.get());
    }

    private static ProviderCertificateService service(
            InMemoryStore store, ProviderCertificateService.MaterialVault vault) {
        ProviderCertificateService.TransactionRunner direct =
                new ProviderCertificateService.TransactionRunner() {
                    @Override
                    public <T> T inTransaction(Supplier<T> work) {
                        return work.get();
                    }
                };
        return new ProviderCertificateService(store, vault, direct, CLOCK);
    }

    private static InMemoryStore preparedRotationStore() {
        InMemoryStore store = new InMemoryStore();
        store.rows.put("cert-1", new StoredCertificate(
                "cert-1", "payout", "ALIPAY_PRIVATE_KEY", "旧证书", "f1",
                "vault://prod/payout/old", NOW, NOW.plusSeconds(86_400), Status.ACTIVE, 2L));
        store.rows.put("cert-2", new StoredCertificate(
                "cert-2", "payout", "ALIPAY_PRIVATE_KEY", "新证书", "f2",
                "vault://prod/payout/new", NOW, NOW.plusSeconds(172_800), Status.STAGED, 0L));
        return store;
    }

    private static boolean allZero(byte[] value) {
        if (value == null) return false;
        for (byte item : value) if (item != 0) return false;
        return true;
    }

    private static final class InMemoryStore implements ProviderCertificateService.Store {
        private final Map<String, StoredCertificate> rows = new LinkedHashMap<>();
        private final Map<String, Approval> approvals = new LinkedHashMap<>();
        private AuditEvent lastAudit;
        private int sequence;
        private boolean rejectInsert;

        @Override
        public String nextId() {
            return "cert-" + (++sequence);
        }

        @Override
        public Optional<StoredCertificate> findForUpdate(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<StoredCertificate> activeForUpdate(String provider, String certificateType) {
            return rows.values().stream().filter(row -> row.provider().equals(provider)
                    && row.certificateType().equals(certificateType)
                    && row.status() == Status.ACTIVE).findFirst();
        }

        @Override
        public Optional<Approval> approvalForUpdate(String approvalId) {
            return Optional.ofNullable(approvals.get(approvalId));
        }

        @Override
        public void insert(StoredCertificate certificate, AuditEvent audit) {
            if (rejectInsert) throw new IllegalStateException("metadata insert failed");
            rows.put(certificate.id(), certificate);
            lastAudit = audit;
        }

        @Override
        public void rotateAtomically(
                StoredCertificate current,
                StoredCertificate retiredCurrent,
                StoredCertificate replacement,
                StoredCertificate activatedReplacement,
                AuditEvent audit) {
            rows.put(retiredCurrent.id(), retiredCurrent);
            rows.put(activatedReplacement.id(), activatedReplacement);
            lastAudit = audit;
        }
    }
}
