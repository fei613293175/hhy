package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.MediaContracts.CompleteUploadSessionRequest;
import cc.orbexa.hhy.access.storage.MediaContracts.CreateUploadSessionRequest;
import cc.orbexa.hhy.access.storage.MediaContracts.MediaResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Application service for the three frozen R04 media operations. */
public class MediaUploadService {
    private static final String MEDIA_TYPE = "r04-media-resource-v1";
    private static final String COMMAND_TYPE = "r04-media-command-v1";
    private final Store store;
    private final MediaStorageGateway storage;
    private final PurposePolicy purposes;
    private final Idempotency idempotency;
    private final Clock clock;

    public MediaUploadService(
            Store store, MediaStorageGateway storage, PurposePolicy purposes,
            Idempotency idempotency, Clock clock) {
        this.store = store;
        this.storage = storage;
        this.purposes = purposes;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public MediaResource create(
            UserPrincipal principal, CreateUploadSessionRequest request, String key) {
        requirePrincipal(principal);
        requireKey(key);
        if (request.sizeBytes() < 0 || !request.sha256().matches("^[0-9A-Fa-f]{64}$")) {
            throw business("文件大小或 SHA-256 不符合要求");
        }
        String fingerprint = digest(String.join("\u0000", request.purpose(), request.fileName(),
                request.contentType(), request.sizeBytes().toString(), request.sha256().toLowerCase(Locale.ROOT)));
        return idempotency.execute(scope("create", principal.userId()), key, fingerprint,
                MEDIA_TYPE, MediaResource.class, () -> createOnce(principal.userId(), request, key));
    }

    public MediaResource complete(
            UserPrincipal principal, String id, CompleteUploadSessionRequest request, String key) {
        requirePrincipal(principal);
        requireKey(key);
        long sessionId = resourceId(id);
        String fingerprint = digest(id + "\u0000" + request.etag() + "\u0000" + stableParts(request.parts()));
        return idempotency.execute(scope("complete", principal.userId()), key, fingerprint,
                MEDIA_TYPE, MediaResource.class,
                () -> completeOnce(principal.userId(), sessionId, request, key));
    }

    public CommandResultResource delete(UserPrincipal principal, String id, String key) {
        requirePrincipal(principal);
        requireKey(key);
        long mediaId = resourceId(id);
        String fingerprint = digest(id);
        return idempotency.execute(scope("delete", principal.userId()), key, fingerprint,
                COMMAND_TYPE, CommandResultResource.class,
                () -> deleteOnce(principal.userId(), mediaId));
    }

    private MediaResource createOnce(long userId, CreateUploadSessionRequest request, String key) {
        StorageSelection selection = purposes.resolve(
                userId, request.purpose(), request.contentType(), request.sizeBytes());
        if (selection == null) throw business("上传用途尚未配置存储策略");
        UploadTicket ticket = storage.createUpload(selection, new UploadCommand(
                userId, request.purpose(), request.fileName(), request.contentType(),
                request.sizeBytes(), request.sha256().toLowerCase(Locale.ROOT), key));
        UploadSession session = store.create(new UploadDraft(
                userId, request.purpose(), request.fileName(), request.contentType(),
                request.sizeBytes(), request.sha256().toLowerCase(Locale.ROOT),
                selection.scope(), selection.bindingId(), ticket.objectKey(),
                ticket.providerUploadId(), ticket.expiresAt()));
        return new MediaResource(Long.toString(session.id()), session.purpose(), session.contentType(),
                session.sizeBytes(), session.sha256(), ticket.uploadUrl().toString(), null,
                session.status(), session.expiresAt());
    }

    private MediaResource completeOnce(
            long userId, long sessionId, CompleteUploadSessionRequest request, String key) {
        UploadSession session = store.upload(sessionId, userId).orElseThrow(MediaUploadService::notFound);
        Instant now = Instant.now(clock);
        if (!session.expiresAt().isAfter(now) && !"COMPLETED".equals(session.status())) {
            throw business("上传会话已过期，请重新上传");
        }
        MediaObject media;
        if ("COMPLETED".equals(session.status())) {
            media = store.media(session.mediaId(), userId).orElseThrow(MediaUploadService::notFound);
        } else {
            if (!List.of("CREATED", "UPLOADING", "COMPLETING").contains(session.status())) {
                throw conflict("上传会话当前状态不可完成");
            }
            StoredUpload stored = storage.completeUpload(session, request.etag(), request.parts(), key);
            if (!session.objectKey().equals(stored.objectKey())
                    || session.sizeBytes() != stored.sizeBytes()
                    || !session.sha256().equalsIgnoreCase(stored.sha256())) {
                throw business("上传文件校验失败，请重新上传");
            }
            media = store.complete(session, stored, now);
        }
        ReadTicket read = storage.createReadUrl(media);
        return new MediaResource(Long.toString(media.id()), media.purpose(), media.contentType(),
                media.sizeBytes(), media.sha256(), null, read.url().toString(),
                media.status(), read.expiresAt());
    }

    private CommandResultResource deleteOnce(long userId, long mediaId) {
        MediaObject current = store.media(mediaId, userId).orElseThrow(MediaUploadService::notFound);
        Instant now = Instant.now(clock);
        if ("READY".equals(current.status())) {
            if (store.bound(mediaId)) throw business("媒体已被业务使用，不能删除");
            current = store.markDeletePending(mediaId, userId, now);
        }
        if (!List.of("DELETE_PENDING", "DELETED").contains(current.status())) {
            throw conflict("媒体当前状态不可删除");
        }
        return new CommandResultResource(Long.toString(mediaId), null, current.status(),
                current.version(), current.updatedAt());
    }

    private static void requirePrincipal(UserPrincipal principal) {
        if (principal == null || principal.userId() < 1) throw notFound();
    }

    private static void requireKey(String key) {
        if (key == null || key.length() < 16 || key.length() > 128) {
            throw new BusinessException("COMMON-400-VALIDATION", "幂等键不符合要求", 400, false);
        }
    }

    private static long resourceId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (RuntimeException invalid) {
            throw notFound();
        }
    }

