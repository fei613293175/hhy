package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class CiAutomationFixtureStorePostgresTest {
    @Test
    void r14FixtureRebuildsTheSameBusinessStartAcrossConsecutiveRuns() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
        Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema("public")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var fixture = new CiAutomationFixtureStore(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Long userId = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code)
                VALUES (?,'ACTIVE',?) RETURNING id
                """, Long.class, "17" + suffix.substring(0, 9), "R14CI" + suffix.substring(0, 12));
        jdbc.update("INSERT INTO hhy.user_profiles(user_id,nickname) VALUES (?,?)", userId, "候选执行账号");
        String commit = "a".repeat(40);
        String runId = "123456789";

        var first = transaction.execute(status ->
                fixture.prepareR14ChatTarget(userId, "R14", commit, runId));
        jdbc.update("""
                INSERT INTO hhy.user_blocks(user_id,blocked_user_id,reason)
                VALUES (?,?,?)
                """, userId, first.peerId(), "候选扰动");
        jdbc.update("""
                UPDATE hhy.conversation_members SET hidden_at=clock_timestamp(),unread_count=9
                WHERE conversation_id=? AND user_id=?
                """, first.conversationId(), userId);

        var second = transaction.execute(status ->
                fixture.prepareR14ChatTarget(userId, "R14", commit, runId));

        assertEquals(first.conversationId(), second.conversationId());
        assertEquals(first.peerId(), second.peerId());
        assertEquals(first.messageId(), second.messageId());
        assertEquals(first.binding(), second.binding());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM hhy.chat_messages WHERE conversation_id=?",
                Integer.class, second.conversationId()));
        assertEquals("R14候选会话已准备", jdbc.queryForObject("""
                SELECT body_json->>'text' FROM hhy.chat_messages WHERE id=?
                """, String.class, second.messageId()));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.user_blocks
                WHERE (user_id=? AND blocked_user_id=?) OR (user_id=? AND blocked_user_id=?)
                """, Integer.class, userId, second.peerId(), second.peerId(), userId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT unread_count FROM hhy.conversation_members
                WHERE conversation_id=? AND user_id=? AND hidden_at IS NULL
                """, Integer.class, second.conversationId(), userId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.chat_read_receipts receipt
                JOIN hhy.chat_messages message ON message.id=receipt.message_id
                WHERE message.conversation_id=?
                """, Integer.class, second.conversationId()));

        var nextRun = transaction.execute(status ->
                fixture.prepareR14ChatTarget(userId, "R14", commit, "123456790"));
        assertNotEquals(second.conversationId(), nextRun.conversationId());
        assertNotEquals(second.peerId(), nextRun.peerId());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.conversation_members
                WHERE conversation_id=? AND user_id=? AND hidden_at IS NULL AND unread_count=1
                """, Integer.class, nextRun.conversationId(), userId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hhy.conversation_members
                WHERE conversation_id=? AND user_id=? AND hidden_at IS NOT NULL AND unread_count=0
                """, Integer.class, second.conversationId(), userId));
    }
}
