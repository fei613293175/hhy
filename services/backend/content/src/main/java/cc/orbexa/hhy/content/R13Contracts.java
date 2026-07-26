package cc.orbexa.hhy.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request shapes owned by the frozen R13 activity contract. */
public final class R13Contracts {
    private R13Contracts() { }

    public record InvalidFeedbackRequest(
            @NotBlank @Size(max = 2000) String reasonCode,
            @Size(max = 2000) String description) { }
}
