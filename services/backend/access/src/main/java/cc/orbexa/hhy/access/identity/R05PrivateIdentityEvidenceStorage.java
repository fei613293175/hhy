package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.EvidenceCommand;
import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.StoredEvidence;
import cc.orbexa.hhy.access.storage.MediaUploadService.MediaObject;
import cc.orbexa.hhy.access.storage.MediaUploadService.StorageSelection;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadCommand;
import cc.orbexa.hhy.access.storage.R04MediaPostgresStore;
import cc.orbexa.hhy.access.storage.R04MediaPurposePolicy;
import cc.orbexa.hhy.access.storage.R04MediaStorageGateway;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import cc.orbexa.hhy.access.storage.StorageObjectPort.StoredObject;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadTicket;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Writes bounded identity photos through the activated R04 private_kyc binding. */
public final class R05PrivateIdentityEvidenceStorage
        implements IdentityProviderResultCoordinator.EvidenceStorage {
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(15);
    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "authorization", "cookie", "proxy-authorization", "host",
            "connection", "content-length", "transfer-encoding");
    private final PurposeResolver purposes;
    private final StorageGateway storage;
    private final MediaRegistrar media;
    private final UploadTransport transport;
    private final Clock clock;

    public R05PrivateIdentityEvidenceStorage(
            R04MediaPurposePolicy purposes, R04MediaStorageGateway storage,
            R04MediaPostgresStore media, Clock clock) {
        this(purposes::resolve, new StorageGateway() {
            @Override
            public UploadTicket create(StorageSelection selection, UploadCommand command) {
                return storage.createServerUpload(selection, command);
            }

            @Override
            public StoredObject complete(
                    StorageSelection selection, UploadCommand command,
                    String objectKey, String etag, String idempotencyKey) {
                return storage.completeServerUpload(
                        selection, command, objectKey, etag, idempotencyKey);
            }
        }, media::registerPrivateIdentityEvidence, new JdkUploadTransport(), clock);
    }

    R05PrivateIdentityEvidenceStorage(
            PurposeResolver purposes, StorageGateway storage,
            MediaRegistrar media, UploadTransport transport, Clock clock) {
        this.purposes = Objects.requireNonNull(purposes, "purposes");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.media = Objects.requireNonNull(media, "media");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public StoredEvidence store(EvidenceCommand evidence) {
        try {
            requireEvidence(evidence);
            StorageSelection selection = purposes.resolve(
                    evidence.userId(), "private_kyc", evidence.contentType(), evidence.bytes().length);
            if (selection == null || selection.scope() != Scope.PRIVATE_KYC) throw unavailable();
            String extension = "image/png".equals(evidence.contentType()) ? ".png" : ".jpg";
            UploadCommand command = new UploadCommand(
                    evidence.userId(), "private_kyc",
                    "identity-session-" + evidence.sessionId() + extension,
                    evidence.contentType(), evidence.bytes().length,
                    evidence.sha256(), evidence.idempotencyKey());
            UploadTicket ticket = storage.create(selection, command);
            Map<String, String> headers = safeHeaders(ticket.headers(), evidence.contentType());
            UploadResponse uploaded = transport.put(
                    ticket.uploadUrl(), headers, evidence.bytes(), UPLOAD_TIMEOUT);
            if (uploaded == null || uploaded.statusCode() < 200 || uploaded.statusCode() >= 300
                    || uploaded.etag() == null || uploaded.etag().isBlank()) {
                throw unavailable();
            }
            StoredObject stored = storage.complete(
                    selection, command, ticket.objectKey(), uploaded.etag(),
                    evidence.idempotencyKey() + "-complete");
            if (!ticket.objectKey().equals(stored.objectKey())
                    || evidence.bytes().length != stored.sizeBytes()
                    || !evidence.sha256().equalsIgnoreCase(stored.sha256())) {
                throw unavailable();
            }
            MediaObject registered = media.register(
                    evidence.userId(), evidence.sessionId(), evidence.contentType(),
                    selection, stored, clock.instant());
            if (registered.scope() != Scope.PRIVATE_KYC
                    || !"READY".equals(registered.status())) {
                throw unavailable();
            }
            return new StoredEvidence(registered.id(), registered.sha256());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (BusinessException failure) {
            throw unavailable();
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    private static void requireEvidence(EvidenceCommand evidence) {
        if (evidence == null || evidence.userId() < 1 || evidence.sessionId() < 1
                || !"LIVENESS_PHOTO".equals(evidence.mediaType())
                || !("image/jpeg".equals(evidence.contentType())
                    || "image/png".equals(evidence.contentType()))
                || evidence.bytes().length < 1
                || evidence.bytes().length > ProviderFaceImageDownloader.MAX_IMAGE_BYTES
                || evidence.sha256() == null
                || !evidence.sha256().matches("^[0-9A-Fa-f]{64}$")
                || evidence.idempotencyKey() == null || evidence.idempotencyKey().isBlank()) {
            throw unavailable();
        }
        try {
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(evidence.bytes()));
            if (!actual.equalsIgnoreCase(evidence.sha256())) throw unavailable();
        } catch (BusinessException mismatch) {
            throw mismatch;
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    private static Map<String, String> safeHeaders(
            Map<String, String> required, String contentType) {
        if (required == null || required.size() > 16) throw unavailable();
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : required.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().strip().toLowerCase(Locale.ROOT);
            String value = entry.getValue() == null ? "" : entry.getValue().strip();
            if (!name.matches("^[a-z0-9-]{1,64}$") || FORBIDDEN_HEADERS.contains(name)
                    || value.isEmpty() || value.length() > 2048
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw unavailable();
            }
            headers.put(name, value);
        }
        headers.putIfAbsent("content-type", contentType);
        return Map.copyOf(headers);
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                "COMMON-500-INTERNAL", "实名认证服务暂时不可用", 500, true);
    }

    @FunctionalInterface
    interface PurposeResolver {
        StorageSelection resolve(long userId, String purpose, String contentType, long sizeBytes);
    }

    interface StorageGateway {
        UploadTicket create(StorageSelection selection, UploadCommand command);

        StoredObject complete(
                StorageSelection selection, UploadCommand command,
                String objectKey, String etag, String idempotencyKey);
    }

    @FunctionalInterface
    interface MediaRegistrar {
        MediaObject register(
                long ownerId, long sessionId, String contentType,
                StorageSelection selection, StoredObject stored, java.time.Instant now);
    }

    @FunctionalInterface
    interface UploadTransport {
        UploadResponse put(
                URI uploadUrl, Map<String, String> headers,
                byte[] body, Duration timeout) throws Exception;
    }

    record UploadResponse(int statusCode, String etag) { }

    private static final class JdkUploadTransport implements UploadTransport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(UPLOAD_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        @Override
        public UploadResponse put(
                URI uploadUrl, Map<String, String> headers,
                byte[] body, Duration timeout) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(uploadUrl).timeout(timeout);
            headers.forEach(request::header);
            HttpResponse<Void> response = client.send(
                    request.PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                    HttpResponse.BodyHandlers.discarding());
            return new UploadResponse(response.statusCode(),
                    response.headers().firstValue("ETag").orElse(null));
        }
    }
}
