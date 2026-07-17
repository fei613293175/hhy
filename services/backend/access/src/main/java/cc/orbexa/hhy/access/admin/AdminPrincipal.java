package cc.orbexa.hhy.access.admin;

import java.util.Set;

public record AdminPrincipal(
        long adminId,
        long sessionId,
        long sessionVersion,
        String accessJti,
        String username,
        Set<String> permissionCodes) {
    public AdminPrincipal {
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
