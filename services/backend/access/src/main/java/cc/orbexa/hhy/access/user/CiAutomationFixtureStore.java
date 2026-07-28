package cc.orbexa.hhy.access.user;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class CiAutomationFixtureStore {
    static final String R12_TARGET_TITLE = "R12候选发布预览项目";
    static final String R14_PEER_PHONE_PREFIX = "188";
    static final String R14_PEER_NICKNAME = "R14候选体验用户";
    private static final long R12_FIXTURE_LOCK = 709012L;
    private static final long R14_FIXTURE_LOCK = 709014L;
    private final JdbcTemplate jdbc;

    public CiAutomationFixtureStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PreparedTarget prepareR12SubmitTarget(long userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("R12 CI fixture preparation requires one active transaction");
        }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", (rs, row) -> Boolean.TRUE, R12_FIXTURE_LOCK);

        List<TargetRow> targets = jdbc.query("""
                SELECT id,status,review_status,version
                FROM hhy.content_posts
                WHERE owner_id=? AND title=?
                ORDER BY id
                FOR UPDATE
                """, (rs, row) -> new TargetRow(
                rs.getLong("id"), rs.getString("status"), rs.getString("review_status"), rs.getLong("version")),
                userId, R12_TARGET_TITLE);
        if (targets.isEmpty()) {
            throw new IllegalStateException("R12 candidate submit target template is missing");
        }
        if (targets.stream().anyMatch(target -> "BANNED".equals(target.status()))) {
            throw new IllegalStateException("Banned R12 candidate submit target cannot be recycled");
        }

        List<TargetRow> drafts = targets.stream().filter(target -> "DRAFT".equals(target.status())).toList();
        if (drafts.size() > 1) {
            throw new IllegalStateException("R12 candidate has duplicate active submit targets");
        }
        if (drafts.size() == 1) {
            TargetRow draft = drafts.get(0);
            validateFreshDraft(draft);
            retireNonDraftTargets(targets);
            requireExactlyOneDraft(userId, draft.id());
            return new PreparedTarget(draft.id(), false);
        }

        TargetRow template = targets.get(targets.size() - 1);
        validateCloneSource(template.id());
        retireNonDraftTargets(targets);
        long targetId = cloneFreshDraft(userId, template.id());
        validateFreshDraft(new TargetRow(targetId, "DRAFT", null, 0L));
        requireExactlyOneDraft(userId, targetId);
        return new PreparedTarget(targetId, true);
    }

    public PreparedChatTarget prepareR14ChatTarget(
            long userId, String release, String commit, String runId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("R14 CI fixture preparation requires one active transaction");
        }
        if (!"R14".equals(release)
                || commit == null || !commit.matches("^[0-9a-f]{40}$")
                || runId == null || !runId.matches("^[0-9]{1,64}$")) {
            throw new IllegalArgumentException("R14 CI fixture binding is invalid");
        }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", (rs, row) -> Boolean.TRUE, R14_FIXTURE_LOCK);

        String peerPhone = r14PeerPhone(commit, runId);
        Long peerId = jdbc.queryForObject("""
                INSERT INTO hhy.users(phone,status,invite_code)
                VALUES (?,'ACTIVE',?)
                ON CONFLICT(phone) DO UPDATE SET status='ACTIVE'
                RETURNING id
                """, Long.class, peerPhone, "R14CI" + peerPhone.substring(3));
        if (peerId == null || peerId == userId) {
            throw new IllegalStateException("R14 candidate peer is invalid");
        }
        jdbc.update("""
                INSERT INTO hhy.user_profiles(user_id,nickname)
                VALUES (?,?)
                ON CONFLICT(user_id) DO UPDATE SET nickname=EXCLUDED.nickname,updated_at=clock_timestamp()
                """, peerId, R14_PEER_NICKNAME);

        long low = Math.min(userId, peerId);
        long high = Math.max(userId, peerId);
        List<Long> conversations = jdbc.query("""
                SELECT id FROM hhy.conversations
                WHERE direct_user_low_id=? AND direct_user_high_id=?
                FOR UPDATE
                """, (rs, row) -> rs.getLong("id"), low, high);
        if (conversations.size() > 1) {
            throw new IllegalStateException("R14 candidate has duplicate direct conversations");
        }
        long conversationId;
        if (conversations.isEmpty()) {
            Long inserted = jdbc.queryForObject("""
                    INSERT INTO hhy.conversations(
                      type,direct_user_low_id,direct_user_high_id,version,created_at,updated_at
                    ) VALUES ('DIRECT',?,?,0,clock_timestamp(),clock_timestamp())
                    RETURNING id
                    """, Long.class, low, high);
            if (inserted == null) throw new IllegalStateException("R14 candidate conversation insert failed");
            conversationId = inserted;
            jdbc.update("""
                    INSERT INTO hhy.conversation_members(
                      conversation_id,user_id,unread_count,created_at,updated_at
                    ) VALUES (?, ?, 0, clock_timestamp(), clock_timestamp()),
                             (?, ?, 0, clock_timestamp(), clock_timestamp())
                    """, conversationId, userId, conversationId, peerId);
        } else {
            conversationId = conversations.getFirst();
        }
        Long memberCount = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.conversation_members WHERE conversation_id=?",
                Long.class, conversationId);
        if (memberCount == null || memberCount != 2L) {
            throw new IllegalStateException("R14 candidate conversation membership is incomplete");
        }

        jdbc.update("""
                UPDATE hhy.conversation_members
                SET hidden_at=clock_timestamp(),unread_count=0,updated_at=clock_timestamp()
                WHERE user_id=? AND conversation_id<>?
                """, userId, conversationId);
        jdbc.update("""
                UPDATE hhy.conversation_members
                SET last_read_message_id=NULL,hidden_at=NULL,unread_count=0,updated_at=clock_timestamp()
                WHERE conversation_id=?
                """, conversationId);
        jdbc.update("""
                UPDATE hhy.conversations
                SET last_message_id=NULL,last_message_at=NULL,updated_at=clock_timestamp(),version=version+1
                WHERE id=?
                """, conversationId);
        jdbc.update("""
                DELETE FROM hhy.user_blocks
                WHERE (user_id=? AND blocked_user_id=?) OR (user_id=? AND blocked_user_id=?)
                """, userId, peerId, peerId, userId);

        String runSuffix = runId.substring(Math.max(0, runId.length() - 16));
        String clientMessageId = "ci-r14-" + commit.substring(0, 12) + "-" + runSuffix;
        List<Long> existingMessages = jdbc.query("""
                SELECT id FROM hhy.chat_messages
                WHERE conversation_id=? AND sender_id=? AND type='TEXT'
                  AND body_json=CAST(? AS jsonb) AND status='SENT' AND client_msg_id=?
                ORDER BY id
                """, (rs, row) -> rs.getLong("id"), conversationId, peerId,
                "{\"text\":\"R14候选会话已准备\"}", clientMessageId);
        Long totalMessages = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.chat_messages WHERE conversation_id=?",
                Long.class, conversationId);
        if (totalMessages == null || totalMessages != existingMessages.size() || existingMessages.size() > 1) {
            throw new IllegalStateException("R14 candidate conversation was already consumed");
        }
        Long messageId = existingMessages.isEmpty()
                ? jdbc.queryForObject("""
                        INSERT INTO hhy.chat_messages(
                          conversation_id,sender_id,type,body_json,status,client_msg_id,created_at,updated_at
                        ) VALUES (?,?,'TEXT',CAST(? AS jsonb),'SENT',?,clock_timestamp(),clock_timestamp())
                        RETURNING id
                        """, Long.class, conversationId, peerId,
                        "{\"text\":\"R14候选会话已准备\"}", clientMessageId)
                : existingMessages.getFirst();
        if (messageId == null) throw new IllegalStateException("R14 candidate seed message insert failed");
        jdbc.update("""
                UPDATE hhy.conversations
                SET last_message_id=?,
                    last_message_at=(SELECT created_at FROM hhy.chat_messages WHERE id=?),
                    updated_at=clock_timestamp(),version=version+1
                WHERE id=?
                """, messageId, messageId, conversationId);
        jdbc.update("""
                UPDATE hhy.conversation_members
                SET unread_count=CASE WHEN user_id=? THEN 1 ELSE 0 END,
                    last_read_message_id=NULL,hidden_at=NULL,updated_at=clock_timestamp()
                WHERE conversation_id=?
                """, userId, conversationId);
        return new PreparedChatTarget(conversationId, peerId, messageId, clientMessageId);
    }

    private static String r14PeerPhone(String commit, String runId) {
        CRC32 checksum = new CRC32();
        checksum.update((commit + ":" + runId).getBytes(StandardCharsets.UTF_8));
        return R14_PEER_PHONE_PREFIX
                + String.format(Locale.ROOT, "%08d", checksum.getValue() % 100_000_000L);
    }

    private void retireNonDraftTargets(List<TargetRow> targets) {
        for (TargetRow target : targets) {
            if ("DRAFT".equals(target.status()) || "DELETED".equals(target.status())) continue;
            int updated = jdbc.update("""
                    UPDATE hhy.content_posts
                    SET status='DELETED',review_status=NULL,version=version+1,updated_at=clock_timestamp()
                    WHERE id=? AND status=? AND version=?
                    """, target.id(), target.status(), target.version());
            if (updated != 1) {
                throw new IllegalStateException("R12 candidate submit target changed while being recycled");
            }
            jdbc.update("""
                    INSERT INTO hhy.content_status_logs(
                      content_id,from_status,to_status,reason,operator,transition_version
                    ) VALUES (?,?,'DELETED','候选轮次结束后回收旧提交目标','r12-ci-bootstrap',?)
                    """, target.id(), target.status(), target.version() + 1L);
        }
    }

    private long cloneFreshDraft(long userId, long templateId) {
        Long targetId = jdbc.queryForObject("""
                INSERT INTO hhy.content_posts(
                  owner_id,type,title,summary,status,review_status,refresh_times,version,created_at,updated_at
                )
                SELECT ?,type,title,summary,'DRAFT',NULL,0,0,clock_timestamp(),clock_timestamp()
                FROM hhy.content_posts WHERE id=?
                RETURNING id
                """, Long.class, userId, templateId);
        if (targetId == null) throw new IllegalStateException("R12 candidate submit target clone failed");

        requireInserted(jdbc.update("""
                INSERT INTO hhy.project_details(
                  content_id,cooperation,conditions,region,website,created_at,updated_at
                )
                SELECT ?,cooperation,conditions,region,website,clock_timestamp(),clock_timestamp()
                FROM hhy.project_details WHERE content_id=?
                """, targetId, templateId), 1, "project detail");
        requireInserted(jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                SELECT ?,'0',snapshot_json,'r12-ci-bootstrap',clock_timestamp()
                FROM hhy.content_versions
                WHERE content_id=?
                ORDER BY version_no::bigint DESC,id DESC
                LIMIT 1
                """, targetId, templateId), 1, "version snapshot");
        requireInserted(jdbc.update("""
                INSERT INTO hhy.content_stats(
                  content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts,created_at,updated_at
                )
                SELECT ?,organic_views,redpacket_views,task_views,favorites,chats,contacts,
                       clock_timestamp(),clock_timestamp()
                FROM hhy.content_stats WHERE content_id=?
                """, targetId, templateId), 1, "content statistics");
        int media = jdbc.update("""
                INSERT INTO hhy.content_media(
                  content_id,media_id,media_type,sort_order,created_at,updated_at,removed_at
                )
                SELECT ?,media_id,media_type,sort_order,clock_timestamp(),clock_timestamp(),NULL
                FROM hhy.content_media
                WHERE content_id=? AND removed_at IS NULL
                ORDER BY sort_order,id
                """, targetId, templateId);
        if (media < 1) throw new IllegalStateException("R12 candidate submit target media clone failed");
        return targetId;
    }

    private void validateCloneSource(long contentId) {
        ChildFacts facts = childFacts(contentId);
        if (facts.projectDetails() != 1 || facts.otherDetails() != 0 || facts.versions() < 1
                || facts.statistics() != 1 || facts.activeMedia() < 1
                || facts.readyMedia() != facts.activeMedia()) {
            throw new IllegalStateException("R12 candidate submit target template is incomplete");
        }
    }

    private void validateFreshDraft(TargetRow draft) {
        if (!"DRAFT".equals(draft.status()) || draft.version() != 0L || draft.reviewStatus() != null) {
            throw new IllegalStateException("R12 candidate submit target is not a fresh draft");
        }
        ChildFacts facts = childFacts(draft.id());
        Long zeroVersions = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.content_versions WHERE content_id=? AND version_no='0'
                """, Long.class, draft.id());
        if (facts.projectDetails() != 1 || facts.otherDetails() != 0 || facts.versions() != 1
                || zeroVersions == null || zeroVersions != 1L || facts.statistics() != 1
                || facts.activeMedia() < 1 || facts.readyMedia() != facts.activeMedia()) {
            throw new IllegalStateException("R12 candidate submit target draft is incomplete");
        }
    }

    private ChildFacts childFacts(long contentId) {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM hhy.project_details WHERE content_id=?) AS project_details,
                  ((SELECT count(*) FROM hhy.app_details WHERE content_id=?)
                    +(SELECT count(*) FROM hhy.group_details WHERE content_id=?)
                    +(SELECT count(*) FROM hhy.team_leader_details WHERE content_id=?)) AS other_details,
                  (SELECT count(*) FROM hhy.content_versions WHERE content_id=?) AS versions,
                  (SELECT count(*) FROM hhy.content_stats WHERE content_id=?) AS statistics,
                  (SELECT count(*) FROM hhy.content_media WHERE content_id=? AND removed_at IS NULL) AS active_media,
                  (SELECT count(*) FROM hhy.content_media link
                    JOIN hhy.media_objects media ON media.id=link.media_id
                    WHERE link.content_id=? AND link.removed_at IS NULL
                      AND media.status='READY' AND media.deleted_at IS NULL) AS ready_media
                """, (rs, row) -> new ChildFacts(
                rs.getLong("project_details"), rs.getLong("other_details"), rs.getLong("versions"),
                rs.getLong("statistics"), rs.getLong("active_media"), rs.getLong("ready_media")),
                contentId, contentId, contentId, contentId, contentId, contentId, contentId, contentId);
    }

    private void requireExactlyOneDraft(long userId, long expectedId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.content_posts
                WHERE owner_id=? AND title=? AND status='DRAFT' AND version=0 AND review_status IS NULL
                """, Long.class, userId, R12_TARGET_TITLE);
        Long id = jdbc.queryForObject("""
                SELECT min(id) FROM hhy.content_posts
                WHERE owner_id=? AND title=? AND status='DRAFT'
                """, Long.class, userId, R12_TARGET_TITLE);
        if (count == null || count != 1L || id == null || id != expectedId) {
            throw new IllegalStateException("R12 candidate submit target is not unique");
        }
    }

    private static void requireInserted(int actual, int expected, String label) {
        if (actual != expected) throw new IllegalStateException("R12 candidate " + label + " clone failed");
    }

    private record TargetRow(long id, String status, String reviewStatus, long version) { }
    private record ChildFacts(long projectDetails, long otherDetails, long versions,
                              long statistics, long activeMedia, long readyMedia) { }
    public record PreparedTarget(long contentId, boolean created) { }
    public record PreparedChatTarget(long conversationId, long peerId, long messageId, String binding) { }
}
