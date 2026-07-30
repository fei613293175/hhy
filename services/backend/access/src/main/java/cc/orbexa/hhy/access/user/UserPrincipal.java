package cc.orbexa.hhy.access.user;

/** Database-bound identity for an authenticated mobile user request. */
public record UserPrincipal(long userId, long sessionId, long sessionVersion, String accessJti, String userStatus) {
    public boolean active() { return "ACTIVE".equals(userStatus); }
}
