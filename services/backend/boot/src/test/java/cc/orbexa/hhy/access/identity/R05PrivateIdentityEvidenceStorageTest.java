package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.EvidenceCommand;
import cc.orbexa.hhy.access.storage.MediaUploadService.MediaObject;
import cc.orbexa.hhy.access.storage.MediaUploadService.StorageSelection;
import cc.orbexa.hhy.access.storage.R04MediaPostgresStore;
import cc.orbexa.hhy.access.identity.R05PrivateIdentityEvidenceStorage.MediaRegistrar;
import cc.orbexa.hhy.access.identity.R05PrivateIdentityEvidenceStorage.PurposeResolver;
import cc.orbexa.hhy.access.identity.R05PrivateIdentityEvidenceStorage.StorageGateway;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import cc.orbexa.hhy.access.storage.StorageObjectPort.StoredObject;
import cc.orbexa.hhy.access.storage.StorageObjectPort.UploadTicket;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class R05PrivateIdentityEvidenceStorageTest {
    private static final Instant NOW = Instant.parse("2026-07-20T05:00:00Z");
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
    private final PurposeResolver purposes = mock(PurposeResolver.class);
    private final StorageGateway storage = mock(StorageGateway.class);
    private final MediaRegistrar media = mock(MediaRegistrar.class);
    private final AtomicReference<Map<String, String>> uploadedHeaders = new AtomicReference<>();
    private R05PrivateIdentityEvidenceStorage evidence;

    @BeforeEach
    void setUp() {
        evidence = new R05PrivateIdentityEvidenceStorage(
                purposes, storage, media, (url, headers, body, timeout) -> {
                    uploadedHeaders.set(headers);
                    return new R05PrivateIdentityEvidenceStorage.UploadResponse(200, "etag-1");
                }, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void uploadsWithSignedHeadersAndRegistersOnlyPrivateReadyMedia() throws Exception {
        EvidenceCommand command = command();
        StorageSelection selection = new StorageSelection(Scope.PRIVATE_KYC, 7);
        UploadTicket ticket = new UploadTicket(
                "private_kyc/11/face.jpg", URI.create("https://upload.example.test/signed"),
                NOW.plusSeconds(300), Map.of("x-amz-checksum-sha256", "checksum-token"));
        StoredObject stored = new StoredObject(
                ticket.objectKey(), "etag-1", JPEG.length, command.sha256(), NOW);
        MediaObject registered = new MediaObject(
                91, 11, "identity.liveness.7", "image/jpeg", JPEG.length,
                command.sha256(), Scope.PRIVATE_KYC, 7, ticket.objectKey(),
                "READY", 0, NOW);
        when(purposes.resolve(11, "private_kyc", "image/jpeg", JPEG.length)).thenReturn(selection);
        when(storage.create(eq(selection), any())).thenReturn(ticket);
        when(storage.complete(eq(selection), any(), eq(ticket.objectKey()),
                eq("etag-1"), eq("identity-face-7-complete"))).thenReturn(stored);
        when(media.register(
                11, 7, "image/jpeg", selection, stored, NOW)).thenReturn(registered);

        var result = evidence.store(command);

        assertEquals(91, result.mediaObjectId());
        assertEquals(command.sha256(), result.sha256());
        assertEquals("checksum-token", uploadedHeaders.get().get("x-amz-checksum-sha256"));
        assertEquals("image/jpeg", uploadedHeaders.get().get("content-type"));
    }

    @Test
    void rejectsCredentialBearingSignedHeadersBeforeNetworkUpload() throws Exception {
        EvidenceCommand command = command();
        StorageSelection selection = new StorageSelection(Scope.PRIVATE_KYC, 7);
        when(purposes.resolve(anyLong(), eq("private_kyc"), eq("image/jpeg"), anyLong()))
                .thenReturn(selection);
        when(storage.create(eq(selection), any())).thenReturn(new UploadTicket(
                "private_kyc/11/face.jpg", URI.create("https://upload.example.test/signed"),
                NOW.plusSeconds(300), Map.of("Authorization", "secret")));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> evidence.store(command));

        assertEquals("实名认证服务暂时不可用", failure.getMessage());
        verify(storage, never()).complete(any(), any(), any(), any(), any());
        verify(media, never()).register(
                anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void rejectsPayloadWhoseDigestDoesNotMatchBeforeStorageSelection() {
        EvidenceCommand tampered = new EvidenceCommand(
                11, 7, "LIVENESS_PHOTO", "image/jpeg", JPEG,
                "a".repeat(64), "identity-face-7");

        BusinessException failure = assertThrows(BusinessException.class,
                () -> evidence.store(tampered));

        assertEquals("实名认证服务暂时不可用", failure.getMessage());
        verify(purposes, never()).resolve(anyLong(), any(), any(), anyLong());
    }

    @Test
    void postgresRegistrationIsPrivateReadyIdempotentAndRejectsObjectKeyConflict() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(
                url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var store = new R04MediaPostgresStore(
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ObjectMapper().findAndRegisterModules());
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Long ownerId = jdbc.queryForObject(
                "INSERT INTO hhy.users(phone,status) VALUES (?,'ACTIVE') RETURNING id",
                Long.class, "17" + suffix.substring(0, 9));
        Long configId = jdbc.queryForObject("""
                INSERT INTO hhy.provider_config_versions(
                  provider_code,version_no,status,environment,values_json,
                  secret_refs_json,remark)
                VALUES ('CLOUDFLARE_R2',?,'ACTIVE','TEST','{}'::jsonb,'{}'::jsonb,'test')
                RETURNING id
                """, Long.class, "identity-" + suffix);
        Long bindingId = jdbc.queryForObject("""
                INSERT INTO hhy.storage_scope_bindings(
                  scope_code,provider_code,config_version_id,bucket,public_domain,status)
                VALUES ('private_kyc','CLOUDFLARE_R2',?, ?,NULL,'ACTIVE')
                RETURNING id
                """, Long.class, configId, "identity-" + suffix);
        String objectKey = "private_kyc/" + ownerId + "/" + suffix + ".jpg";
        String sha = "b".repeat(64);
        var selection = new StorageSelection(Scope.PRIVATE_KYC, bindingId);
        var stored = new StoredObject(objectKey, "etag", 123, sha, NOW);
        MediaObject first = store.registerPrivateIdentityEvidence(
                ownerId, 71, "image/jpeg", selection, stored, NOW);
        MediaObject repeated = store.registerPrivateIdentityEvidence(
                ownerId, 71, "image/jpeg", selection, stored, NOW.plusSeconds(1));

        assertEquals(first.id(), repeated.id());
        assertEquals("READY", first.status());
        assertEquals(Scope.PRIVATE_KYC, first.scope());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.media_objects
                WHERE storage_binding_id=? AND object_key=? AND visibility='PRIVATE'
                """, Integer.class, bindingId, objectKey));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.outbox_events
                WHERE event_type='media.private.identity.stored.v1'
                  AND aggregate_id=?
                """, Integer.class, Long.toString(first.id())));
        var conflicting = new StoredObject(
                objectKey, "etag", 123, "c".repeat(64), NOW.plusSeconds(2));
        assertThrows(IllegalStateException.class, () ->
                store.registerPrivateIdentityEvidence(
                        ownerId, 71, "image/jpeg", selection, conflicting,
                        NOW.plusSeconds(2)));
        assertTrue(first.purpose().startsWith("identity.liveness."));
    }

    private static EvidenceCommand command() throws Exception {
        String sha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(JPEG));
        return new EvidenceCommand(
                11, 7, "LIVENESS_PHOTO", "image/jpeg", JPEG,
                sha, "identity-face-7");
    }
}
