package cc.orbexa.hhy.access.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Canonical Java representation of the three frozen R04 media operations. */
public final class MediaContracts {
    private MediaContracts() { }

    public record CreateUploadSessionRequest(
            @NotBlank @Size(max = 2000) String purpose,
            @NotBlank @Size(max = 2000) String fileName,
            @NotBlank @Size(max = 2000) String contentType,
            @NotNull Long sizeBytes,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Fa-f0-9]{40,64}$") String sha256) { }

    public record CompleteUploadSessionRequest(
            @NotBlank @Size(max = 2000) String etag,
            @Size(max = 10000) List<Map<String, Object>> parts) {
        public CompleteUploadSessionRequest {
            parts = parts == null ? List.of() : List.copyOf(parts);
        }
    }

    public record MediaResource(
            String id,
            String purpose,
            String contentType,
            Long sizeBytes,
            String sha256,
            String uploadUrl,
            String readUrl,
            String status,
            Instant expiresAt) { }
}