    private static String stableParts(List<Map<String, Object>> parts) {
        if (parts == null) return "[]";
        return parts.stream().map(java.util.TreeMap::new).toList().toString();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to hash media command", failure);
        }
    }

    private static String scope(String operation, long userId) {
        return "media:" + operation + ":" + userId;
    }

    private static BusinessException business(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", message, 409, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "资源不存在或不可见", 404, false);
    }

    public interface PurposePolicy {
        StorageSelection resolve(long userId, String purpose, String contentType, long sizeBytes);
    }

    public interface Idempotency {
        <T> T execute(
                String scope, String key, String requestHash, String responseType,
                Class<T> type, java.util.function.Supplier<T> action);
    }

    public interface MediaStorageGateway {
        UploadTicket createUpload(StorageSelection selection, UploadCommand command);
        StoredUpload completeUpload(
                UploadSession session, String etag, List<Map<String, Object>> parts, String idempotencyKey);
        ReadTicket createReadUrl(MediaObject media);
    }

    public interface Store {
        UploadSession create(UploadDraft draft);
        java.util.Optional<UploadSession> upload(long id, long ownerId);
        java.util.Optional<MediaObject> media(long id, long ownerId);
        MediaObject complete(UploadSession session, StoredUpload stored, Instant completedAt);
        boolean bound(long mediaId);
        MediaObject markDeletePending(long mediaId, long ownerId, Instant now);
    }

    public record StorageSelection(StorageObjectPort.Scope scope, long bindingId) {
        public StorageSelection { Objects.requireNonNull(scope, "scope"); }
    }

    public record UploadCommand(
            long ownerId, String purpose, String fileName, String contentType,
            long sizeBytes, String sha256, String idempotencyKey) { }

    public record UploadTicket(
            String objectKey, String providerUploadId, URI uploadUrl, Instant expiresAt) { }

    public record UploadDraft(
            long ownerId, String purpose, String fileName, String contentType,
            long sizeBytes, String sha256, StorageObjectPort.Scope scope, long bindingId,
            String objectKey, String providerUploadId, Instant expiresAt) { }

    public record UploadSession(
            long id, long ownerId, String purpose, String fileName, String contentType,
            long sizeBytes, String sha256, StorageObjectPort.Scope scope, long bindingId,
            String objectKey, String providerUploadId, String status, Instant expiresAt,
            Long mediaId, long version) { }

    public record StoredUpload(
            String objectKey, String etag, long sizeBytes, String sha256, Instant storedAt) { }

    public record MediaObject(
            long id, long ownerId, String purpose, String contentType, long sizeBytes,
            String sha256, StorageObjectPort.Scope scope, long bindingId, String objectKey,
            String status, long version, Instant updatedAt) { }

    public record ReadTicket(URI url, Instant expiresAt) { }
}
