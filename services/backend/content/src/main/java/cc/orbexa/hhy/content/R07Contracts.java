package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** DTOs for the corrected R07 public search, publisher, and contact contracts. */
public final class R07Contracts {
    private R07Contracts() { }

    public record PageMeta(long page, long pageSize, String total, String nextCursor, String hasMore) { }
    public record PublisherSummary(
            String userId, String nickname, String avatarUrl, String bio,
            boolean verified, String memberBadge, Boolean followed) { }
    public record SearchResult(
            String id, String contentType, String title, String summary, String coverUrl,
            PublisherSummary publisher, double score, List<String> badges) {
        public SearchResult { badges = badges == null ? List.of() : List.copyOf(badges); }
    }
    public record SearchResults(List<SearchResult> items, PageMeta page) {
        public SearchResults { items = List.copyOf(items); }
    }
    public record SearchTerm(String id, String keyword, Instant createdAt) { }
    public record SearchTerms(List<SearchTerm> items, PageMeta page) {
        public SearchTerms { items = List.copyOf(items); }
    }
    public record ContactAccess(String channel, String value, Instant accessedAt) { }
    public record CommandResult(
            String resourceId, String businessNo, String status, long version, Instant acceptedAt) { }
    public record ContactAccessRequest(Map<String, Object> clientContext) {
        public ContactAccessRequest {
            clientContext = clientContext == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(clientContext));
        }
    }
}
