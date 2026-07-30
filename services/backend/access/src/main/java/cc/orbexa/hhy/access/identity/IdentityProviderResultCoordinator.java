package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityProviderClient.FaceDecision;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.LivenessDecision;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderFaceComparison;
import cc.orbexa.hhy.access.identity.IdentityProviderClient.ProviderLivenessOutcome;
import cc.orbexa.hhy.access.identity.IdentityService.Session;
import cc.orbexa.hhy.access.identity.ProviderFaceImageDownloader.DownloadedFace;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves a provider result without exposing provider payloads or photo URLs to clients. */
public final class IdentityProviderResultCoordinator {
    private static final List<String> PROCESSABLE = List.of("LIVENESS_PENDING", "PROVIDER_PROCESSING");
    private final Store store;
    private final Provider provider;
    private final ProviderFaceImageDownloader downloader;
    private final EvidenceStorage evidenceStorage;
    private final Clock clock;

    public IdentityProviderResultCoordinator(
            Store store, Provider provider, ProviderFaceImageDownloader downloader,
            EvidenceStorage evidenceStorage, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.evidenceStorage = Objects.requireNonNull(evidenceStorage, "evidenceStorage");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Session reconcile(long sessionId, long userId) {
        ProcessingContext context = store.context(sessionId, userId).orElseThrow(
                IdentityProviderResultCoordinator::notFound);
        Session current = context.session();
        if (!PROCESSABLE.contains(current.status())) return current;

        ProviderLivenessOutcome liveness = provider.queryLiveness(
                current.provider(), context.providerOrderNo());
        if (liveness == null || liveness.decision() == null
                || liveness.providerOrderNo() == null
                || !context.providerOrderNo().equals(liveness.providerOrderNo())) {
            throw unavailable();
        }
        Instant now = clock.instant();
        if (liveness.decision() == LivenessDecision.PENDING) {
            return store.recordPending(context, liveness.rawResponse(), now);
        }
        if (liveness.decision() == LivenessDecision.REJECTED) {
            return store.complete(context, new ProviderCompletion(
                    "REJECTED", "LIVENESS_REJECTED", null, null,
                    liveness.rawResponse(), new byte[0], null), now);
        }
        if (liveness.faceImageUrl() == null) throw unavailable();
        context = store.recordLivenessPassed(context, liveness.rawResponse(), now);
        current = context.session();

        DownloadedFace face = downloader.download(liveness.faceImageUrl());
        StoredEvidence stored = evidenceStorage.store(new EvidenceCommand(
                current.userId(), current.id(), "LIVENESS_PHOTO", face.contentType(),
                face.bytes(), face.sha256(), "identity-face-" + current.id()));
        if (stored == null || stored.mediaObjectId() < 1
                || !face.sha256().equalsIgnoreCase(stored.sha256())) {
            throw unavailable();
        }

        ProviderFaceComparison comparison = provider.compareFace(
                current.provider(), context.realName(), context.idNumber(), face.bytes());
        if (comparison == null || comparison.decision() == null) throw unavailable();
        String status = switch (comparison.decision()) {
            case VERIFIED -> "VERIFIED";
            case MANUAL_REVIEW -> "MANUAL_REVIEW";
            case REJECTED -> "REJECTED";
        };
        String failure = comparison.decision() == FaceDecision.REJECTED ? "FACE_NOT_MATCHED" : null;
        return store.complete(context, new ProviderCompletion(
                status, failure, stored.mediaObjectId(), comparison.score(),
                liveness.rawResponse(), comparison.rawResponse(), face.sha256()), now);
    }

    public interface Store {
        Optional<ProcessingContext> context(long sessionId, long userId);
        Session recordPending(ProcessingContext context, byte[] encryptedSource, Instant now);
        ProcessingContext recordLivenessPassed(
                ProcessingContext context, byte[] encryptedSource, Instant now);
        Session complete(ProcessingContext context, ProviderCompletion completion, Instant now);
    }

    public interface Provider {
        ProviderLivenessOutcome queryLiveness(String provider, String providerOrderNo);
        ProviderFaceComparison compareFace(
                String provider, String realName, String idNumber, byte[] image);
    }

    public interface EvidenceStorage {
        StoredEvidence store(EvidenceCommand command);
    }

    public record ProcessingContext(
            Session session, String providerOrderNo, String realName, String idNumber) {
        public ProcessingContext {
            Objects.requireNonNull(session, "session");
            if (providerOrderNo == null || providerOrderNo.isBlank()
                    || realName == null || realName.isBlank()
                    || idNumber == null || idNumber.isBlank()) {
                throw new IllegalArgumentException("identity processing context is incomplete");
            }
        }
    }

    public record EvidenceCommand(
            long userId, long sessionId, String mediaType, String contentType,
            byte[] bytes, String sha256, String idempotencyKey) {
        public EvidenceCommand {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record StoredEvidence(long mediaObjectId, String sha256) { }

    public record ProviderCompletion(
            String status, String failureCode, Long mediaObjectId, BigDecimal score,
            byte[] livenessRaw, byte[] comparisonRaw, String imageSha256) {
        public ProviderCompletion {
            livenessRaw = livenessRaw == null ? new byte[0] : livenessRaw.clone();
            comparisonRaw = comparisonRaw == null ? new byte[0] : comparisonRaw.clone();
        }

        @Override
        public byte[] livenessRaw() {
            return livenessRaw.clone();
        }

        @Override
        public byte[] comparisonRaw() {
            return comparisonRaw.clone();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                "COMMON-500-INTERNAL", "实名认证服务暂时不可用", 500, true);
    }

    private static BusinessException notFound() {
        return new BusinessException(
                "COMMON-404-NOT_FOUND", "资源不存在或不可见", 404, false);
    }
}
