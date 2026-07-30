package cc.orbexa.hhy.access.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.access.storage.MediaContracts.CompleteUploadSessionRequest;
import cc.orbexa.hhy.access.storage.MediaContracts.CreateUploadSessionRequest;
import cc.orbexa.hhy.access.storage.MediaUploadService.MediaObject;
import cc.orbexa.hhy.access.storage.MediaUploadService.ReadTicket;
import cc.orbexa.hhy.access.storage.MediaUploadService.StorageSelection;
import cc.orbexa.hhy.access.storage.MediaUploadService.StoredUpload;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadDraft;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadSession;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadTicket;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaUploadServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final String KEY = "media-test-key-0001";
    private static final String SHA = "a".repeat(64);
    private final FakeStore store = new FakeStore();
    private final FakeStorage storage = new FakeStorage();
    private MediaUploadService service;

    @BeforeEach
    void setUp() {
        service = new MediaUploadService(store, storage,
                (userId, purpose, contentType, sizeBytes) -> {
                    if (!"public_media".equals(purpose)) return null;
                    return new StorageSelection(StorageObjectPort.Scope.PUBLIC_MEDIA, 7L);
                }, new MediaUploadService.Idempotency() {
                    @Override
                    public <T> T execute(
                            String scope, String key, String hash, String type,
                            Class<T> resultType, java.util.function.Supplier<T> action) {
                        return action.get();
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsUploadWithoutInventingPurposeOrScopeDefault() {
        var resource = service.create(principal(11), create("public_media", SHA), KEY);

        assertEquals("1", resource.id());
        assertEquals("public_media", resource.purpose());
        assertEquals("CREATED", resource.status());
        assertEquals("https://upload.example.test/signed", resource.uploadUrl());
        assertNull(resource.readUrl());
        assertEquals(StorageObjectPort.Scope.PUBLIC_MEDIA, store.uploads.get(1L).scope());
        assertEquals(List.of("media.upload.created:1:CREATED"), store.events);
    }

    @Test
    void rejectsUnknownPurposeAndShortShaWithoutProviderSideEffects() {
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.create(principal(11), create("unknown", SHA), KEY));
        assertEquals(422, unknown.httpStatus());
        assertEquals(0, storage.createCalls);

        BusinessException sha = assertThrows(BusinessException.class,
                () -> service.create(principal(11), create("public_media", "a".repeat(40)), KEY));
        assertEquals(422, sha.httpStatus());
        assertEquals(0, storage.createCalls);
    }

    @Test
    void completesOnceAndReturnsExistingMediaOnRepeatedCompletion() {
        var created = service.create(principal(11), create("public_media", SHA), KEY);
        var request = new CompleteUploadSessionRequest("etag-1", List.of());

        var first = service.complete(principal(11), created.id(), request, "media-complete-0001");
        var repeated = service.complete(principal(11), created.id(), request, "media-complete-0002");

        assertEquals("101", first.id());
        assertEquals(first.id(), repeated.id());
        assertEquals("READY", first.status());
        assertEquals(1, storage.completeCalls);
        assertEquals(2, storage.readCalls);
        assertEquals(List.of("media.upload.created:1:CREATED", "media.upload.completed:101:READY"),
                store.events);
    }

    @Test
    void rejectsExpiredAndCrossOwnerCompletion() {
        service.create(principal(11), create("public_media", SHA), KEY);
        store.uploads.compute(1L, (id, value) -> new UploadSession(
                value.id(), value.ownerId(), value.purpose(), value.fileName(), value.contentType(),
                value.sizeBytes(), value.sha256(), value.scope(), value.bindingId(), value.objectKey(),
                value.providerUploadId(), value.status(), NOW.minusSeconds(1), value.mediaId(), value.version()));

        BusinessException expired = assertThrows(BusinessException.class, () -> service.complete(
                principal(11), "1", new CompleteUploadSessionRequest("etag", List.of()),
                "media-complete-0003"));
        assertEquals(422, expired.httpStatus());

        BusinessException hidden = assertThrows(BusinessException.class, () -> service.complete(
                principal(12), "1", new CompleteUploadSessionRequest("etag", List.of()),
                "media-complete-0004"));
        assertEquals(404, hidden.httpStatus());
    }

    @Test
    void deleteIsSoftQueuedRepeatableAndRejectsBoundMedia() {
        service.create(principal(11), create("public_media", SHA), KEY);
        service.complete(principal(11), "1", new CompleteUploadSessionRequest("etag", List.of()),
                "media-complete-0005");

        store.bound = true;
        BusinessException bound = assertThrows(BusinessException.class,
                () -> service.delete(principal(11), "101", "media-delete-key01"));
        assertEquals(422, bound.httpStatus());

        store.bound = false;
        var first = service.delete(principal(11), "101", "media-delete-key02");
        var repeated = service.delete(principal(11), "101", "media-delete-key03");
        assertEquals("DELETE_PENDING", first.status());
        assertEquals(first.version(), repeated.version());
        assertEquals(1, store.deleteTransitions);
        assertEquals("media.delete.requested:101:DELETE_PENDING", store.events.get(2));
    }

    private static CreateUploadSessionRequest create(String purpose, String sha) {
        return new CreateUploadSessionRequest(purpose, "cover.png", "image/png", 32L, sha);
    }

    private static UserPrincipal principal(long id) {
        return new UserPrincipal(id, 1, 0, "jti", "ACTIVE");
    }

    private static final class FakeStorage implements MediaUploadService.MediaStorageGateway {
        int createCalls;
        int completeCalls;
        int readCalls;

        @Override
        public UploadTicket createUpload(StorageSelection selection, MediaUploadService.UploadCommand command) {
            createCalls++;
            return new UploadTicket("public_media/11/cover.png", "provider-upload-1",
                    URI.create("https://upload.example.test/signed"), NOW.plusSeconds(300));
        }

        @Override
        public StoredUpload completeUpload(
                UploadSession session, String etag, List<Map<String, Object>> parts, String key) {
            completeCalls++;
            return new StoredUpload(session.objectKey(), etag, session.sizeBytes(), session.sha256(), NOW);
        }

        @Override
        public ReadTicket createReadUrl(MediaObject media) {
            readCalls++;
            return new ReadTicket(URI.create("https://assets.example.test/cover.png"), Instant.MAX);
        }
    }

    private static final class FakeStore implements MediaUploadService.Store {
        final Map<Long, UploadSession> uploads = new HashMap<>();
        final Map<Long, MediaObject> media = new HashMap<>();
        final java.util.ArrayList<String> events = new java.util.ArrayList<>();
        boolean bound;
        int deleteTransitions;

        @Override
        public UploadSession create(UploadDraft draft) {
            UploadSession result = new UploadSession(1, draft.ownerId(), draft.purpose(), draft.fileName(),
                    draft.contentType(), draft.sizeBytes(), draft.sha256(), draft.scope(), draft.bindingId(),
                    draft.objectKey(), draft.providerUploadId(), "CREATED", draft.expiresAt(), null, 0);
            uploads.put(result.id(), result);
            events.add("media.upload.created:" + result.id() + ":CREATED");
            return result;
        }

        @Override
        public Optional<UploadSession> upload(long id, long ownerId) {
            return Optional.ofNullable(uploads.get(id)).filter(value -> value.ownerId() == ownerId);
        }

        @Override
        public Optional<MediaObject> media(long id, long ownerId) {
            return Optional.ofNullable(media.get(id)).filter(value -> value.ownerId() == ownerId);
        }

        @Override
        public MediaObject complete(UploadSession session, StoredUpload stored, Instant completedAt) {
            MediaObject result = new MediaObject(101, session.ownerId(), session.purpose(),
                    session.contentType(), stored.sizeBytes(), stored.sha256(), session.scope(),
                    session.bindingId(), stored.objectKey(), "READY", 0, completedAt);
            media.put(result.id(), result);
            uploads.put(session.id(), new UploadSession(
                    session.id(), session.ownerId(), session.purpose(), session.fileName(),
                    session.contentType(), session.sizeBytes(), session.sha256(), session.scope(),
                    session.bindingId(), session.objectKey(), session.providerUploadId(), "COMPLETED",
                    session.expiresAt(), result.id(), session.version() + 1));
            events.add("media.upload.completed:" + result.id() + ":READY");
            return result;
        }

        @Override
        public boolean bound(long mediaId) {
            return bound;
        }

        @Override
        public MediaObject markDeletePending(long mediaId, long ownerId, Instant now) {
            MediaObject current = media(mediaId, ownerId).orElseThrow();
            MediaObject changed = new MediaObject(current.id(), current.ownerId(), current.purpose(),
                    current.contentType(), current.sizeBytes(), current.sha256(), current.scope(),
                    current.bindingId(), current.objectKey(), "DELETE_PENDING", current.version() + 1, now);
            media.put(mediaId, changed);
            deleteTransitions++;
            events.add("media.delete.requested:" + mediaId + ":DELETE_PENDING");
            return changed;
        }

    }
}
