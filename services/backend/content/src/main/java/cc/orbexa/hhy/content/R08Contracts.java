package cc.orbexa.hhy.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Java request/response shapes for the frozen R08 project operations. */
public final class R08Contracts {
    private R08Contracts() { }

    public record ContactInput(
            @NotBlank @Size(max = 32) String channel,
            @NotBlank @Size(max = 2000) String value) { }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 2000) String contentType,
            @NotBlank @Size(max = 2000) String title,
            @Size(max = 2000) String summary,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 2000) String categoryCode,
            @Size(max = 2000) String regionCode,
            @Size(max = 100) List<@NotBlank @Size(max = 64) String> mediaIds,
            @Size(max = 20) List<@Valid ContactInput> contacts,
            Map<String, Object> attributes) { }

    public record PatchProjectRequest(
            @Size(max = 2000) String title,
            @Size(max = 2000) String summary,
            @Size(max = 2000) String description,
            @Size(max = 2000) String categoryCode,
            @Size(max = 2000) String regionCode,
            @Size(max = 100) List<@NotBlank @Size(max = 64) String> mediaIds,
            @Size(max = 20) List<@Valid ContactInput> contacts,
            Map<String, Object> attributes,
            @NotNull @PositiveOrZero Long expectedVersion) { }

    public record FavoriteRequest(
            @Size(max = 2000) String reason,
            @NotNull @PositiveOrZero Long expectedVersion,
            Map<String, Object> payload) { }

    public record ShareRequest(@NotBlank @Size(max = 2000) String channel) { }
    public record ShareResult(String contentId, String channel, String url, Instant acceptedAt) { }

    public record DirectConversationRequest(
            @NotBlank @Size(max = 64) String peerUserId,
            @Size(max = 64) String sourceContentId) { }

    public record PublisherSummary(
            String userId, String nickname, String avatarUrl, String bio,
            boolean verified, String memberBadge, Boolean followed) { }
    public record Conversation(
            String id, PublisherSummary peer, Object lastMessage, long unreadCount,
            String lastReadMessageId, Instant updatedAt, long version) { }

    public record PublicPageBlock(
            String blockId, String blockType, String heading, String body,
            List<ContentContracts.MediaItem> media, Object action, long sortOrder) { }
    public record SeoMetadata(
            String title, String description, List<String> keywords,
            String canonicalUrl, String ogImageUrl, String robots) { }
    public record PublicPage(
            String code, String title, String description, List<PublicPageBlock> content,
            SeoMetadata seoMetadata, Object download, Object trackingContext, long version) { }
}
