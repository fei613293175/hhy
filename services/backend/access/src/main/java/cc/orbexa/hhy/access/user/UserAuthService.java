package cc.orbexa.hhy.access.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.DeviceSummaryResource;
import cc.orbexa.hhy.access.user.UserAuthContracts.RefreshRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserSessionResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuthService {
    private static final String RESPONSE_TYPE = "user-auth-session-v1";
    private static final String SCOPE_PREFIX = "user-auth-refresh:";

    private final UserAuthStore repository;
    private final UserTokenService tokens;
    private final UserIdempotencySnapshotCipher snapshots;
    private final UserAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UserAuthService(
            UserAuthStore repository,
            UserTokenService tokens,
            UserIdempotencySnapshotCipher snapshots,
            UserAuthProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.tokens = tokens;
        this.snapshots = snapshots;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public UserSessionResource refresh(
            RefreshRequest request, String headerRefreshToken, String idempotencyKey) {
        if (!tokens.sameSecret(headerRefreshToken, request.refreshToken())) {
            throw unauthenticated();
        }

        Instant now = Instant.now(clock);
        String oldRefreshHash = tokens.refreshHash(request.refreshToken());
        String scope = SCOPE_PREFIX + oldRefreshHash;
        String requestHash = tokens.intentHash(
                "authPostAuthRefresh", request.refreshToken(), request.deviceId());
        UserAuthStore.IdempotencyClaim claim = repository.claimIdempotency(
                scope, idempotencyKey, requestHash, now.plus(properties.idempotencyTtl()));
        if (claim.replay()) {
            return replay(claim.row(), scope, idempotencyKey, requestHash);
        }

        UserAuthStore.UserSessionRow session = repository.findSessionForUpdate(oldRefreshHash)
                .orElseThrow(UserAuthService::sessionRevoked);
        if (!"ACTIVE".equals(session.userStatus())) {
            throw new BusinessException(
                    "AUTH-423-ACCOUNT_RESTRICTED", "账号已冻结或受限", 423, false);
        }
        if (session.expiresAt() == null || !session.expiresAt().isAfter(now)) {
            throw sessionRevoked();
        }
        if (session.deviceId() == null
                || !tokens.sameSecret(Long.toString(session.deviceId()), request.deviceId())) {
            throw sessionRevoked();
        }

        long nextVersion = session.version() + 1;
        String accessJti = UUID.randomUUID().toString();
        String newRefreshToken = tokens.newRefreshToken();
        String newRefreshHash = tokens.refreshHash(newRefreshToken);
        Instant accessExpiresAt = now.plus(properties.accessTtl());
        Instant refreshExpiresAt = now.plus(properties.refreshTtl());
        String accessToken = tokens.issueAccess(
                session.userId(), session.id(), nextVersion, accessJti, accessExpiresAt);

        if (!repository.rotateSession(
                session.id(), session.version(), oldRefreshHash,
                accessJti, newRefreshHash, refreshExpiresAt)) {
            throw new BusinessException(
                    "COMMON-409-VERSION_CONFLICT", "会话已被其他请求更新", 409, false);
        }
        repository.touchDevice(session.deviceId(), session.userId(), now);

        var device = new DeviceSummaryResource(
                Long.toString(session.deviceId()), session.deviceName(), "ANDROID",
                null, null, now, null);
        var result = new UserSessionResource(
                accessToken, newRefreshToken, accessExpiresAt,
                Long.toString(session.userId()), Long.toString(session.id()),
                device, List.of());
        return complete(claim.row(), scope, idempotencyKey, requestHash, result);
    }

    private UserSessionResource replay(
            UserAuthStore.IdempotencyRow row,
            String scope,
            String idempotencyKey,
            String requestHash) {
        validateReplay(row, requestHash);
        try {
            byte[] plaintext = snapshots.decrypt(
                    scope, idempotencyKey, requestHash,
                    RESPONSE_TYPE, row.responsePayloadCiphertext());
            return objectMapper.readValue(plaintext, UserSessionResource.class);
        } catch (UserIdempotencySnapshotCipher.SnapshotIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("User idempotency snapshot is unavailable", exception);
        }
    }

    private UserSessionResource complete(
            UserAuthStore.IdempotencyRow row,
            String scope,
            String idempotencyKey,
            String requestHash,
            UserSessionResource result) {
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(result);
            String ciphertext = snapshots.encrypt(
                    scope, idempotencyKey, requestHash, RESPONSE_TYPE, plaintext);
            repository.completeIdempotencySnapshot(
                    row.id(), "user-session:" + result.sessionId(), RESPONSE_TYPE, ciphertext);
            return result;
        } catch (UserIdempotencySnapshotCipher.SnapshotIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist user idempotency snapshot", exception);
        }
    }

    private static void validateReplay(UserAuthStore.IdempotencyRow row, String requestHash) {
        if (!requestHash.equals(row.requestHash())) {
            throw new BusinessException(
                    "COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
        }
        if (row.responseRef() == null
                || !RESPONSE_TYPE.equals(row.responseType())
                || row.responsePayloadCiphertext() == null
                || row.responsePayloadCiphertext().isBlank()) {
            throw new BusinessException(
                    "COMMON-409-VERSION_CONFLICT", "同一请求仍在处理中", 409, true);
        }
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(
                "COMMON-401-UNAUTHENTICATED", "刷新令牌无效", 401, false);
    }

    private static BusinessException sessionRevoked() {
        return new BusinessException(
                "AUTH-401-SESSION_REVOKED", "登录会话已失效", 401, false);
    }
}
