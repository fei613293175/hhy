package cc.orbexa.hhy.access.user;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class CiAutomationFixtureStore {
    static final String R12_TARGET_TITLE = "R12候选发布预览项目";
    private static final long R12_FIXTURE_LOCK = 709012L;
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
}
