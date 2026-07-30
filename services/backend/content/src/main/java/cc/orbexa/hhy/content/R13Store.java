package cc.orbexa.hhy.content;

import java.time.Instant;
import java.util.List;

public interface R13Store {
    ActivityPage favorites(ActivityQuery query);
    ActivityPage history(ActivityQuery query);
    boolean unfavorite(long userId, long contentId, Instant now);
    long invalidFeedback(long userId, long contentId, String reasonCode, String description, Instant now);

    enum SortDirection { ASC, DESC }

    record ActivityCursor(Instant occurredAt, long activityId) { }

    record ActivityQuery(
            long userId, int page, int pageSize, ActivityCursor cursor,
            String status, String keyword, SortDirection direction) { }

    record ActivityRow(long activityId, long contentId, Instant occurredAt) { }

    record ActivityPage(List<ActivityRow> items, long total, boolean hasMore) {
        public ActivityPage { items = List.copyOf(items); }
    }
}
