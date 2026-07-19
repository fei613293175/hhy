package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.identity.IdentityProviderClient.FaceDecision;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.LivenessDecision;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderFaceComparison;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderLivenessOutcome;
import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.EvidenceCommand;
import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.ProcessingContext;
import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.ProviderCompletion;
import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.StoredEvidence;
import cc.orbexa.hhy.access.identity.IdentityService.Session;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityProviderResultCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-07-20T04:00:00Z");
    private static final byte[] RAW_LIVENESS = "liveness-result".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final byte[] RAW_COMPARE = "face-result".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
    private final FakeStore store = new FakeStore();
    private final FakeProvider provider = new FakeProvider();
    private final FakeEvidence evidence = new FakeEvidence();
    private IdentityProviderResultCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        ProviderFaceImageDownloader downloader = new ProviderFaceImageDownloader(
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                (source, timeout, maximumBytes) -> new ProviderFaceImageDownloader.Response(
                        200, "image/jpeg", JPEG));
        coordinator = new IdentityProviderResultCoordinator(
                store, provider, downloader, evidence, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void pendingResultKeepsSessionRetryableAndPersistsOnlyRawResult() {
        provider.liveness = new ProviderLivenessOutcome(
                LivenessDecision.PENDING, "order-1", null, RAW_LIVENESS);

        Session result = coordinator.reconcile(7, 11);

        assertEquals("PROVIDER_PROCESSING", result.status());
        assertArrayEquals(RAW_LIVENESS, store.pendingRaw);
        assertEquals(0, evidence.calls);
        assertEquals(0, provider.compareCalls);
    }

    @Test
    void livenessRejectionFinishesWithoutDownloadingPhoto() {
        provider.liveness = new ProviderLivenessOutcome(
                LivenessDecision.REJECTED, "order-1", null, RAW_LIVENESS);

        Session result = coordinator.reconcile(7, 11);

        assertEquals("REJECTED", result.status());
        assertEquals("LIVENESS_REJECTED", store.completion.failureCode());
        assertNull(store.completion.mediaObjectId());
        assertEquals(0, evidence.calls);
    }

    @Test
    void verifiedFaceStoresPrivateEvidenceBeforeAtomicCompletion() {
        provider.liveness = passedLiveness();
        provider.comparison = new ProviderFaceComparison(
                FaceDecision.VERIFIED, 1001, new BigDecimal("88.50"),
                "face-order-1", RAW_COMPARE);

        Session result = coordinator.reconcile(7, 11);

        assertEquals("VERIFIED", result.status());
        assertEquals(91L, store.completion.mediaObjectId());
        assertEquals(new BigDecimal("88.50"), store.completion.score());
        assertArrayEquals(RAW_LIVENESS, store.completion.livenessRaw());
        assertArrayEquals(RAW_COMPARE, store.completion.comparisonRaw());
        assertEquals("LIVENESS_PHOTO", evidence.command.mediaType());
        assertArrayEquals(JPEG, evidence.command.bytes());
    }

    @Test
    void uncertainFaceRoutesToManualReviewWithoutTechnicalFailureCode() {
        provider.liveness = passedLiveness();
        provider.comparison = new ProviderFaceComparison(
                FaceDecision.MANUAL_REVIEW, 1004, new BigDecimal("71.20"),
                "face-order-2", RAW_COMPARE);

        Session result = coordinator.reconcile(7, 11);

        assertEquals("MANUAL_REVIEW", result.status());
        assertNull(store.completion.failureCode());
    }

    @Test
    void terminalSessionDoesNotCallProviderAgain() {
        store.context = new ProcessingContext(session("VERIFIED", 4), "order-1", "张三", "110101199001010011");

        Session result = coordinator.reconcile(7, 11);

        assertEquals("VERIFIED", result.status());
        assertEquals(0, provider.queryCalls);
    }

    @Test
    void mismatchedProviderOrderFailsRetryablyWithoutStateMutation() {
        provider.liveness = new ProviderLivenessOutcome(
                LivenessDecision.PENDING, "different-order", null, RAW_LIVENESS);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> coordinator.reconcile(7, 11));

        assertEquals(500, failure.httpStatus());
        assertTrue(failure.retryable());
        assertNull(store.pendingRaw);
        assertNull(store.completion);
    }

    @Test
    void evidenceDigestMismatchFailsRetryablyBeforeFaceComparisonOrCompletion() {
        provider.liveness = passedLiveness();
        evidence.storedSha256 = "0".repeat(64);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> coordinator.reconcile(7, 11));

        assertEquals(500, failure.httpStatus());
        assertTrue(failure.retryable());
        assertEquals(0, provider.compareCalls);
        assertNull(store.completion);
    }

    private static ProviderLivenessOutcome passedLiveness() {
        return new ProviderLivenessOutcome(
                LivenessDecision.PASSED, "order-1",
                URI.create("https://provider.example.test/face.jpg"), RAW_LIVENESS);
    }

    private static Session session(String status, long version) {
        return new Session(7, 11, "state-1", status, "ALIYUN_MARKET_FACE", null,
                null, NOW.plusSeconds(300), version, 1);
    }

    private static final class FakeStore implements IdentityProviderResultCoordinator.Store {
        ProcessingContext context = new ProcessingContext(
                session("LIVENESS_PENDING", 1), "order-1", "张三", "110101199001010011");
        byte[] pendingRaw;
        ProviderCompletion completion;

        @Override
        public Optional<ProcessingContext> context(long sessionId, long userId) {
            return sessionId == 7 && userId == 11 ? Optional.of(context) : Optional.empty();
        }

        @Override
        public Session recordPending(ProcessingContext current, byte[] raw, Instant now) {
            pendingRaw = raw.clone();
            context = new ProcessingContext(session("PROVIDER_PROCESSING", 2),
                    current.providerOrderNo(), current.realName(), current.idNumber());
            return context.session();
        }

        @Override
        public ProcessingContext recordLivenessPassed(
                ProcessingContext current, byte[] raw, Instant now) {
            pendingRaw = raw.clone();
            context = new ProcessingContext(session("PROVIDER_PROCESSING", 2),
                    current.providerOrderNo(), current.realName(), current.idNumber());
            return context;
        }

        @Override
        public Session complete(ProcessingContext current, ProviderCompletion value, Instant now) {
            completion = value;
            context = new ProcessingContext(session(value.status(), 2),
                    current.providerOrderNo(), current.realName(), current.idNumber());
            return context.session();
        }
    }

    private static final class FakeProvider implements IdentityProviderResultCoordinator.Provider {
        ProviderLivenessOutcome liveness;
        ProviderFaceComparison comparison;
        int queryCalls;
        int compareCalls;

        @Override
        public ProviderLivenessOutcome queryLiveness(String provider, String providerOrderNo) {
            queryCalls++;
            return liveness;
        }

        @Override
        public ProviderFaceComparison compareFace(
                String provider, String realName, String idNumber, byte[] image) {
            compareCalls++;
            return comparison;
        }
    }

    private static final class FakeEvidence implements IdentityProviderResultCoordinator.EvidenceStorage {
        int calls;
        EvidenceCommand command;
        String storedSha256;

        @Override
        public StoredEvidence store(EvidenceCommand value) {
            calls++;
            command = value;
            return new StoredEvidence(91,
                    storedSha256 == null ? value.sha256() : storedSha256);
        }
    }
}
