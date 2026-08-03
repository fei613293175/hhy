package cc.orbexa.hhy.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request shapes owned by the frozen R13 activity contract. */
public final class R13Contracts {
    private R13Contracts() { }

    public record InvalidFeedbackRequest(
            @NotBlank @Size(max = 2000) String reasonCode,
            @Size(max = 2000) String description) { }

    public record ContentReportRequest(
            @NotBlank @Size(max = 2000) String reasonCode,
            @NotBlank @Size(max = 2000) String description,
            @Size(max = 100) List<@NotBlank @Size(max = 64) String> evidenceMediaIds,
            @Size(max = 100) List<@NotBlank @Size(max = 64) String> messageIds,
            @PositiveOrZero Long expectedVersion) {
        public ContentReportRequest {
            evidenceMediaIds = evidenceMediaIds == null ? List.of() : List.copyOf(evidenceMediaIds);
            messageIds = messageIds == null ? List.of() : List.copyOf(messageIds);
        }
    }
}
