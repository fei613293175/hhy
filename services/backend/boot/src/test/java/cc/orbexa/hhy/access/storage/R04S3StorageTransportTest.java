package cc.orbexa.hhy.access.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.storage.R04S3StorageTransport.Head;
import cc.orbexa.hhy.access.storage.R04S3StorageTransport.S3Facade;
import cc.orbexa.hhy.access.storage.R04StorageProviderSettings.Settings;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.CompleteUpload;
import cc.orbexa.hhy.access.storage.StorageObjectPort.MigrationPage;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ObjectRef;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ReadTicket;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadIntent;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadTicket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class R04S3StorageTransportTest {
    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SHA = "a".repeat(64);
    private static final Binding BINDING = new Binding(
            Scope.PRIVATE_KYC, Provider.CLOUDFLARE_R2, "hhy-private-kyc",
            URI.create("https://account.r2.cloudflarestorage.com"), null, 91L);
    private static final Settings SETTINGS = new Settings(
            BINDING.endpoint(), "auto", BINDING.bucket(), Duration.ofSeconds(120),
            "vault://r2/access", "vault://r2/secret");

    @Test
    void uploadKeyIsIdempotentAndCredentialsAreCleared() {
        char[] primaryMaterial = "fixture-primary".toCharArray();
        char[] secondaryMaterial = "fixture-secondary".toCharArray();
        FakeFacade facade = new FakeFacade();
        var transport = transport(primaryMaterial, secondaryMaterial, facade);
        UploadIntent intent = new UploadIntent(
                7L, "private_kyc", "face.jpg", "image/jpeg", 12L, SHA, "same-request");

        UploadTicket first = transport.createUpload(BINDING, intent);
        UploadTicket second = transport.createUpload(BINDING, intent);

        assertEquals(first.objectKey(), second.objectKey());
        assertTrue(first.objectKey().startsWith("private_kyc/7/"));
        assertEquals("image/jpeg", first.headers().get("content-type"));
        assertEquals(SHA, first.headers().get("x-amz-meta-sha256"));
        assertTrue(allZero(primaryMaterial));
        assertTrue(allZero(secondaryMaterial));
    }

    @Test
    void awsPresignerBindsContentTypeAndShaMetadataWithoutNetworkAccess() {
        char[] primaryMaterial = "fixture-primary".toCharArray();
        char[] secondaryMaterial = "fixture-secondary".toCharArray();
        var transport = new R04S3StorageTransport(
                binding -> SETTINGS,
                reference -> reference.endsWith("access") ? primaryMaterial : secondaryMaterial,
                CLOCK);

        UploadTicket ticket = transport.createUpload(BINDING, new UploadIntent(
                7L, "private_kyc", "face.jpg", "image/jpeg", 12L, SHA, "aws-presign-request"));

        assertEquals("https", ticket.uploadUrl().getScheme());
        assertEquals("account.r2.cloudflarestorage.com", ticket.uploadUrl().getHost());
        assertTrue(ticket.uploadUrl().getQuery().contains("X-Amz-Signature="));
        assertEquals("image/jpeg", ticket.headers().get("content-type"));
        assertEquals(SHA, ticket.headers().get("x-amz-meta-sha256"));
        assertTrue(allZero(primaryMaterial));
        assertTrue(allZero(secondaryMaterial));
    }

    @Test
    void completionRequiresSizeShaAndEtagToMatchHead() {
        FakeFacade facade = new FakeFacade();
        var transport = transport("a".toCharArray(), "b".toCharArray(), facade);
        String key = "private_kyc/7/object.jpg";
        facade.head = new Head("\"provider-etag\"", 12L, SHA);
        var command = new CompleteUpload(7L, key, "provider-etag", List.of(), SHA, 12L, "request");

        assertEquals(SHA, transport.completeUpload(BINDING, command).sha256());
        assertThrows(IllegalStateException.class, () -> transport.completeUpload(
                BINDING, new CompleteUpload(7L, key, "different", List.of(), SHA, 12L, "request")));
    }

    @Test
    void privateReadIsSigned() {
        FakeFacade facade = new FakeFacade();
        var transport = transport("a".toCharArray(), "b".toCharArray(), facade);
        ReadTicket ticket = transport.createReadUrl(
                BINDING, new ObjectRef(7L, "private_kyc/7/object.jpg", SHA), Duration.ofSeconds(60));
        assertEquals(NOW.plusSeconds(60), ticket.expiresAt());

    }

    private static R04S3StorageTransport transport(
            char[] access, char[] secret, FakeFacade facade) {
        return new R04S3StorageTransport(
                binding -> SETTINGS,
                reference -> reference.endsWith("access") ? access : secret,
                CLOCK, (settings, accessValue, secretValue) -> facade);
    }

    private static boolean allZero(char[] value) {
        for (char item : value) if (item != '\0') return false;
        return true;
    }

    private static final class FakeFacade implements S3Facade {
        private Head head = new Head("provider-etag", 12L, SHA);

        @Override
        public UploadTicket signPut(
                String bucket, String key, String contentType, long sizeBytes,
                String sha256, Duration ttl, Instant now) {
            return new UploadTicket(key,
                    URI.create("https://account.r2.cloudflarestorage.com/upload?signature=redacted"),
                    now.plus(ttl), Map.of(
                            "content-type", contentType,
                            "x-amz-meta-sha256", sha256));
        }

        @Override public Head head(String bucket, String key) { return head; }

        @Override
        public ReadTicket signGet(String bucket, String key, Duration ttl, Instant now) {
            return new ReadTicket(
                    URI.create("https://account.r2.cloudflarestorage.com/read?signature=redacted"),
                    now.plus(ttl));
        }

        @Override public void delete(String bucket, String key) { }
        @Override public MigrationPage scan(String bucket, String cursor, int limit) {
            return new MigrationPage(List.of(), null, true);
        }
        @Override public void copy(String sourceBucket, String targetBucket, String key) { }
        @Override public void close() { }
    }
}
