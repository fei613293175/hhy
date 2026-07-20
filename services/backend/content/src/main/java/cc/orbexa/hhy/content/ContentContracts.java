package cc.orbexa.hhy.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Java shapes that mirror the frozen R06 content and home schemas. */
public final class ContentContracts {
    private ContentContracts() { }

    public record PageMeta(long page, long pageSize, String total, String nextCursor, String hasMore) { }
    public record PublisherSummary(
            String userId, String nickname, String avatarUrl, String bio,
            boolean verified, String memberBadge, Boolean followed) { }
    public record ContactSummary(
            String channel, String maskedValue, boolean available,
            String accessPolicy, boolean accessed) { }
    public record Statistics(
            long viewCount, long favoriteCount, long shareCount,
            long contactAccessCount, Long conversationCount) { }
    public record MediaItem(
            String id, String mediaType, String url, String thumbnailUrl,
            Long width, Long height, Long durationMs, String altText,
            long sortOrder, String accessMode) { }
    public record ContentResource(
            String id, String contentType, String title, String summary, String description,
            String categoryCode, String regionCode, List<MediaItem> media,
            PublisherSummary publisher, List<ContactSummary> contactsMasked,
            String status, String reviewStatus, Statistics statistics,
            Instant createdAt, Instant updatedAt, long version, Map<String, Object> attributes) {
        public ContentResource {
            media = media == null ? List.of() : List.copyOf(media);
            contactsMasked = contactsMasked == null ? List.of() : List.copyOf(contactsMasked);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
    public record ContentPage(List<ContentResource> items, PageMeta page) {
        public ContentPage { items = List.copyOf(items); }
    }
    public record CommandResult(
            String resourceId, String businessNo, String status,
            long version, Instant acceptedAt) { }

    public record StatusRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @Size(max = 2000) String reason) { }
    public record BanRequest(
            @NotBlank @Size(max = 2000) String reason,
            @NotNull @PositiveOrZero Long expectedVersion) { }
    public record RecommendRequest(
            @NotNull Boolean enabled, Long weight, Instant startAt, Instant endAt,
            @NotNull @PositiveOrZero Long expectedVersion) { }
    public record OfficialMarkRequest(
            @NotNull Boolean enabled, @Size(max = 2000) String label,
            @NotNull @PositiveOrZero Long expectedVersion) { }
    public record DictionaryRequest(
            @NotNull Map<String, Object> items,
            @NotNull @PositiveOrZero Long expectedVersion) { }

    public record NavigationTarget(
            String targetType, String route, String url, boolean requiresLogin) { }
    public record TrackingContext(
            String pageCode, String source, String campaignId, String contentId,
            String requestId, List<Map<String, String>> experimentAssignments) { }
    public record HomeItem(
            String id, String itemType, String title, String subtitle, String coverUrl,
            List<String> badges, NavigationTarget target, TrackingContext trackingContext) { }
    public record HomeModule(
            String moduleId, String moduleType, String title, String subtitle,
            String layoutType, List<HomeItem> items, NavigationTarget moreTarget,
            TrackingContext trackingContext, Instant startAt, Instant endAt) { }
    public record FeatureFlag(String key, boolean enabled, String variant, String reason) { }
    public record HomeResource(
            List<HomeModule> modules, Instant serverTime, List<FeatureFlag> featureFlags,
            TrackingContext trackingContext) { }
}
