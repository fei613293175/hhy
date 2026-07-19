package cc.orbexa.hhy.access.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.admin.AdminIdempotencySnapshotCipher;
import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.admin.AdminSecurityProperties;
import cc.orbexa.hhy.access.admin.AdminSecurityStore;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.FreezeRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.MediaAccessRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.ReviewRequest;
import cc.orbexa.hhy.access.identity.IdentityService.Session;
import cc.orbexa.hhy.access.storage.MediaUploadService.MediaObject;
import cc.orbexa.hhy.access.storage.MediaUploadService.ReadTicket;
import cc.orbexa.hhy.access.storage.R04MediaStorageGateway;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminIdentityServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T18:00:00Z");
    private static final String KEY = "identity-admin-key-0001";

    @Mock private AdminIdentityStore store;
    @Mock private AdminIdentityPolicy policy;
    @Mock private R04MediaStorageGateway storage;
    @Mock private AdminSecurityStore security;
    private AdminIdentityService service;

    @BeforeEach
    void setUp() {
        var properties = new AdminSecurityProperties(
                "jwt-secret-12345678901234567890123456789012",
                "mfa-secret-12345678901234567890123456789012",
                "idem-secret-1234567890123456789012345678901",
                "test", Duration.ofHours(1), Duration.ofMinutes(5), Duration.ofMinutes(10),
                8, 72, 5, 900);
        var snapshots = new AdminIdempotencySnapshotCipher(properties, "v1", "");
        service = new AdminIdentityService(
                store, policy, storage, security, snapshots,
                new ObjectMapper().findAndRegisterModules(), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(security.claimIdempotency(anyString(), eq(KEY), anyString(), any()))
                .thenReturn(new AdminSecurityStore.IdempotencyClaim(
                        new AdminSecurityStore.IdempotencyRow(91L, "ignored", null, null, null), false));
    }

    @Test
    void mapsLatestIdentityPageWithoutSensitiveCiphertext() {
        when(store.page(1, 20, "PENDING", "42", "s.created_at DESC,s.id DESC"))
                .thenReturn(new AdminIdentityStore.IdentityPage(List.of(session(8L, 42L, 3L)), 1L));

        var result = service.list(1, 20, null, "PENDING", "42", "createdAt:desc");

        assertEquals("8", result.items().getFirst().id());
        assertEquals("42", result.items().getFirst().userId());
        assertEquals("1", result.page().total());
    }

    @Test
    void staleReviewVersionIsRejectedBeforeMutation() {
        when(store.sessionForUpdate(8L)).thenReturn(Optional.of(session(8L, 42L, 4L)));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.review(
                principal(7L), "8", new ReviewRequest("APPROVE", "材料一致", 3L, List.of()),
                KEY, "request-1", "127.0.0.1"));

        assertEquals("COMMON-409-VERSION_CONFLICT", failure.code());
        verify(store, never()).review(any(), anyString(), anyString(), eq(7L),
                anyString(), any(), any());
    }

    @Test
    void reviewWritesImmutableHistorySensitiveAuditAndIdempotentSnapshot() {
        Session before = session(8L, 42L, 3L);
        Session after = new Session(8L, 42L, "VERIFIED", "ALIYUN_MARKET_FACE", null,
                null, NOW.plusSeconds(120), 4L, 1);
        when(store.sessionForUpdate(8L)).thenReturn(Optional.of(before));
        when(store.evidenceBelongsToSession(8L, List.of(71L))).thenReturn(true);
        when(store.review(before, "APPROVE", "材料一致", 7L, KEY, List.of(71L), NOW))
                .thenReturn(after);

        var result = service.review(principal(7L), "8",
                new ReviewRequest("APPROVE", "材料一致", 3L, List.of("71")),
                KEY, "request-2", "127.0.0.1");

        assertEquals("VERIFIED", result.session().status());
        verify(store).profileAccess(7L, 42L, "REVIEW", "材料一致",
                "request-2", "127.0.0.1", null);
        verify(security).operationLog(eq(7L), eq("IDENTITY_MANUAL_APPROVE"),
                eq("IDENTITY"), eq(42L), eq(null), anyString(), eq("127.0.0.1"));
        verify(security).completeIdempotencySnapshot(
                eq(91L), eq("8"), eq("r05.admin-identity-command.v1"), anyString());
    }

    @Test
    void freezeRequiresSecondDistinctAdministrator() {
        Session current = session(8L, 42L, 3L);
        when(store.latestByUserForUpdate(42L)).thenReturn(Optional.of(current));
        when(store.pendingFreezeApprovalForUpdate(42L))
                .thenReturn(Optional.of(new AdminIdentityStore.FreezeApproval(17L, 7L, 0L)));

        var result = service.freeze(principal(7L), "42", new FreezeRequest("风险处置", 3L),
                KEY, "request-3", "127.0.0.1");

        assertEquals("PENDING_APPROVAL", result.command().status());
        verify(store, never()).freeze(any(), anyString(), eq(7L), anyString(), any());
    }

    @Test
    void mediaPreviewHonorsActivatedCeilingAndAuditsEverySensitiveRead() {
        MediaObject media = new MediaObject(
                71L, 42L, "identity", "image/jpeg", 128L, "a".repeat(64),
                Scope.PRIVATE_KYC, 5L, "identity/42/front.jpg", "READY", 2L, NOW);
        when(store.latestMedia(42L)).thenReturn(Optional.of(media));
        when(policy.previewTtl()).thenReturn(Duration.ofSeconds(30));
        when(storage.createReadUrl(media, Duration.ofSeconds(15))).thenReturn(
                new ReadTicket(URI.create("https://objects.orbexa.cc/preview"), NOW.plusSeconds(15)));
        when(store.createMediaAccessToken(71L, "admin:7", NOW.plusSeconds(15))).thenReturn(88L);

        var result = service.mediaAccess(principal(7L), "42",
                new MediaAccessRequest("人工复核原图", 15L), KEY,
                "request-4", "127.0.0.1");

        assertEquals("PREVIEW_READY", result.command().status());
        assertEquals("88", result.command().resourceId());
        verify(store).profileAccess(7L, 42L, "VIEW_MEDIA", "人工复核原图",
                "request-4", "127.0.0.1", 71L);
    }

    @Test
    void secondAdministratorApprovesAndFreezesIdentity() {
        Session current = session(8L, 42L, 3L);
        when(store.latestByUserForUpdate(42L)).thenReturn(Optional.of(current));
        when(store.pendingFreezeApprovalForUpdate(42L))
                .thenReturn(Optional.of(new AdminIdentityStore.FreezeApproval(17L, 7L, 0L)));
        when(store.approveFreeze(17L, 0L, 8L)).thenReturn(true);

        var result = service.freeze(principal(8L), "42", new FreezeRequest("复核通过", 3L),
                KEY, "request-5", "127.0.0.2");

        assertEquals("IDENTITY_FROZEN", result.command().status());
        verify(store).freeze(current, "复核通过", 8L, KEY, NOW);
        verify(store).profileAccess(8L, 42L, "FREEZE", "复核通过",
                "request-5", "127.0.0.2", null);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadIsRejected() {
        when(security.claimIdempotency(anyString(), eq(KEY), anyString(), any()))
                .thenReturn(new AdminSecurityStore.IdempotencyClaim(
                        new AdminSecurityStore.IdempotencyRow(91L, "different", "42", null, null), true));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.freeze(
                principal(7L), "42", new FreezeRequest("风险处置", 3L),
                KEY, "request-6", "127.0.0.1"));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", failure.code());
        verify(store, never()).latestByUserForUpdate(42L);
    }

    private static Session session(long id, long userId, long version) {
        return new Session(id, userId, "MANUAL_REVIEW", "ALIYUN_MARKET_FACE", null,
                null, NOW.plusSeconds(120), version, 1);
    }

    private static AdminPrincipal principal(long adminId) {
        return new AdminPrincipal(adminId, 1L, 1L, "jti", "admin-" + adminId,
                Set.of("identity.read", "identity.media.view", "identity.review", "identity.freeze"));
    }
}
