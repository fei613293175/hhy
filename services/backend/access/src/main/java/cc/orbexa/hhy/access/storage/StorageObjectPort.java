package cc.orbexa.hhy.access.storage;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Provider-neutral object-storage boundary. Secrets are resolved behind the adapter. */
public interface StorageObjectPort {
    UploadTicket createUpload(Binding binding, UploadIntent intent);

    StoredObject completeUpload(Binding binding, CompleteUpload command);

    ReadTicket createReadUrl(Binding binding, ObjectRef object, Duration ttl);

    void delete(Binding binding, ObjectRef object);

    MigrationPage scan(Binding binding, String cursor, int limit);

    void copy(Binding source, Binding target, ObjectRef object);

    enum Provider { CLOUDFLARE_R2, ALIYUN_OSS }

    enum Scope {
        PUBLIC_MEDIA(false), PRIVATE_KYC(true), PRIVATE_CHAT(true),
        AUDIT_EVIDENCE(true), APK_RELEASE(true), BACKUP(true);

        private final boolean privateAccess;

        Scope(boolean privateAccess) {
            this.privateAccess = privateAccess;
        }

        public boolean privateAccess() {
            return privateAccess;
        }
    }

    record Binding(Scope scope, Provider provider, String bucket, URI endpoint, URI publicBaseUrl) { }

    record UploadIntent(
            long ownerId, String purpose, String fileName, String contentType,
            long sizeBytes, String sha256, String idempotencyKey) { }

    record UploadTicket(
            String objectKey, URI uploadUrl, Instant expiresAt, Map<String, String> headers) {
        public UploadTicket {
            headers = Map.copyOf(headers);
        }
    }

    record CompleteUpload(
            long ownerId, String objectKey, String etag, List<CompletedPart> parts,
            String expectedSha256, long expectedSizeBytes, String idempotencyKey) {
        public CompleteUpload {
            parts = List.copyOf(parts);
        }
    }

    record CompletedPart(int number, String etag) { }

    record StoredObject(
            String objectKey, String etag, long sizeBytes, String sha256, Instant storedAt) { }

    record ObjectRef(long ownerId, String objectKey, String sha256) { }

    record ReadTicket(URI readUrl, Instant expiresAt) { }

    record MigrationPage(List<ObjectRef> objects, String nextCursor, boolean complete) {
        public MigrationPage {
            objects = List.copyOf(objects);
        }
    }
}
