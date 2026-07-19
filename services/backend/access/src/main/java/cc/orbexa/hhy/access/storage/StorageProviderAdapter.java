package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.CompleteUpload;
import cc.orbexa.hhy.access.storage.StorageObjectPort.MigrationPage;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ObjectRef;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ReadTicket;
import cc.orbexa.hhy.access.storage.StorageObjectPort.StoredObject;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadIntent;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadTicket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Shared R2/OSS adapter contract; the injected transport owns SDK calls and secret resolution. */
public final class StorageProviderAdapter implements StorageObjectPort {
    private static final Duration MAX_PRIVATE_READ_TTL = Duration.ofMinutes(5);
    private final Provider provider;
    private final StorageTransport transport;
    private final Clock clock;

    public StorageProviderAdapter(Provider provider, StorageTransport transport, Clock clock) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public UploadTicket createUpload(Binding binding, UploadIntent intent) {
        requireBinding(binding);
        requireObjectInputs(intent.fileName(), intent.sizeBytes(), intent.sha256(), intent.idempotencyKey());
        UploadTicket ticket = transport.createUpload(binding, intent);
        requireSignedUrl(ticket.uploadUrl(), ticket.expiresAt(), Duration.ofMinutes(30));
        return ticket;
    }

    @Override
    public StoredObject completeUpload(Binding binding, CompleteUpload command) {
        requireBinding(binding);
        requireObjectKey(command.objectKey());
        requireSha256(command.expectedSha256());
        if (command.expectedSizeBytes() < 0 || blank(command.etag()) || blank(command.idempotencyKey())) {
            throw new IllegalArgumentException("invalid completion contract");
        }
        StoredObject stored = transport.completeUpload(binding, command);
        if (!command.objectKey().equals(stored.objectKey())
                || command.expectedSizeBytes() != stored.sizeBytes()
                || !command.expectedSha256().equalsIgnoreCase(stored.sha256())) {
            throw new IllegalStateException("provider completion does not match expected object identity");
        }
        return stored;
    }

    @Override
    public ReadTicket createReadUrl(Binding binding, ObjectRef object, Duration ttl) {
        requireBinding(binding);
        requireObjectKey(object.objectKey());
        requireSha256(object.sha256());
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("read ttl must be positive");
        }
        if (!binding.scope().privateAccess()) {
            URI publicUrl = binding.publicBaseUrl();
            requireHttps(publicUrl, false);
            return new ReadTicket(publicUrl.resolve(encodePath(object.objectKey())), Instant.MAX);
        }
        if (ttl.compareTo(MAX_PRIVATE_READ_TTL) > 0) {
            throw new IllegalArgumentException("private read ttl exceeds five minutes");
        }
        ReadTicket ticket = transport.createReadUrl(binding, object, ttl);
        requireSignedUrl(ticket.readUrl(), ticket.expiresAt(), MAX_PRIVATE_READ_TTL);
        return ticket;
    }

    @Override
    public void delete(Binding binding, ObjectRef object) {
        requireBinding(binding);
        requireObjectKey(object.objectKey());
        transport.delete(binding, object);
    }

    @Override
    public MigrationPage scan(Binding binding, String cursor, int limit) {
        requireBinding(binding);
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("scan limit out of range");
        MigrationPage page = transport.scan(binding, cursor, limit);
        if (!page.complete() && blank(page.nextCursor())) {
            throw new IllegalStateException("incomplete migration page requires a cursor");
        }
        return page;
    }

    @Override
    public void copy(Binding source, Binding target, ObjectRef object) {
        requireBinding(source);
        requireBinding(target);
        requireObjectKey(object.objectKey());
        if (source.scope() != target.scope()) throw new IllegalArgumentException("cross-scope copy rejected");
        if (source.equals(target)) throw new IllegalArgumentException("source and target must differ");
        transport.copy(source, target, object);
    }

    private void requireBinding(Binding binding) {
        Objects.requireNonNull(binding, "binding");
        if (binding.provider() != provider || blank(binding.bucket())) {
            throw new IllegalArgumentException("binding does not match adapter provider");
        }
        requireHttps(binding.endpoint(), false);
        if (binding.scope().privateAccess() && binding.publicBaseUrl() != null) {
            throw new IllegalArgumentException("private scope cannot expose a public base url");
        }
    }

    private void requireSignedUrl(URI url, Instant expiresAt, Duration maximumTtl) {
        requireHttps(url, true);
        Instant now = clock.instant();
        if (expiresAt == null || !expiresAt.isAfter(now)
                || expiresAt.isAfter(now.plus(maximumTtl))) {
            throw new IllegalStateException("provider returned an invalid signed-url expiry");
        }
    }

    private static void requireObjectInputs(
            String fileName, long sizeBytes, String sha256, String idempotencyKey) {
        if (blank(fileName) || sizeBytes < 0 || blank(idempotencyKey)) {
            throw new IllegalArgumentException("invalid upload contract");
        }
        requireSha256(sha256);
    }

    private static void requireObjectKey(String value) {
        if (blank(value) || value.startsWith("/") || value.contains("\\")
                || value.contains("../") || value.contains("/..") || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("unsafe object key");
        }
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("^[0-9A-Fa-f]{64}$")) {
            throw new IllegalArgumentException("sha256 must contain 64 hexadecimal characters");
        }
    }

    private static void requireHttps(URI uri, boolean queryAllowed) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null
                || (!queryAllowed && uri.getQuery() != null)) {
            throw new IllegalArgumentException("storage url must be a safe HTTPS url");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
            throw new IllegalArgumentException("storage url cannot target a local host");
        }
    }

    private static String encodePath(String objectKey) {
        requireObjectKey(objectKey);
        return objectKey.replace(" ", "%20");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public interface StorageTransport {
        UploadTicket createUpload(Binding binding, UploadIntent intent);
        StoredObject completeUpload(Binding binding, CompleteUpload command);
        ReadTicket createReadUrl(Binding binding, ObjectRef object, Duration ttl);
        void delete(Binding binding, ObjectRef object);
        MigrationPage scan(Binding binding, String cursor, int limit);
        void copy(Binding source, Binding target, ObjectRef object);
    }
}
