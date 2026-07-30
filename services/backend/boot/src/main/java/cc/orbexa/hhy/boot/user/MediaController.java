package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.storage.MediaContracts.CompleteUploadSessionRequest;
import cc.orbexa.hhy.access.storage.MediaContracts.CreateUploadSessionRequest;
import cc.orbexa.hhy.access.storage.MediaContracts.MediaResource;
import cc.orbexa.hhy.access.storage.MediaUploadService;
import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Thin orchestration for the three frozen R04 operationIds. */
@Validated
@RestController
public class MediaController {
    private final MediaUploadService service;
    private final Clock clock;

    public MediaController(MediaUploadService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/api/v1/media/upload-sessions")
    public ApiResponse<MediaResource> mediaPostMediaUploadSessions(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUploadSessionRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.create(principal, body, key));
    }

    @PostMapping("/api/v1/media/upload-sessions/{id}/complete")
    public ApiResponse<MediaResource> mediaPostMediaUploadSessionsByIdComplete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @Valid @RequestBody CompleteUploadSessionRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.complete(principal, id, body, key));
    }

    @DeleteMapping("/api/v1/media/{id}")
    public ApiResponse<CommandResultResource> mediaDeleteMediaById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.delete(principal, id, key));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(requestId == null ? "missing" : requestId.toString(),
                data, Instant.now(clock));
    }
}
