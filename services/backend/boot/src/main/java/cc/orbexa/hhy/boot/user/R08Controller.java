package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.R08Contracts.Conversation;
import cc.orbexa.hhy.content.R08Contracts.CreateProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.DirectConversationRequest;
import cc.orbexa.hhy.content.R08Contracts.FavoriteRequest;
import cc.orbexa.hhy.content.R08Contracts.PatchProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.PublicPage;
import cc.orbexa.hhy.content.R08Contracts.ShareRequest;
import cc.orbexa.hhy.content.R08Contracts.ShareResult;
import cc.orbexa.hhy.content.R08Service;
import cc.orbexa.hhy.content.R09Service;
import cc.orbexa.hhy.content.R10Service;
import cc.orbexa.hhy.content.R11Service;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** R08 project detail, publishing, favorite, share, direct-chat, and public preview endpoints. */
@Validated
@RestController
public class R08Controller {
    private static final String RESOURCE_ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R08Service service;
    private final R09Service appService;
    private final R10Service groupService;
    private final R11Service teamLeaderService;
    private final Clock clock;

    public R08Controller(
            R08Service service, R09Service appService, R10Service groupService,
            R11Service teamLeaderService, Clock clock) {
        this.service = service;
        this.appService = appService;
        this.groupService = groupService;
        this.teamLeaderService = teamLeaderService;
        this.clock = clock;
    }

    @PostMapping("/api/v1/contents")
    public ApiResponse<ContentResource> contentPostContents(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateProjectRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        String type = body.contentType().strip().toUpperCase(java.util.Locale.ROOT);
        return success(request, switch (type) {
            case "APP" -> appService.create(principal.userId(), body, key);
            case "GROUP_CHAT" -> groupService.create(principal.userId(), body, key);
            case "TEAM_LEADER" -> teamLeaderService.create(principal.userId(), body, key);
            default -> service.create(principal.userId(), body, key);
        });
    }

    @GetMapping("/api/v1/contents/{id}")
    public ApiResponse<ContentResource> contentGetContentsById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            HttpServletRequest request) {
        return success(request, appService.isApp(id)
                ? appService.detail(principal.userId(), id)
                : groupService.isGroup(id)
                        ? groupService.detail(principal.userId(), id)
                        : teamLeaderService.isTeamLeader(id)
                                ? teamLeaderService.detail(principal.userId(), id)
                                : service.detail(principal.userId(), id));
    }

    @PatchMapping("/api/v1/contents/{id}")
    public ApiResponse<ContentResource> contentPatchContentsById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody PatchProjectRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, appService.isApp(id)
                ? appService.patch(principal.userId(), id, body, key)
                : groupService.isGroup(id)
                        ? groupService.patch(principal.userId(), id, body, key)
                        : teamLeaderService.isTeamLeader(id)
                                ? teamLeaderService.patch(principal.userId(), id, body, key)
                                : service.patch(principal.userId(), id, body, key));
    }

    @PostMapping("/api/v1/contents/{id}/favorite")
    public ApiResponse<ContentResource> contentPostContentsByIdFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody FavoriteRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.favorite(principal.userId(), id, body, key));
    }

    @PostMapping("/api/v1/contents/{id}/share")
    public ApiResponse<ShareResult> contentPostContentsByIdShare(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody ShareRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.share(principal.userId(), id, body, key));
    }

    @PostMapping("/api/v1/conversations/direct")
    public ApiResponse<Conversation> chatPostConversationsDirect(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DirectConversationRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.direct(principal.userId(), body, key));
    }

    @GetMapping("/public-api/v1/share/contents/{id}")
    public ApiResponse<PublicPage> publicGetShareContentsById(
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            HttpServletRequest request) {
        return success(request, appService.isApp(id)
                ? appService.publicShare(id)
                : groupService.isGroup(id) ? groupService.publicShare(id) : service.publicShare(id));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}
