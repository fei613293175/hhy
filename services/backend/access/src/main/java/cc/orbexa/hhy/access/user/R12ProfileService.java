package cc.orbexa.hhy.access.user;

import cc.orbexa.hhy.access.user.R12ProfileContracts.ProfilePatchRequest;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class R12ProfileService {
    private static final int PROFILE_COLUMN_LIMIT = 255;
    private static final String RESPONSE_TYPE = "r12-profile-user-v1";

    private final R12ProfileStore profiles;
    private final UserAuthStore idempotency;
    private final UserIdempotencySnapshotCipher snapshots;
    private final UserAuthService users;
    private final UserAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public R12ProfileService(
            R12ProfileStore profiles,
            UserAuthStore idempotency,
            UserIdempotencySnapshotCipher snapshots,
            UserAuthService users,
            UserAuthProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.profiles = profiles;
        this.idempotency = idempotency;
        this.snapshots = snapshots;
        this.users = users;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public UserResource patch(UserPrincipal principal, ProfilePatchRequest request, String key) {
        requireActive(principal);
        requireRequest(request);
        validateProfileFields(request);
        String scope = "r12:profile:" + principal.userId();
        String requestHash = fingerprint(request);
        UserAuthStore.IdempotencyClaim claim = idempotency.claimIdempotency(
                scope, key, requestHash, Instant.now(clock).plus(properties.idempotencyTtl()));
        if (claim.replay()) {
            return replay(claim.row(), scope, key, requestHash);
        }

        R12ProfileStore.ProfileAggregate locked = profiles.lock(principal.userId())
                .orElseThrow(R12ProfileService::notFound);
        if (locked.userVersion() != request.expectedVersion()) throw versionConflict();

        List<String> changedFields = new ArrayList<>(3);
        String nickname = normalizeNickname(request.nickname(), locked.nickname(), changedFields);
        String bio = normalizeBio(request.bio(), locked.bio(), changedFields);
        String avatarUrl = normalizeAvatar(
                principal.userId(), request.avatarMediaId(), locked.avatarUrl(), changedFields);
        if (changedFields.isEmpty()) {
            throw new BusinessException(
                    "COMMON-400-VALIDATION", "至少提交一个个人资料字段", 400, false);
        }

        Instant now = Instant.now(clock);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("actorId", principal.userId());
        event.put("resourceId", Long.toString(principal.userId()));
        event.put("fromVersion", locked.userVersion());
        event.put("toVersion", locked.userVersion() + 1);
        event.put("profileVersion", locked.profileVersion() + 1);
        event.put("changedFields", List.copyOf(changedFields));
        event.put("occurredAt", now.toString());

        R12ProfileStore.UpdateResult updated = profiles.update(
                locked, request.expectedVersion(), nickname, avatarUrl, bio, now,
                changedFields, json(event));
        if (!updated.updated()) throw versionConflict();

        UserResource result = users.self(principal);
        if (result.version() != updated.userVersion()) {
            throw new IllegalStateException("R12 profile response version did not match the committed aggregate");
        }
        completeSnapshot(claim.row().id(), scope, key, requestHash, result);
        return result;
    }

    private String normalizeNickname(String candidate, String current, List<String> changedFields) {
        if (candidate == null) return current;
        String normalized = candidate.strip();
        if (normalized.isEmpty() || codePointLength(normalized) > PROFILE_COLUMN_LIMIT) {
            throw validation("昵称必须为1至255个字符");
        }
        changedFields.add("nickname");
        return normalized;
    }

    private String normalizeBio(String candidate, String current, List<String> changedFields) {
        if (candidate == null) return current;
        String normalized = candidate.strip();
        if (codePointLength(normalized) > PROFILE_COLUMN_LIMIT) {
            throw validation("个人简介不能超过255个字符");
        }
        changedFields.add("bio");
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeAvatar(
            long userId, String mediaId, String current, List<String> changedFields) {
        if (mediaId == null) return current;
        String normalized = mediaId.strip();
        changedFields.add("avatarMediaId");
        if (normalized.isEmpty()) return null;
        final long numericId;
        try {
            numericId = Long.parseLong(normalized);
            if (numericId < 1) throw new NumberFormatException();
        } catch (NumberFormatException failure) {
            throw validation("头像媒体标识无效");
        }
        String url = profiles.resolvePublicAvatar(userId, numericId)
                .orElseThrow(() -> new BusinessException(
                        "COMMON-422-BUSINESS_RULE", "头像媒体不存在、未就绪或不属于当前用户", 422, false));
        if (url.length() > PROFILE_COLUMN_LIMIT) {
            throw new BusinessException(
                    "COMMON-422-BUSINESS_RULE", "头像公开地址超过资料存储上限", 422, false);
        }
        return url;
    }

    private UserResource replay(
            UserAuthStore.IdempotencyRow row, String scope, String key, String requestHash) {
        if (!requestHash.equals(row.requestHash())) {
            throw new BusinessException(
                    "COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
        }
        if (row.responseRef() == null || !RESPONSE_TYPE.equals(row.responseType())
                || row.responsePayloadCiphertext() == null || row.responsePayloadCiphertext().isBlank()) {
            throw new BusinessException(
                    "COMMON-409-VERSION_CONFLICT", "同一请求仍在处理中", 409, true);
        }
        try {
            byte[] plain = snapshots.decrypt(
                    scope, key, requestHash, RESPONSE_TYPE, row.responsePayloadCiphertext());
            return objectMapper.readValue(plain, UserResource.class);
        } catch (UserIdempotencySnapshotCipher.SnapshotIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("R12 profile idempotency snapshot is unavailable", failure);
        }
    }

    private void completeSnapshot(
            long claimId, String scope, String key, String requestHash, UserResource result) {
        try {
            byte[] plain = objectMapper.writeValueAsBytes(result);
            String envelope = snapshots.encrypt(scope, key, requestHash, RESPONSE_TYPE, plain);
            idempotency.completeIdempotencySnapshot(
                    claimId, "user:" + result.id() + ":v" + result.version(), RESPONSE_TYPE, envelope);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to persist R12 profile idempotency snapshot", failure);
        }
    }

    private String fingerprint(ProfilePatchRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, request.nickname());
            update(digest, request.avatarMediaId());
            update(digest, request.bio());
            update(digest, Long.toString(request.expectedVersion()));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to fingerprint R12 profile request", failure);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to serialize R12 profile event", failure);
        }
    }

    private static void requireRequest(ProfilePatchRequest request) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw validation("资料版本无效");
        }
    }

    private static void validateProfileFields(ProfilePatchRequest request) {
        if (request.nickname() != null) {
            String nickname = request.nickname().strip();
            if (nickname.isEmpty() || codePointLength(nickname) > PROFILE_COLUMN_LIMIT) {
                throw validation("昵称必须为1至255个字符");
            }
        }
        if (request.bio() != null && codePointLength(request.bio().strip()) > PROFILE_COLUMN_LIMIT) {
            throw validation("个人简介不能超过255个字符");
        }
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static void requireActive(UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        }
        if (!principal.active()) {
            throw new BusinessException(
                    "COMMON-403-FORBIDDEN", "当前账号状态不允许修改资料", 403, false);
        }
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }

    private static BusinessException versionConflict() {
        return new BusinessException(
                "COMMON-409-VERSION_CONFLICT", "个人资料版本已变化，请刷新后重试", 409, true);
    }

    private static BusinessException notFound() {
        return new BusinessException(
                "COMMON-404-NOT_FOUND", "个人资料不存在", 404, false);
    }
}
