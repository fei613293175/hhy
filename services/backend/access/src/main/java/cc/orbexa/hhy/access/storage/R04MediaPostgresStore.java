package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.storage.MediaUploadService.MediaObject;
import cc.orbexa.hhy.access.storage.MediaUploadService.StoredUpload;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadDraft;
import cc.orbexa.hhy.access.storage.MediaUploadService.UploadSession;
import cc.orbexa.hhy.access.storage.MediaUploadService.StorageSelection;
import cc.orbexa.hhy.access.storage.StorageObjectPort.StoredObject;
import cc.orbexa.hhy.access.storage.StorageObjectPort.Scope;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL aggregate store; every media state change and Outbox fact is atomic. */
@Component
public final class R04MediaPostgresStore implements MediaUploadService.Store {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public R04MediaPostgresStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    @Override
    public UploadSession create(UploadDraft draft) {
        return required(transactions.execute(status -> {
            UploadSession created = jdbc.queryForObject("""
                    INSERT INTO hhy.upload_sessions(
                      user_id,scene,status,expires_at,purpose,file_name,content_type,size_bytes,sha256,
                      storage_scope,storage_binding_id,object_key,provider_upload_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    RETURNING id,user_id,purpose,file_name,content_type,size_bytes,sha256,storage_scope,
                              storage_binding_id,object_key,provider_upload_id,status,expires_at,media_id,version
                    """, this::upload, draft.ownerId(), draft.scope().name(), "CREATED", draft.expiresAt(),
                    draft.purpose(), draft.fileName(), draft.contentType(), draft.sizeBytes(), draft.sha256(),
                    code(draft.scope()), draft.bindingId(), draft.objectKey(), draft.providerUploadId());
            outbox(draft.ownerId(), "media.upload.created.v1", "MEDIA_UPLOAD_SESSION",
                    Long.toString(created.id()), "CREATED", Instant.now());
            return created;
        }));
    }

    @Override
    public Optional<UploadSession> upload(long id, long ownerId) {
        return jdbc.query("""
                SELECT id,user_id,purpose,file_name,content_type,size_bytes,sha256,storage_scope,
                       storage_binding_id,object_key,provider_upload_id,status,expires_at,media_id,version
                FROM hhy.upload_sessions WHERE id=? AND user_id=?
                """, this::upload, id, ownerId).stream().findFirst();
    }

    @Override
    public Optional<MediaObject> media(long id, long ownerId) {
        return jdbc.query("""
                SELECT id,owner_id,purpose,mime,size,sha256,storage_scope,storage_binding_id,
                       object_key,status,version,updated_at
                FROM hhy.media_objects WHERE id=? AND owner_id=?
                """, this::media, id, ownerId).stream().findFirst();
    }

    /** Registers a provider-confirmed private identity object without creating a public read URL. */
    public MediaObject registerPrivateIdentityEvidence(
            long ownerId, long sessionId, String contentType,
            StorageSelection selection, StoredObject stored, Instant now) {
        if (selection == null || selection.scope() != Scope.PRIVATE_KYC
                || stored == null || stored.objectKey() == null || stored.objectKey().isBlank()
                || stored.sha256() == null || !stored.sha256().matches("^[0-9A-Fa-f]{64}$")
                || stored.sizeBytes() < 1) {
            throw new IllegalArgumentException("invalid private identity evidence");
        }
        try {
            return required(transactions.execute(transaction -> {
                Optional<MediaObject> existing = mediaByObjectKey(
                        selection.bindingId(), stored.objectKey());
                if (existing.isPresent()) {
                    requireSameEvidence(existing.orElseThrow(), ownerId, stored);
                    return existing.orElseThrow();
                }
                MediaObject created = jdbc.queryForObject("""
                        INSERT INTO hhy.media_objects(
                          owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,
                          storage_scope,storage_binding_id,status,version,created_at,updated_at)
                        SELECT ?,binding.bucket,?,?,?,?, 'PRIVATE',?,
                               'private_kyc',binding.id,'READY',0,?,?
                        FROM hhy.storage_scope_bindings binding
                        WHERE binding.id=? AND binding.scope_code='private_kyc'
                          AND binding.status='ACTIVE'
                        RETURNING id,owner_id,purpose,mime,size,sha256,storage_scope,
                                  storage_binding_id,object_key,status,version,updated_at
                        """, this::media, ownerId, stored.objectKey(), contentType,
                        stored.sizeBytes(), stored.sha256(), "identity.liveness." + sessionId,
                        Timestamp.from(now), Timestamp.from(now), selection.bindingId());
                if (created == null) throw new IllegalStateException("private storage binding is inactive");
                outbox(ownerId, "media.private.identity.stored.v1", "MEDIA_OBJECT",
                        Long.toString(created.id()), "READY", now);
                return created;
            }));
        } catch (DataIntegrityViolationException duplicate) {
            MediaObject existing = mediaByObjectKey(selection.bindingId(), stored.objectKey())
                    .orElseThrow(() -> duplicate);
            requireSameEvidence(existing, ownerId, stored);
            return existing;
        }
    }

