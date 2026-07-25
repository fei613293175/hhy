package cc.orbexa.hhy.content;

import org.springframework.jdbc.core.JdbcTemplate;

final class R12PostgresTestFixtures {
    private R12PostgresTestFixtures() { }

    static long publish(JdbcTemplate jdbc, long contentId, long expectedVersion, String suffix) {
        Long adminId = jdbc.queryForObject("""
                INSERT INTO hhy.admin_users(username,password_hash,status)
                VALUES (?,repeat('a',60),'ACTIVE') RETURNING id
                """, Long.class, "r12-test-" + suffix.substring(0, 12));
        if (adminId == null) throw new IllegalStateException("R12 test administrator id missing");

        long submittedVersion = expectedVersion + 1;
        jdbc.update("UPDATE hhy.content_posts SET status='PENDING_REVIEW',version=version+1 WHERE id=? AND version=?",
                contentId, expectedVersion);
        Long snapshotId = jdbc.queryForObject("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by)
                SELECT content.id,content.version::text,latest.snapshot_json,'test:submit'
                FROM hhy.content_posts content
                JOIN LATERAL (
                  SELECT snapshot_json FROM hhy.content_versions
                  WHERE content_id=content.id ORDER BY version_no::bigint DESC LIMIT 1
                ) latest ON true
                WHERE content.id=? RETURNING id
                """, Long.class, contentId);
        if (snapshotId == null) throw new IllegalStateException("R12 test submit snapshot missing");
        jdbc.update("""
                INSERT INTO hhy.content_status_logs(
                  content_id,from_status,to_status,operator,transition_version
                ) VALUES (?,'DRAFT','PENDING_REVIEW','test:owner',?)
                """, contentId, submittedVersion);

        long reviewingVersion = submittedVersion + 1;
        jdbc.update("UPDATE hhy.content_posts SET status='REVIEWING',version=version+1 WHERE id=? AND version=?",
                contentId, submittedVersion);
        jdbc.update("""
                INSERT INTO hhy.content_review_records(
                  content_id,version_no,decision,admin_id,snapshot_version_id,command_id
                ) VALUES (?,?, 'CLAIM',?,?,?)
                """, contentId, Long.toString(submittedVersion), adminId, snapshotId, "claim-" + suffix);
        jdbc.update("""
                INSERT INTO hhy.content_status_logs(
                  content_id,from_status,to_status,operator,transition_version
                ) VALUES (?,'PENDING_REVIEW','REVIEWING','test:admin',?)
                """, contentId, reviewingVersion);

        long approvedVersion = reviewingVersion + 1;
        jdbc.update("UPDATE hhy.content_posts SET status='APPROVED',version=version+1 WHERE id=? AND version=?",
                contentId, reviewingVersion);
        jdbc.update("""
                INSERT INTO hhy.content_review_records(
                  content_id,version_no,decision,admin_id,snapshot_version_id,command_id
                ) VALUES (?,?,'APPROVE',?,?,?)
                """, contentId, Long.toString(submittedVersion), adminId, snapshotId, "approve-" + suffix);
        jdbc.update("""
                INSERT INTO hhy.content_status_logs(
                  content_id,from_status,to_status,operator,transition_version
                ) VALUES (?,'REVIEWING','APPROVED','test:admin',?)
                """, contentId, approvedVersion);

        long onlineVersion = approvedVersion + 1;
        jdbc.update("UPDATE hhy.content_posts SET status='ONLINE',version=version+1 WHERE id=? AND version=?",
                contentId, approvedVersion);
        jdbc.update("""
                INSERT INTO hhy.content_status_logs(
                  content_id,from_status,to_status,operator,transition_version
                ) VALUES (?,'APPROVED','ONLINE','test:owner',?)
                """, contentId, onlineVersion);
        return onlineVersion;
    }
}

