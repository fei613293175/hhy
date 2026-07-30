package cc.orbexa.hhy.access.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.CodePointLength;

public final class R12ProfileContracts {
    private R12ProfileContracts() { }

    public record ProfilePatchRequest(
            @CodePointLength(max = 255) String nickname,
            @CodePointLength(max = 64) String avatarMediaId,
            @CodePointLength(max = 255) String bio,
            @NotNull @PositiveOrZero Long expectedVersion) { }
}
