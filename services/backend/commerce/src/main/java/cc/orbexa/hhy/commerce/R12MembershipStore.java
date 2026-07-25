package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R12MembershipContracts.BenefitResource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class R12MembershipStore {
    private static final int MAX_BENEFITS = 100;

    private final DataSource dataSource;
    private final JsonDecoder jsonDecoder;

    public R12MembershipStore(DataSource dataSource, JsonDecoder jsonDecoder) {
        this.dataSource = dataSource;
        this.jsonDecoder = jsonDecoder;
    }

    public Optional<MembershipRow> findCurrent(long userId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                Optional<MembershipHead> head = membershipHead(connection, userId);
                if (head.isEmpty()) {
                    connection.commit();
                    return Optional.empty();
                }
                MembershipHead current = head.orElseThrow();
                Optional<Sku> sku = currentSku(connection, userId, current.planId());
                List<BenefitResource> benefits = sku.isEmpty()
                        ? List.of() : benefits(connection, sku.orElseThrow().membershipSkuId());
                Values values = entitlementValues(connection, userId);
                connection.commit();
                return Optional.of(new MembershipRow(
                        current.id(), sku.map(Sku::code).orElse(null), current.name(), current.status(),
                        current.startsAt(), current.expiresAt(), benefits,
                        values.paidValueCent(), values.remainingValueCent(), current.version()));
            } catch (RuntimeException | SQLException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to read the R12 membership projection", failure);
        }
    }

    private Optional<MembershipHead> membershipHead(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT membership.id,membership.plan_id,plan.name,membership.status,
                       membership.starts_at,membership.ends_at,membership.version
                FROM hhy.user_memberships membership
                JOIN hhy.membership_plans plan ON plan.id=membership.plan_id
                WHERE membership.user_id=?
                ORDER BY membership.updated_at DESC,membership.id DESC
                LIMIT 2
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                MembershipHead result = new MembershipHead(
                        rows.getLong("id"), rows.getLong("plan_id"), rows.getString("name"),
                        rows.getString("status"), instant(rows, "starts_at"),
                        instant(rows, "ends_at"), rows.getLong("version"));
                if (rows.next()) {
                    throw new IllegalStateException("Multiple current memberships exist for one user");
                }
                return Optional.of(result);
            }
        }
    }

    private Optional<Sku> currentSku(Connection connection, long userId, long planId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT membership_sku.id AS membership_sku_id,product_sku.code
                FROM hhy.membership_entitlement_segments segment
                JOIN hhy.order_items item ON item.order_id=segment.source_order_id
                JOIN hhy.membership_skus membership_sku
                  ON membership_sku.sku_id=item.sku_id AND membership_sku.plan_id=?
                JOIN hhy.product_skus product_sku ON product_sku.id=membership_sku.sku_id
                WHERE segment.user_id=? AND segment.source_order_id IS NOT NULL
                ORDER BY segment.ends_at DESC NULLS LAST,segment.id DESC,item.id DESC
                LIMIT 1
                """)) {
            statement.setLong(1, planId);
            statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new Sku(
                        rows.getLong("membership_sku_id"), rows.getString("code")));
            }
        }
    }

    private List<BenefitResource> benefits(Connection connection, long membershipSkuId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT benefit.code,benefit.description,sku_benefit.value_json::text AS value_json
                FROM hhy.membership_sku_benefits sku_benefit
                JOIN hhy.membership_benefits benefit ON benefit.id=sku_benefit.benefit_id
                WHERE sku_benefit.membership_sku_id=? AND sku_benefit.enabled=true
                ORDER BY benefit.code,sku_benefit.id
                LIMIT 101
                """)) {
            statement.setLong(1, membershipSkuId);
            try (ResultSet rows = statement.executeQuery()) {
                List<BenefitResource> result = new ArrayList<>();
                while (rows.next()) {
                    if (result.size() == MAX_BENEFITS) {
                        throw new IllegalStateException("Membership benefit projection exceeds the API limit");
                    }
                    String code = rows.getString("code");
                    String description = rows.getString("description");
                    String name = description == null || description.isBlank() || description.length() > 120
                            ? code : description;
                    String encodedValue = rows.getString("value_json");
                    Object value = jsonDecoder.decode(encodedValue);
                    if (value == null) {
                        throw new IllegalStateException("Enabled membership benefit has no value");
                    }
                    result.add(new BenefitResource(code, name, value, null));
                }
                return List.copyOf(result);
            }
        }
    }

    private Values entitlementValues(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) AS segment_count,
                       sum(COALESCE(paid_amount,0)) AS paid_value,
                       sum(COALESCE(paid_amount,0)-COALESCE(consumed,0)) AS remaining_value
                FROM hhy.membership_entitlement_segments WHERE user_id=?
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Membership value query returned no row");
                if (rows.getLong("segment_count") == 0) return new Values(null, null);
                Long paid = exactLong(rows.getBigDecimal("paid_value"), "paid membership value");
                Long remaining = exactLong(rows.getBigDecimal("remaining_value"), "remaining membership value");
                if (paid == null || remaining == null || paid < 0 || remaining < 0) {
                    throw new IllegalStateException("Membership value projection is negative or incomplete");
                }
                return new Values(paid, remaining);
            }
        }
    }

    private static Long exactLong(BigDecimal value, String field) {
        if (value == null) return null;
        try {
            return value.longValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalStateException(field + " exceeds the API int64 range", failure);
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    @FunctionalInterface
    public interface JsonDecoder {
        Object decode(String json);
    }

    public record MembershipRow(
            long id,
            String skuId,
            String name,
            String status,
            Instant startsAt,
            Instant expiresAt,
            List<BenefitResource> benefits,
            Long paidValueCent,
            Long remainingValueCent,
            long version) { }

    private record MembershipHead(
            long id, long planId, String name, String status,
            Instant startsAt, Instant expiresAt, long version) { }

    private record Sku(long membershipSkuId, String code) { }

    private record Values(Long paidValueCent, Long remainingValueCent) { }
}
