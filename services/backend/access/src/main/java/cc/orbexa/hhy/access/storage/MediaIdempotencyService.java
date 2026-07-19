package cc.orbexa.hhy.access.storage;

import cc.orbexa.hhy.access.user.UserAuthStore;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/** Durable encrypted first-response replay for authenticated media commands. */
public final class MediaIdempotencyService implements MediaUploadService.Idempotency {
    private static final Duration TTL = Duration.ofHours(24);
    private final UserAuthStore store;
    private final UserIdempotencySnapshotCipher snapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MediaIdempotencyService(
            UserAuthStore store,
            UserIdempotencySnapshotCipher snapshots,
            ObjectMapper objectMapper,
            Clock clock) {
        this.store = store;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public <T> T execute(
            String scope, String key, String requestHash, String responseType,
            Class<T> type, Supplier<T> action) {
        UserAuthStore.IdempotencyClaim claim = store.claimIdempotency(
                scope, key, requestHash, Instant.now(clock).plus(TTL));
        if (claim.replay()) {
            return replay(claim.row(), scope, key, requestHash, responseType, type);
        }
        try {
            T result = action.get();
            byte[] plain = objectMapper.writeValueAsBytes(result);
            String cipher = snapshots.encrypt(scope, key, requestHash, responseType, plain);
            store.completeIdempotencySnapshot(
                    claim.row().id(), responseType + ":ok", responseType, cipher);
            return result;
        } catch (BusinessException failure) {
            store.abandonIdempotency(claim.row().id());
            throw failure;
        } catch (RuntimeException failure) {
            store.abandonIdempotency(claim.row().id());
            throw failure;
        } catch (Exception failure) {
            store.abandonIdempotency(claim.row().id());
            throw new IllegalStateException("Unable to persist media idempotency snapshot", failure);
        }
    }

    private <T> T replay(
            UserAuthStore.IdempotencyRow row, String scope, String key,
            String requestHash, String responseType, Class<T> type) {
        if (!requestHash.equals(row.requestHash())) {
            throw business("COMMON-409-IDEMPOTENCY_CONFLICT", "同一幂等键对应不同请求", 409, false);
        }
        if (row.responseRef() == null || !responseType.equals(row.responseType())
                || row.responsePayloadCiphertext() == null
                || row.responsePayloadCiphertext().isBlank()) {
            throw business("COMMON-409-VERSION_CONFLICT", "同一请求仍在处理中", 409, true);
        }
        try {
            byte[] plain = snapshots.decrypt(
                    scope, key, requestHash, responseType, row.responsePayloadCiphertext());
            return objectMapper.readValue(plain, type);
        } catch (UserIdempotencySnapshotCipher.SnapshotIntegrityException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Media idempotency snapshot is unavailable", failure);
        }
    }

    private static BusinessException business(
            String code, String message, int status, boolean retryable) {
        return new BusinessException(code, message, status, retryable);
    }
}
