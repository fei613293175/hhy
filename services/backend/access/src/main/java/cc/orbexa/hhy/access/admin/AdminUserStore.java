package cc.orbexa.hhy.access.admin;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminUserStore {
    private static final String USER_PROJECTION = """
            SELECT u.id,u.phone,u.status,u.created_at,u.version,
                   p.nickname,p.avatar,p.bio,
                   (SELECT identity.status FROM hhy.identity_profiles identity
                    WHERE identity.user_id=u.id ORDER BY identity.updated_at DESC,identity.id DESC LIMIT 1)
                     AS identity_status,
                   (SELECT membership.status FROM hhy.user_memberships membership
                    WHERE membership.user_id=u.id ORDER BY membership.updated_at DESC,membership.id DESC LIMIT 1)
                     AS membership_status
            FROM hhy.users u
            LEFT JOIN hhy.user_profiles p ON p.user_id=u.id
            """;

    private final JdbcTemplate jdbc;

    public AdminUserStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserPage listUsers(
            int page, int pageSize, String status, String keyword, String orderBy) {
        QueryFilter filter = filter(status, keyword);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.users u LEFT JOIN hhy.user_profiles p ON p.user_id=u.id"
                        + filter.where(),
                Long.class,
                filter.arguments().toArray());
        List<Object> pageArguments = new ArrayList<>(filter.arguments());
        pageArguments.add(pageSize);
        pageArguments.add((page - 1L) * pageSize);
        List<UserRow> items = jdbc.query(
                USER_PROJECTION + filter.where() + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                (rs, row) -> new UserRow(
                        rs.getLong("id"),
                        rs.getString("phone"),
                        rs.getString("nickname"),
                        rs.getString("avatar"),
                        rs.getString("bio"),
                        rs.getString("status"),
                        rs.getString("identity_status"),
                        rs.getString("membership_status"),
                        instant(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getLong("version")),
                pageArguments.toArray());
        return new UserPage(items, total == null ? 0 : total);
    }

    public Optional<UserRow> findUser(long userId) {
        return jdbc.query(
                USER_PROJECTION + " WHERE u.id=?",
                (rs, row) -> new UserRow(
                        rs.getLong("id"),
                        rs.getString("phone"),
                        rs.getString("nickname"),
                        rs.getString("avatar"),
                        rs.getString("bio"),
                        rs.getString("status"),
                        rs.getString("identity_status"),
                        rs.getString("membership_status"),
                        instant(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getLong("version")),
                userId).stream().findFirst();
    }

    private static QueryFilter filter(String status, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND u.status=?");
            arguments.add(status.strip().toUpperCase(java.util.Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.strip().toLowerCase(java.util.Locale.ROOT) + "%";
            where.append(" AND (LOWER(u.phone) LIKE ? OR LOWER(COALESCE(p.nickname,'')) LIKE ?")
                    .append(" OR CAST(u.id AS text) LIKE ?)");
            arguments.add(pattern);
            arguments.add(pattern);
            arguments.add(pattern);
        }
        return new QueryFilter(where.toString(), List.copyOf(arguments));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record QueryFilter(String where, List<Object> arguments) { }

    public record UserRow(
            long id,
            String phone,
            String nickname,
            String avatarUrl,
            String bio,
            String status,
            String identityStatus,
            String membershipStatus,
            Instant createdAt,
            long version) { }

    public record UserPage(List<UserRow> items, long total) {
        public UserPage {
            items = List.copyOf(items);
        }
    }
}
