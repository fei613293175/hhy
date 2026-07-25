package cc.orbexa.hhy.access.user;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class R12ProfileStore {
    private final JdbcTemplate jdbc;

    public R12ProfileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProfileAggregate> lock(long userId) {
        return jdbc.query("""
                SELECT u.id,u.version AS user_version,p.version AS profile_version,
                       p.nickname,p.avatar,p.bio
                FROM hhy.users u JOIN hhy.user_profiles p ON p.user_id=u.id
                WHERE u.id=? FOR UPDATE OF u,p
                """, (rs, row) -> new ProfileAggregate(
                rs.getLong("id"), rs.getLong("user_version"), rs.getLong("profile_version"),
                rs.getString("nickname"), rs.getString("avatar"), rs.getString("bio")), userId)
                .stream().findFirst();
    }

    public Optional<String> resolvePublicAvatar(long userId, long mediaId) {
        return jdbc.query("""
                SELECT rtrim(binding.public_domain,'/') || '/' || ltrim(media.object_key,'/') AS public_url
                FROM hhy.media_objects media
                JOIN hhy.storage_scope_bindings binding ON binding.id=media.storage_binding_id
                WHERE media.id=? AND media.owner_id=? AND media.status='READY'
                  AND media.deleted_at IS NULL AND media.storage_scope='public_media'
                  AND media.visibility='PUBLIC' AND media.mime LIKE 'image/%'
                  AND binding.status='ACTIVE' AND binding.scope_code='public_media'
                  AND binding.public_domain IS NOT NULL AND btrim(binding.public_domain)<>''
                  AND media.object_key IS NOT NULL AND btrim(media.object_key)<>''
                """, (rs, row) -> rs.getString("public_url"), mediaId, userId)
                .stream().findFirst();
    }

    public UpdateResult update(
            ProfileAggregate locked, long expectedUserVersion,
            String nickname, String avatarUrl, String bio, Instant now,
            List<String> changedFields, String eventPayload) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        int userUpdated = jdbc.update("""
                UPDATE hhy.users SET version=version+1,updated_at=?
                WHERE id=? AND version=?
                """, timestamp, locked.userId(), expectedUserVersion);
        if (userUpdated != 1) return UpdateResult.versionConflict();

        int profileUpdated = jdbc.update("""
                UPDATE hhy.user_profiles
                SET nickname=?,avatar=?,bio=?,version=version+1,updated_at=?
                WHERE user_id=? AND version=?
                """, nickname, avatarUrl, bio, timestamp, locked.userId(), locked.profileVersion());
        if (profileUpdated != 1) {
            throw new IllegalStateException("R12 profile changed after its aggregate lock");
        }

        long nextUserVersion = expectedUserVersion + 1;
        long nextProfileVersion = locked.profileVersion() + 1;
        jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?,'USER',?,'USER_PROFILE_UPDATED',1,
                        '{"source":"r12-account-api"}'::jsonb,?::jsonb)
                """, Long.toString(locked.userId()), UUID.randomUUID().toString(), eventPayload);
        return UpdateResult.updated(nextUserVersion, nextProfileVersion, changedFields);
    }

    public record ProfileAggregate(
            long userId, long userVersion, long profileVersion,
            String nickname, String avatarUrl, String bio) { }

    public record UpdateResult(
            boolean updated, long userVersion, long profileVersion, List<String> changedFields) {
        static UpdateResult versionConflict() {
            return new UpdateResult(false, -1, -1, List.of());
        }

        static UpdateResult updated(long userVersion, long profileVersion, List<String> changedFields) {
            return new UpdateResult(true, userVersion, profileVersion, List.copyOf(changedFields));
        }
    }
}
