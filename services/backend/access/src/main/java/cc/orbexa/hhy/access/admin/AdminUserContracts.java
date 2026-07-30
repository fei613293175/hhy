package cc.orbexa.hhy.access.admin;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    public record RestrictionRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,128}$") String restrictionType,
            @NotBlank @Size(max = 2000) String reason,
            @Future Instant expiresAt,
            @NotNull Long expectedVersion) { }

    public record UserControlRequest(
            @NotBlank @Size(max = 2000) String reason,
            @NotNull Long expectedVersion) { }

    public record CommandResultResource(
            String resourceId,
            String businessNo,
            String status,
            Long version,
            Instant acceptedAt) { }
}
