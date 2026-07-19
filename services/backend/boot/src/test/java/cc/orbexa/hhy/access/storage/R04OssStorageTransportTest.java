package cc.orbexa.hhy.access.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.access.storage.R04OssStorageTransport.Head;
import cc.orbexa.hhy.access.storage.R04OssStorageTransport.OssFacade;
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

class R04OssStorageTransportTest {
    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SHA = "b".repeat(64);
    private static final Binding BINDING = new Binding(
            Scope.PRIVATE_KYC, Provider.ALIYUN_OSS, "hhy-private-kyc",
            URI.create("https://oss-cn-hangzhou.aliyuncs.com"), null, 92L);
    private static final Settings SETTINGS = new Settings(
            BINDING.endpoint(), "cn-hangzhou", BINDING.bucket(), Duration.ofSeconds(120),
            "vault://oss/access", "vault://oss/secret");

    @Test
    void uploadKeyIsIdempotentAndCredentialsAreCleared() {
        char[] primaryMaterial = "fixture-primary".toCharArray();
        char[] secondaryMaterial = "fixture-secondary".toCharArray();
        FakeFacade facade = new FakeFacade();
        var transport = transport(primaryMaterial, secondaryMaterial, facade);
        UploadIntent intent = new UploadIntent(
                8L, "private_kyc", "face.png", "image/png", 13L, SHA, "same-request");

        UploadTicket first = transport.createUpload(BINDING, intent);
        UploadTicket second = transport.createUpload(BINDING, intent);

        assertEquals(first.objectKey(), second.objectKey());
        assertTrue(first.objectKey().startsWith("private_kyc/8/"));
        assertEquals("image/png", first.headers().get("content-type"));
        assertEquals(SHA, first.headers().get("x-oss-meta-sha256"));
        assertTrue(allZero(primaryMaterial));
        assertTrue(allZero(secondaryMaterial));
    }

    @Test
    void ossV4PresignerBindsContentTypeAndShaMetadataWithoutNetworkAccess() {
        char[] primaryMaterial = "fixture-primary".toCharArray();
        char[] secondaryMaterial = "fixture-secondary".toCharArray();
        var transport = new R04OssStorageTransport(
                binding -> SETTINGS,
                reference -> reference.endsWith("access") ? primaryMaterial : secondaryMaterial,
                CLOCK);

        UploadTicket ticket = transport.createUpload(BINDING, new UploadIntent(
                8L, "private_kyc", "face.png", "image/png", 13L, SHA, "oss-presign-request"));

        assertEquals("https", ticket.uploadUrl().getScheme());
        assertTrue(ticket.uploadUrl().getQuery().toLowerCase().contains("x-oss-signature"));
        assertEquals("image/png", ticket.headers().get("content-type"));
        assertEquals(SHA, ticket.headers().get("x-oss-meta-sha256"));
        assertTrue(allZero(primaryMaterial));
        assertTrue(allZero(secondaryMaterial));
    }

    @Test
    void completionRequiresSizeShaAndEtagToMatchHead() {
        FakeFacade facade = new FakeFacade();
        var transport = transport("a".toCharArray(), "b".toCharArray(), facade);
        String key = "private_kyc/8/object.png";
        facade.head = new Head("\"provider-etag\"", 13L, SHA);
        var command = new CompleteUpload(8L, key, "provider-etag", List.of(), SHA, 13L, "request");

        assertEquals(SHA, transport.completeUpload(BINDING, command).sha256());
        assertThrows(IllegalStateException.class, () -> transport.completeUpload(
                BINDING, new CompleteUpload(8L, key, "different", List.of(), SHA, 13L, "request")));
    }

    @Test
    void resolverRoutesBothProductionPorts() {
        StorageObjectPort r2 = new StorageProviderAdapter(
                Provider.CLOUDFLARE_R2, new NoopTransport(), CLOCK);
        StorageObjectPort oss = new StorageProviderAdapter(
                Provider.ALIYUN_OSS, new NoopTransport(), CLOCK);
        var resolver = new R04StoragePortResolver(r2, oss);

        assertSame(r2, resolver.resolve(Provider.CLOUDFLARE_R2));
        assertSame(oss, resolver.resolve(Provider.ALIYUN_OSS));
    }

    private static R04OssStorageTransport transport(
            char[] access, char[] secret, FakeFacade facade) {
        return new R04OssStorageTransport(
                binding -> SETTINGS,
                reference -> reference.endsWith("access") ? access : secret,
                CLOCK, (settings, accessValue, secretValue) -> facade);
    }

    private static boolean allZero(char[] value) {
        for (char item : value) if (item != '\0') return false;
        return true;
    }

    private static final class FakeFacade implements OssFacade {
        private Head head = new Head("provider-etag", 13L, SHA);

        @Override
        public UploadTicket signPut(
                String bucket, String key, String contentType,
                String sha256, Duration ttl, Instant now) {
            return new UploadTicket(key,
                    URI.create("https://hhy-private-kyc.oss-cn-hangzhou.aliyuncs.com/upload?signature=redacted"),
                    now.plus(ttl), Map.of(
                            "content-type", contentType,
                            "x-oss-meta-sha256", sha256));
        }

        @Override public Head head(String bucket, String key) { return head; }
        @Override public ReadTicket signGet(String bucket, String key, Duration ttl, Instant now) {
            return new ReadTicket(
                    URI.create("https://hhy-private-kyc.oss-cn-hangzhou.aliyuncs.com/read?signature=redacted"),
                    now.plus(ttl));
        }
        @Override public void delete(String bucket, String key) { }
        @Override public MigrationPage scan(String bucket, String cursor, int limit) {
            return new MigrationPage(List.of(), null, true);
        }
        @Override public void copy(String sourceBucket, String targetBucket, String key) { }
        @Override public void close() { }
    }

    private static final class NoopTransport implements StorageProviderAdapter.StorageTransport {
        @Override public UploadTicket createUpload(Binding binding, UploadIntent intent) { return null; }
        @Override public StorageObjectPort.StoredObject completeUpload(
                Binding binding, CompleteUpload command) { return null; }
        @Override public ReadTicket createReadUrl(Binding binding, ObjectRef object, Duration ttl) { return null; }
        @Override public void delete(Binding binding, ObjectRef object) { }
        @Override public MigrationPage scan(Binding binding, String cursor, int limit) { return null; }
        @Override public void copy(Binding source, Binding target, ObjectRef object) { }
    }
}
