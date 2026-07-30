package cc.orbexa.hhy.access.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.storage.StorageObjectPort.Binding;
import cc.orbexa.hhy.access.storage.StorageObjectPort.CompleteUpload;
import cc.orbexa.hhy.access.storage.StorageObjectPort.MigrationPage;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ObjectRef;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Provider;
import cc.orbexa.hhy.access.storage.StorageObjectPort.ReadTicket;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import cc.orbexa.hhy.access.storage.StorageObjectPort.StoredObject;
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

class StorageProviderAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SHA = "a".repeat(64);

    @Test
    void privateReadUrlIsShortLivedAndNeverUsesPublicBaseUrl() {
        var adapter = adapter(Provider.CLOUDFLARE_R2);
        Binding binding = binding(Scope.PRIVATE_CHAT, Provider.CLOUDFLARE_R2, null);
        ReadTicket result = adapter.createReadUrl(
                binding, new ObjectRef(7L, "private_chat/7/image.png", SHA), Duration.ofSeconds(120));

        assertEquals("https", result.readUrl().getScheme());
        assertEquals(NOW.plusSeconds(120), result.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> adapter.createReadUrl(
                binding, new ObjectRef(7L, "private_chat/7/image.png", SHA), Duration.ofMinutes(6)));
    }

    @Test
    void adapterRejectsProviderMismatchAndCrossScopeMigration() {
        var adapter = adapter(Provider.CLOUDFLARE_R2);
        UploadIntent intent = new UploadIntent(7L, "CHAT", "a.png", "image/png", 10, SHA, "idem-1234567890123456");
        assertThrows(IllegalArgumentException.class, () -> adapter.createUpload(
                binding(Scope.PUBLIC_MEDIA, Provider.ALIYUN_OSS, URI.create("https://cdn.example.test/")), intent));
        assertThrows(IllegalArgumentException.class, () -> adapter.copy(
                binding(Scope.PUBLIC_MEDIA, Provider.CLOUDFLARE_R2, URI.create("https://cdn.example.test/")),
                binding(Scope.BACKUP, Provider.CLOUDFLARE_R2, null),
                new ObjectRef(7L, "public_media/7/a.png", SHA)));
    }

    @Test
    void targetAdapterAcceptsSameScopeCrossProviderMigration() {
        var adapter = adapter(Provider.ALIYUN_OSS);
        adapter.copy(
                binding(Scope.BACKUP, Provider.CLOUDFLARE_R2, null),
                binding(Scope.BACKUP, Provider.ALIYUN_OSS, null),
                new ObjectRef(7L, "backup/7/a.bin", SHA));
    }

    @Test
    void completionMustMatchExpectedSizeAndSha() {
        var adapter = adapter(Provider.CLOUDFLARE_R2);
        Binding binding = binding(Scope.PUBLIC_MEDIA, Provider.CLOUDFLARE_R2, URI.create("https://cdn.example.test/"));
        CompleteUpload command = new CompleteUpload(
                7L, "public_media/7/a.png", "etag", List.of(), SHA, 10, "idem-1234567890123456");
        assertEquals(SHA, adapter.completeUpload(binding, command).sha256());
    }

    @Test
    void incompleteMigrationPageRequiresResumeCursor() {
        StorageProviderAdapter.StorageTransport broken = new StubTransport() {
            @Override
            public MigrationPage scan(Binding binding, String cursor, int limit) {
                return new MigrationPage(List.of(), null, false);
            }
        };
        var adapter = new StorageProviderAdapter(Provider.ALIYUN_OSS, broken, CLOCK);
        assertThrows(IllegalStateException.class, () -> adapter.scan(
                binding(Scope.BACKUP, Provider.ALIYUN_OSS, null), null, 100));
    }

    private static StorageProviderAdapter adapter(Provider provider) {
        return new StorageProviderAdapter(provider, new StubTransport(), CLOCK);
    }

    private static Binding binding(Scope scope, Provider provider, URI publicBase) {
        return new Binding(scope, provider, "hhy-" + scope.name().toLowerCase(),
                URI.create("https://objects.example.test"), publicBase);
    }

    private static class StubTransport implements StorageProviderAdapter.StorageTransport {
        @Override
        public UploadTicket createUpload(Binding binding, UploadIntent intent) {
            return new UploadTicket("public_media/7/a.png",
                    URI.create("https://objects.example.test/upload?signature=redacted"),
                    NOW.plusSeconds(300), Map.of());
        }

        @Override
        public StoredObject completeUpload(Binding binding, CompleteUpload command) {
            return new StoredObject(command.objectKey(), command.etag(),
                    command.expectedSizeBytes(), command.expectedSha256(), NOW);
        }

        @Override
        public ReadTicket createReadUrl(Binding binding, ObjectRef object, Duration ttl) {
            return new ReadTicket(URI.create("https://objects.example.test/read?signature=redacted"), NOW.plus(ttl));
        }

        @Override public void delete(Binding binding, ObjectRef object) { }

        @Override
        public MigrationPage scan(Binding binding, String cursor, int limit) {
            return new MigrationPage(List.of(), null, true);
        }

        @Override public void copy(Binding source, Binding target, ObjectRef object) { }
    }
}