    @Override
    public MediaObject complete(UploadSession session, StoredUpload stored, Instant completedAt) {
        return required(transactions.execute(status -> {
            UploadSession locked = jdbc.query("""
                    SELECT id,user_id,purpose,file_name,content_type,size_bytes,sha256,storage_scope,
                           storage_binding_id,object_key,provider_upload_id,status,expires_at,media_id,version
                    FROM hhy.upload_sessions WHERE id=? AND user_id=? FOR UPDATE
                    """, this::upload, session.id(), session.ownerId()).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("upload session disappeared"));
            if ("COMPLETED".equals(locked.status())) {
                return media(locked.mediaId(), locked.ownerId())
                        .orElseThrow(() -> new IllegalStateException("completed media disappeared"));
            }
            if (!java.util.List.of("CREATED", "UPLOADING", "COMPLETING").contains(locked.status())) {
                throw new IllegalStateException("upload session changed concurrently");
            }
            MediaObject created = jdbc.queryForObject("""
                    INSERT INTO hhy.media_objects(
                      owner_id,bucket,object_key,mime,size,sha256,visibility,purpose,
                      storage_scope,storage_binding_id,status,version)
                    SELECT ?,binding.bucket,?,?,?,?,?,?,?,?,?,0
                    FROM hhy.storage_scope_bindings binding WHERE binding.id=?
                    RETURNING id,owner_id,purpose,mime,size,sha256,storage_scope,storage_binding_id,
                              object_key,status,version,updated_at
                    """, this::media, locked.ownerId(), stored.objectKey(), locked.contentType(),
                    stored.sizeBytes(), stored.sha256(), locked.scope() == Scope.PUBLIC_MEDIA ? "PUBLIC" : "PRIVATE",
                    locked.purpose(), code(locked.scope()), locked.bindingId(), "READY", locked.bindingId());
            int changed = jdbc.update("""
                    UPDATE hhy.upload_sessions
                    SET status='COMPLETED',provider_etag=?,media_id=?,completed_at=?,version=version+1
                    WHERE id=? AND user_id=? AND version=?
                    """, stored.etag(), created.id(), completedAt, locked.id(), locked.ownerId(), locked.version());
            if (changed != 1) throw new IllegalStateException("upload completion changed concurrently");
            outbox(locked.ownerId(), "media.upload.completed.v1", "MEDIA_OBJECT",
                    Long.toString(created.id()), "READY", completedAt);
            return created;
        }));
    }

    @Override
    public boolean bound(long mediaId) {
        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM hhy.identity_media WHERE media_object_id=?)
                    OR EXISTS(SELECT 1 FROM hhy.content_media WHERE media_id=?)
                    OR EXISTS(SELECT 1 FROM hhy.chat_message_attachments WHERE media_id=?)
                    OR EXISTS(SELECT 1 FROM hhy.referral_materials WHERE media_id=?)
                    OR EXISTS(SELECT 1 FROM hhy.ticket_attachments WHERE media_id=?)
                    OR EXISTS(SELECT 1 FROM hhy.banners WHERE media_id=?)
                """, Boolean.class, mediaId, mediaId, mediaId, mediaId, mediaId, mediaId);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public MediaObject markDeletePending(long mediaId, long ownerId, Instant now) {
        return required(transactions.execute(status -> {
            MediaObject locked = jdbc.query("""
                    SELECT id,owner_id,purpose,mime,size,sha256,storage_scope,storage_binding_id,
                           object_key,status,version,updated_at
                    FROM hhy.media_objects WHERE id=? AND owner_id=? FOR UPDATE
                    """, this::media, mediaId, ownerId).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("media disappeared"));
            if (!"READY".equals(locked.status())) return locked;
            if (bound(mediaId)) {
                throw new BusinessException(
                        "COMMON-422-BUSINESS_RULE", "媒体已被业务使用，不能删除", 422, false);
            }
            int changed = jdbc.update("""
                    UPDATE hhy.media_objects
                    SET status='DELETE_PENDING',version=version+1,updated_at=?
                    WHERE id=? AND owner_id=? AND version=? AND status='READY'
                    """, now, mediaId, ownerId, locked.version());
            if (changed != 1) throw new IllegalStateException("media deletion changed concurrently");
            outbox(ownerId, "media.delete.requested.v1", "MEDIA_OBJECT",
                    Long.toString(mediaId), "DELETE_PENDING", now);
            return media(mediaId, ownerId).orElseThrow();
        }));
    }

    private void outbox(
            long actorId, String eventType, String aggregateType,
            String aggregateId, String state, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?,?,?,?,1,?::jsonb,?::jsonb)
                """, aggregateId, aggregateType, UUID.randomUUID().toString(), eventType,
                json(Map.of("source", "media-api")), json(Map.of(
                        "actorId", actorId, "resourceId", aggregateId,
                        "status", state, "occurredAt", occurredAt.toString())));
    }

    private UploadSession upload(ResultSet rs, int row) throws SQLException {
        long mediaId = rs.getLong("media_id");
        Long optionalMediaId = rs.wasNull() ? null : mediaId;
        return new UploadSession(rs.getLong("id"), rs.getLong("user_id"), rs.getString("purpose"),
                rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
                rs.getString("sha256"), scope(rs.getString("storage_scope")),
                rs.getLong("storage_binding_id"), rs.getString("object_key"),
                rs.getString("provider_upload_id"), rs.getString("status"),
                rs.getTimestamp("expires_at").toInstant(), optionalMediaId,
                rs.getLong("version"));
    }

    private MediaObject media(ResultSet rs, int row) throws SQLException {
        return new MediaObject(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("purpose"),
                rs.getString("mime"), rs.getLong("size"), rs.getString("sha256"),
                scope(rs.getString("storage_scope")), rs.getLong("storage_binding_id"),
                rs.getString("object_key"), rs.getString("status"), rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private Optional<MediaObject> mediaByObjectKey(long bindingId, String objectKey) {
        return jdbc.query("""
                SELECT id,owner_id,purpose,mime,size,sha256,storage_scope,storage_binding_id,
                       object_key,status,version,updated_at
                FROM hhy.media_objects
                WHERE storage_binding_id=? AND object_key=?
                """, this::media, bindingId, objectKey).stream().findFirst();
    }

    private static void requireSameEvidence(
            MediaObject existing, long ownerId, StoredObject stored) {
        if (existing.ownerId() != ownerId || existing.scope() != Scope.PRIVATE_KYC
                || !"READY".equals(existing.status())
                || existing.sizeBytes() != stored.sizeBytes()
                || !existing.sha256().equalsIgnoreCase(stored.sha256())) {
            throw new IllegalStateException("storage object key belongs to different media");
        }
    }

    private static Scope scope(String value) {
        return Scope.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }

    private static String code(Scope scope) {
        return scope.name().toLowerCase(java.util.Locale.ROOT);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("media event serialization failed", failure);
        }
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("media transaction returned no result");
        return value;
    }
}
