package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.access.admin.AdminUserContracts.PageMetaResource;
import cc.orbexa.hhy.access.identity.IdentityContracts.IdentitySessionResource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Exact Java request and page shapes for the five frozen R05 administrator operations. */
public final class AdminIdentityContracts {
    private AdminIdentityContracts() { }

    public record IdentityPageResource(
            List<IdentitySessionResource> items, PageMetaResource page) {
        public IdentityPageResource {
            items = List.copyOf(items);
        }
    }

    public record MediaAccessRequest(
            @NotBlank @Size(max = 2000) String purpose,
            @PositiveOrZero Long ttlSeconds) { }

    public record ReviewRequest(
            @NotBlank @Size(max = 2000) String decision,
            @NotBlank @Size(max = 2000) String reason,
            @NotNull @PositiveOrZero Long expectedVersion,
            @Size(max = 100) List<@Size(max = 64) String> evidenceIds) {
        public ReviewRequest {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record FreezeRequest(
            @NotBlank @Size(max = 2000) String reason,
            @NotNull @PositiveOrZero Long expectedVersion) { }
}
