package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.identity.IdentityService.LivenessTicket;
import cc.orbexa.hhy.access.identity.IdentityService.IdentityConsent;
import cc.orbexa.hhy.access.identity.IdentityService.Session;
import cc.orbexa.hhy.access.identity.IdentityService.SessionDraft;
import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.ProcessingContext;
import cc.orbexa.hhy.access.identity.IdentityProviderResultCoordinator.ProviderCompletion;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL identity aggregate store; state mutation and Outbox fact are atomic. */
@Component
public final class R05IdentityPostgresStore
        implements IdentityService.Store, IdentityProviderResultCoordinator.Store {
    private static final String SESSION_SELECT = """
            SELECT s.id,s.user_id,s.state,s.status,s.provider,s.expires_at,s.version,
                   s.attempt_no,s.failure_code,
                   (SELECT p.response_cipher FROM hhy.identity_provider_requests p
                    WHERE p.session_id=s.id AND p.request_type='LIVENESS_TOKEN'
                      AND p.status='SUCCEEDED' AND p.response_cipher IS NOT NULL
                    ORDER BY p.created_at DESC,p.id DESC LIMIT 1) liveness_url_cipher
            FROM hhy.identity_verification_sessions s
            """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final IdentitySensitiveCipher sensitiveData;

    public R05IdentityPostgresStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            @Qualifier("adminSecurityObjectMapper") ObjectMapper objectMapper,
            IdentitySensitiveCipher sensitiveData) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.sensitiveData = sensitiveData;
    }

    @Override
    public Optional<IdentityConsent> currentConsent() {
        return jdbc.query("""
                SELECT version_row.id, version_row.content
                FROM hhy.agreements agreement
                JOIN hhy.agreement_versions version_row
                  ON version_row.id=agreement.current_version_id
                WHERE agreement.code='IDENTITY_VERIFICATION'
                  AND version_row.effective_at IS NOT NULL
                  AND version_row.effective_at<=clock_timestamp()
                  AND length(trim(coalesce(version_row.content,'')))>0
                """, (rs, row) -> new IdentityConsent(
                Long.toString(rs.getLong("id")), rs.getString("content")))
                .stream().findFirst();
    }

    @Override
    public Optional<String> profileStatus(long userId) {
        return jdbc.query("""
                SELECT status FROM hhy.identity_profiles WHERE user_id=?
                """, (rs, row) -> rs.getString("status"), userId).stream().findFirst();
    }

    @Override
    public Optional<Session> active(long userId) {
        return jdbc.query(SESSION_SELECT + """
                WHERE user_id=?
                  AND status IN ('SESSION_CREATED','LIVENESS_PENDING','PROVIDER_PROCESSING','MANUAL_REVIEW')
                ORDER BY created_at DESC LIMIT 1
                """, this::session, userId).stream().findFirst();
    }

    @Override
    public long countCreatedSince(long userId, Instant since) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.identity_verification_sessions
                WHERE user_id=? AND created_at>=?
                """, Long.class, userId, time(since));
        return count == null ? 0 : count;
    }

    @Override
    public Session create(SessionDraft draft) {
        try {
            return required(transactions.execute(status -> {
                int profile = jdbc.update("""
                        INSERT INTO hhy.identity_profiles(
                          user_id,name_cipher,id_no_cipher,id_hash,status,version)
                        VALUES (?,?,?,?, 'SESSION_CREATED',0)
                        ON CONFLICT (user_id) DO UPDATE SET
                          name_cipher=EXCLUDED.name_cipher,id_no_cipher=EXCLUDED.id_no_cipher,
                          id_hash=EXCLUDED.id_hash,status='SESSION_CREATED',verified_at=NULL,
                          version=hhy.identity_profiles.version+1,updated_at=?
                        WHERE hhy.identity_profiles.status<>'VERIFIED'
                        """, draft.userId(), draft.identity().nameCipher(),
                        draft.identity().idNumberCipher(), draft.identity().idHash(), time(draft.createdAt()));
                if (profile != 1) throw business("当前账号已完成实名认证");

                Session created = jdbc.queryForObject("""
                        INSERT INTO hhy.identity_verification_sessions(
                          user_id,state,status,provider,expires_at,idempotency_key,attempt_no,
                          last_event,consent_version,version,created_at,updated_at)
                        VALUES (?,?,'SESSION_CREATED',?,?,?,?, 'SESSION_CREATED',?,0,?,?)
                        RETURNING id,user_id,state,status,provider,expires_at,version,attempt_no,failure_code
                        """, this::sessionWithoutLiveness, draft.userId(), UUID.randomUUID().toString(),
                        draft.provider(), time(draft.expiresAt()), draft.idempotencyKey(), 1,
                        draft.consentVersion(), time(draft.createdAt()), time(draft.createdAt()));
                outbox(draft.userId(), "identity.session.created.v1", "IDENTITY_SESSION",
                        Long.toString(created.id()), created.status(), draft.createdAt());
                return created;
            }));
        } catch (DataIntegrityViolationException conflict) {
            if (!isUniqueViolation(conflict)) throw conflict;
            throw new BusinessException(
                    "COMMON-422-BUSINESS_RULE", "该实名信息已绑定其他账号或已有认证流程", 422, false);
        }
    }

    @Override
    public Optional<Session> find(long id, long userId) {
        return jdbc.query(SESSION_SELECT + " WHERE id=? AND user_id=?",
                this::session, id, userId).stream().findFirst();
    }

    @Override
    public Optional<ProcessingContext> context(long sessionId, long userId) {
        Optional<Session> current = find(sessionId, userId);
        if (current.isEmpty()) return Optional.empty();
        return jdbc.query("""
                SELECT request.provider_order_no,profile.name_cipher,profile.id_no_cipher
                FROM hhy.identity_profiles profile
                JOIN hhy.identity_verification_sessions session
                  ON session.user_id=profile.user_id
                JOIN LATERAL (
                  SELECT provider_order_no
                  FROM hhy.identity_provider_requests
                  WHERE session_id=session.id AND request_type='LIVENESS_TOKEN'
                    AND status='SUCCEEDED' AND provider_order_no IS NOT NULL
                  ORDER BY created_at DESC,id DESC LIMIT 1
                ) request ON true
                WHERE session.id=? AND session.user_id=?
                """, (rs, row) -> new ProcessingContext(
                        current.orElseThrow(), rs.getString("provider_order_no"),
                        sensitiveData.decrypt(userId, "name", rs.getString("name_cipher")),
                        sensitiveData.decrypt(userId, "id-number", rs.getString("id_no_cipher"))),
                sessionId, userId).stream().findFirst();
    }

    @Override
    public Session recordPending(ProcessingContext context, byte[] raw, Instant now) {
        return required(transactions.execute(transaction -> {
            Session locked = lock(context.session().id(), context.session().userId());
            if (!java.util.List.of("LIVENESS_PENDING", "PROVIDER_PROCESSING").contains(locked.status())) {
                return locked;
            }
            if (locked.version() != context.session().version()) {
                throw conflict("认证状态已变化，请刷新后重试");
            }
            if (!providerRequestExists(locked.id(), "LIVENESS_RESULT", "liveness-result-pending")) {
                insertProviderResult(locked, "LIVENESS_RESULT", "liveness-result-pending",
                        "PROVIDER_PROCESSING", "LIVENESS_RESULT_PENDING", raw, now);
            }
            if ("LIVENESS_PENDING".equals(locked.status())) {
                int changed = jdbc.update("""
                        UPDATE hhy.identity_verification_sessions
                        SET status='PROVIDER_PROCESSING',last_event='LIVENESS_RESULT_PENDING',
                            version=version+1,updated_at=?
                        WHERE id=? AND user_id=? AND version=? AND status='LIVENESS_PENDING'
                        """, time(now), locked.id(), locked.userId(), locked.version());
                requireOne(changed);
            }
            return find(locked.id(), locked.userId()).orElseThrow();
        }));
    }

    @Override
    public ProcessingContext recordLivenessPassed(
            ProcessingContext context, byte[] raw, Instant now) {
        Session processing = required(transactions.execute(transaction -> {
            Session locked = lock(context.session().id(), context.session().userId());
            if (!java.util.List.of("LIVENESS_PENDING", "PROVIDER_PROCESSING").contains(locked.status())) {
                return locked;
            }
            if (locked.version() != context.session().version()) {
                throw conflict("认证状态已变化，请刷新后重试");
            }
            if (!providerRequestExists(locked.id(), "LIVENESS_RESULT", "liveness-result-passed")) {
                insertProviderResult(locked, "LIVENESS_RESULT", "liveness-result-passed",
                        "PROVIDER_PROCESSING", "LIVENESS_RESULT_PASSED", raw, now);
            }
            if ("LIVENESS_PENDING".equals(locked.status())) {
                int changed = jdbc.update("""
                        UPDATE hhy.identity_verification_sessions
                        SET status='PROVIDER_PROCESSING',last_event='LIVENESS_RESULT_PASSED',
                            version=version+1,updated_at=?
                        WHERE id=? AND user_id=? AND version=? AND status='LIVENESS_PENDING'
                        """, time(now), locked.id(), locked.userId(), locked.version());
                requireOne(changed);
            }
            return find(locked.id(), locked.userId()).orElseThrow();
        }));
        return new ProcessingContext(
                processing, context.providerOrderNo(), context.realName(), context.idNumber());
    }

    @Override
    public Session complete(ProcessingContext context, ProviderCompletion completion, Instant now) {
        return required(transactions.execute(transaction -> {
            Session locked = lock(context.session().id(), context.session().userId());
            if (!java.util.List.of("LIVENESS_PENDING", "PROVIDER_PROCESSING").contains(locked.status())) {
                return locked;
            }
            if (locked.version() != context.session().version()) {
                throw conflict("认证状态已变化，请刷新后重试");
            }
            if (!java.util.List.of("VERIFIED", "MANUAL_REVIEW", "REJECTED")
                    .contains(completion.status())) {
                throw new IllegalArgumentException("unsupported provider completion status");
            }
            insertProviderResult(locked, "LIVENESS_RESULT", "liveness-result-final",
                    completion.status(), "LIVENESS_RESULT_COMPLETED", completion.livenessRaw(), now);
            if (completion.comparisonRaw().length > 0) {
                insertProviderResult(locked, "FACE_COMPARE", "face-compare-final",
                        completion.status(), "FACE_COMPARE_COMPLETED", completion.comparisonRaw(), now);
            }
            if (completion.mediaObjectId() != null) {
                int media = jdbc.update("""
                        INSERT INTO hhy.identity_media(
                          session_id,media_object_id,media_type,storage_scope,purpose,version)
                        VALUES (?,?,'LIVENESS_PHOTO','private_kyc','identity_liveness',0)
                        ON CONFLICT (session_id,media_type) WHERE media_type IS NOT NULL DO NOTHING
                        """, locked.id(), completion.mediaObjectId());
                if (media == 0) {
                    Long existing = jdbc.queryForObject("""
                            SELECT media_object_id FROM hhy.identity_media
                            WHERE session_id=? AND media_type='LIVENESS_PHOTO'
                            """, Long.class, locked.id());
                    if (!completion.mediaObjectId().equals(existing)) {
                        throw conflict("认证状态已变化，请刷新后重试");
                    }
                }
            }
            boolean terminal = java.util.List.of("VERIFIED", "REJECTED").contains(completion.status());
            String event = switch (completion.status()) {
                case "VERIFIED" -> "IDENTITY_VERIFIED";
                case "MANUAL_REVIEW" -> "IDENTITY_MANUAL_REVIEW";
                case "REJECTED" -> "IDENTITY_REJECTED";
                default -> throw new IllegalStateException();
            };
            int changed = jdbc.update("""
                    UPDATE hhy.identity_verification_sessions
                    SET status=?,failure_code=?,last_event=?,completed_at=?,
                        version=version+1,updated_at=?
                    WHERE id=? AND user_id=? AND version=?
                      AND status IN ('LIVENESS_PENDING','PROVIDER_PROCESSING')
                    """, completion.status(), completion.failureCode(), event,
                    terminal ? time(now) : null, time(now), locked.id(), locked.userId(), locked.version());
            requireOne(changed);
            int profile = jdbc.update("""
                    UPDATE hhy.identity_profiles
                    SET status=?,verified_at=CASE WHEN ?='VERIFIED' THEN ? ELSE NULL END,
                        version=version+1,updated_at=?
                    WHERE user_id=? AND status<>'VERIFIED'
                    """, completion.status(), completion.status(), time(now), time(now), locked.userId());
            requireOne(profile);
            outbox(locked.userId(), "identity.provider.completed.v1", "IDENTITY_SESSION",
                    Long.toString(locked.id()), completion.status(), now);
            return find(locked.id(), locked.userId()).orElseThrow();
        }));
    }

    @Override
    public Session attachLiveness(
            Session current, LivenessTicket ticket, String idempotencyKey, Instant now) {
        try {
            return required(transactions.execute(status -> {
                Session locked = lock(current.id(), current.userId());
                if (!java.util.List.of("SESSION_CREATED", "LIVENESS_PENDING").contains(locked.status())) {
                    throw conflict("认证状态已变化，请刷新后重试");
                }
                int changed = jdbc.update("""
                        UPDATE hhy.identity_verification_sessions
                        SET status='LIVENESS_PENDING',last_event='LIVENESS_TOKEN_CREATED',
                            failure_code=NULL,version=version+1,updated_at=?
                        WHERE id=? AND user_id=? AND version=?
                        """, time(now), locked.id(), locked.userId(), locked.version());
                requireOne(changed);
                jdbc.update("""
                        INSERT INTO hhy.identity_provider_requests(
                          session_id,request_type,provider_order_no,idempotency_key,request_hash,
                          response_cipher,status,attempt_no,from_status,to_status,event,completed_at,
                          version,created_at,updated_at)
                        VALUES (?,'LIVENESS_TOKEN',?,?,?,?, 'SUCCEEDED',1,?,
                                'LIVENESS_PENDING','LIVENESS_TOKEN_CREATED',?,0,?,?)
                        """, locked.id(), ticket.providerOrderNo(),
                        idempotencyKey, digest(ticket.url().toString()),
                        sensitiveData.encrypt(locked.userId(), "liveness-url", ticket.url().toString()),
                        locked.status(), time(now), time(now), time(now));
                outbox(locked.userId(), "identity.liveness.created.v1", "IDENTITY_SESSION",
                        Long.toString(locked.id()), "LIVENESS_PENDING", now);
                Session updated = find(locked.id(), locked.userId()).orElseThrow();
                return new Session(updated.id(), updated.userId(), updated.state(),
                        updated.status(), updated.provider(),
                        ticket.url(), updated.failureCode(), updated.expiresAt(),
                        updated.version(), updated.attemptNo());
            }));
        } catch (DataIntegrityViolationException duplicateProviderOrder) {
            if (!isUniqueViolation(duplicateProviderOrder)) throw duplicateProviderOrder;
            throw conflict("活体检测请求已受理，请查询认证状态");
        }
    }

    @Override
    public Session expire(long id, long userId, long expectedVersion, Instant now) {
        return required(transactions.execute(status -> {
            int changed = jdbc.update("""
                    UPDATE hhy.identity_verification_sessions
                    SET status='EXPIRED',completed_at=?,failure_code='SESSION_EXPIRED',
                        last_event='SESSION_EXPIRED',version=version+1,updated_at=?
                    WHERE id=? AND user_id=? AND version=?
                      AND status IN ('SESSION_CREATED','LIVENESS_PENDING','PROVIDER_PROCESSING','MANUAL_REVIEW')
                    """, time(now), time(now), id, userId, expectedVersion);
            if (changed == 1) {
                outbox(userId, "identity.session.expired.v1", "IDENTITY_SESSION",
                        Long.toString(id), "EXPIRED", now);
            }
            return find(id, userId).orElseThrow(R05IdentityPostgresStore::notFound);
        }));
    }

    @Override
    public Session retry(
            Session previous, String provider, String idempotencyKey,
            Instant expiresAt, Instant now) {
        try {
            return required(transactions.execute(status -> {
                Session locked = lock(previous.id(), previous.userId());
                if (!java.util.List.of("REJECTED", "EXPIRED").contains(locked.status())) {
                    throw conflict("认证状态已变化，请刷新后重试");
                }
                Session retried = jdbc.queryForObject("""
                        INSERT INTO hhy.identity_verification_sessions(
                          user_id,state,status,provider,expires_at,idempotency_key,attempt_no,
                          retry_of_session_id,last_event,consent_version,version,created_at,updated_at)
                        SELECT user_id,?,'SESSION_CREATED',?,?,?,attempt_no+1,id,
                               'RETRY_CREATED',consent_version,0,?,?
                        FROM hhy.identity_verification_sessions WHERE id=? AND user_id=?
                        RETURNING id,user_id,state,status,provider,expires_at,version,attempt_no,failure_code
                        """, this::sessionWithoutLiveness, UUID.randomUUID().toString(), provider, time(expiresAt),
                        idempotencyKey, time(now), time(now), locked.id(), locked.userId());
                outbox(locked.userId(), "identity.session.retried.v1", "IDENTITY_SESSION",
                        Long.toString(retried.id()), retried.status(), now);
                return retried;
            }));
        } catch (DataIntegrityViolationException duplicateActive) {
            if (!isUniqueViolation(duplicateActive)) throw duplicateActive;
            throw conflict("已有进行中的实名认证，请先完成当前流程");
        }
    }

    private Session lock(long id, long userId) {
        return jdbc.query(SESSION_SELECT + " WHERE id=? AND user_id=? FOR UPDATE",
                this::session, id, userId).stream().findFirst()
                .orElseThrow(R05IdentityPostgresStore::notFound);
    }

    private Session session(ResultSet rs, int row) throws SQLException {
        String protectedUrl = rs.getString("liveness_url_cipher");
        URI livenessUrl = protectedUrl == null ? null : URI.create(
                sensitiveData.decrypt(rs.getLong("user_id"), "liveness-url", protectedUrl));
        return session(rs, livenessUrl);
    }

    private Session sessionWithoutLiveness(ResultSet rs, int row) throws SQLException {
        return session(rs, null);
    }

    private static Session session(ResultSet rs, URI livenessUrl) throws SQLException {
        return new Session(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("state"), rs.getString("status"),
                rs.getString("provider"), livenessUrl, rs.getString("failure_code"),
                rs.getTimestamp("expires_at").toInstant(), rs.getLong("version"),
                rs.getInt("attempt_no"));
    }

    private void outbox(
            long actorId, String eventType, String aggregateType,
            String aggregateId, String state, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?,?,?,?,1,?::jsonb,?::jsonb)
                """, aggregateId, aggregateType, UUID.randomUUID().toString(), eventType,
                json(Map.of("source", "identity-api")), json(Map.of(
                        "actorId", actorId, "resourceId", aggregateId,
                        "status", state, "occurredAt", occurredAt.toString())));
    }

    private boolean providerRequestExists(long sessionId, String type, String idempotencyKey) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM hhy.identity_provider_requests
                  WHERE session_id=? AND request_type=? AND idempotency_key=?)
                """, Boolean.class, sessionId, type, idempotencyKey);
        return Boolean.TRUE.equals(found);
    }

    private void insertProviderResult(
            Session session, String requestType, String idempotencyKey,
            String toStatus, String event, byte[] raw, Instant now) {
        String encoded = Base64.getEncoder().encodeToString(raw == null ? new byte[0] : raw);
        jdbc.update("""
                INSERT INTO hhy.identity_provider_requests(
                  session_id,request_type,provider_order_no,idempotency_key,request_hash,
                  response_cipher,status,attempt_no,from_status,to_status,event,completed_at,
                  version,created_at,updated_at)
                VALUES (?,?,NULL,?,?,?,'SUCCEEDED',1,?,?,?, ?,0,?,?)
                """, session.id(), requestType, idempotencyKey, digest(encoded),
                sensitiveData.encrypt(session.userId(), "provider-" + requestType.toLowerCase(), encoded),
                session.status(), toStatus, event, time(now), time(now), time(now));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Identity event serialization failed", failure);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Identity provider hash unavailable", failure);
        }
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("Identity transaction returned no result");
        return value;
    }

    private static void requireOne(int changed) {
        if (changed != 1) throw conflict("认证状态已变化，请刷新后重试");
    }

    private static BusinessException business(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", message, 409, false);
    }

    private static BusinessException notFound() {
        return new BusinessException("COMMON-404-NOT_FOUND", "资源不存在或不可见", 404, false);
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
