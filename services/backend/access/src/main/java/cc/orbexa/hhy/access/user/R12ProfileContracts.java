package cc.orbexa.hhy.access.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class R12ProfileContracts {
    private R12ProfileContracts() { }

    public record ProfilePatchRequest(
            @Size(max = 2000) String nickname,
            @Size(max = 64) String avatarMediaId,
            @Size(max = 2000) String bio,
            @NotNull @PositiveOrZero Long expectedVersion) { }
}
