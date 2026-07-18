package cc.orbexa.hhy.access.admin;

import java.time.Instant;
import java.util.List;

public final class AdminUserContracts {
    private AdminUserContracts() { }

    public record UserResource(
            String id,
            String phoneMasked,
            String nickname,
            String avatarUrl,
            String bio,
            String status,
            String identityStatus,
            String membershipStatus,
            Instant createdAt,
            long version) { }

    public record PageMetaResource(
            long page,
            long pageSize,
            String total,
            String nextCursor,
            String hasMore) { }

    public record UserPageResource(List<UserResource> items, PageMetaResource page) {
        public UserPageResource {
            items = List.copyOf(items);
        }
    }
}
