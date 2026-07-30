package cc.orbexa.hhy.access.admin;

import java.util.Set;

public record AdminPrincipal(
        long adminId,
        long sessionId,
        long sessionVersion,
        String accessJti,
        String username,
        Set<String> permissionCodes,
        boolean replayOnly) {
    public static final String IDEMPOTENCY_REPLAY_MARKER = "admin.idempotency.replay";

    public AdminPrincipal {
        permissionCodes = Set.copyOf(permissionCodes);
        if (permissionCodes.contains(IDEMPOTENCY_REPLAY_MARKER)) {
            throw new IllegalArgumentException(
                    "Reserved replay marker cannot be loaded as an administrator permission");
        }
        if (replayOnly && !permissionCodes.isEmpty()) {
            throw new IllegalArgumentException("Replay-only administrator principal cannot carry business permissions");
        }
    }

    public AdminPrincipal(
            long adminId, long sessionId, long sessionVersion, String accessJti,
            String username, Set<String> permissionCodes) {
        this(adminId, sessionId, sessionVersion, accessJti, username, permissionCodes, false);
    }
}
