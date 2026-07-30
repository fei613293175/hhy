package cc.orbexa.hhy.incentive;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.sql.DataSource;

public class R12RewardStore {
    private final DataSource dataSource;

    public R12RewardStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<RewardLookup> find(long userId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT user_account.id AS user_id,
                            (SELECT identity.status FROM hhy.identity_profiles identity
                             WHERE identity.user_id=user_account.id
                             ORDER BY identity.updated_at DESC,identity.id DESC LIMIT 1) AS identity_status,
                            account.id AS account_id,account.pending,account.available,account.frozen,
                            account.withdrawn,account.version,account.updated_at,
                            EXISTS(SELECT 1 FROM hhy.reward_ledger ledger
                                   WHERE ledger.user_id=user_account.id
                                     AND ledger.status='RISK_FROZEN') AS risk_frozen
                     FROM hhy.users user_account
                     LEFT JOIN hhy.reward_accounts account ON account.user_id=user_account.id
                     WHERE user_account.id=?
                     """)) {
            connection.setReadOnly(true);
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                long accountId = rows.getLong("account_id");
                boolean accountExists = !rows.wasNull();
                RewardAccountRow account = accountExists
                        ? new RewardAccountRow(
                                accountId,
                                rows.getLong("pending"),
                                rows.getLong("available"),
                                rows.getLong("frozen"),
                                rows.getLong("withdrawn"),
                                rows.getLong("version"),
                                instant(rows, "updated_at"))
                        : null;
                return Optional.of(new RewardLookup(
                        rows.getLong("user_id"), rows.getString("identity_status"),
                        rows.getBoolean("risk_frozen"), account));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to read the R12 reward account projection", failure);
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record RewardLookup(
            long userId, String identityStatus, boolean riskFrozen, RewardAccountRow account) { }

    public record RewardAccountRow(
            long id,
            long pendingCent,
            long availableCent,
            long frozenCent,
            long withdrawnCent,
            long version,
            Instant updatedAt) { }
}
