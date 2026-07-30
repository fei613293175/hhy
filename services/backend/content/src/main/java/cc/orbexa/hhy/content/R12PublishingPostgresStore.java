package cc.orbexa.hhy.content;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class R12PublishingPostgresStore implements R12PublishingStore {
    private final JdbcTemplate jdbc;

    public R12PublishingPostgresStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageIds ownerPage(OwnerQuery query) {
        StringBuilder where = new StringBuilder(" WHERE p.owner_id=? AND p.status<>'DELETED'");
        List<Object> args = new ArrayList<>();
        args.add(query.ownerId());
        if (query.draftsOnly()) {
            where.append(" AND p.status='DRAFT'");
        } else if (query.status() != null) {
            where.append(" AND p.status=?");
            args.add(query.status());
        }
        if (query.keyword() != null) {
            where.append(" AND (p.title ILIKE ? OR p.summary ILIKE ?)");
            String value = "%" + query.keyword() + "%";
            args.add(value);
            args.add(value);
        }
        append(where, args, "p.type=?", query.contentType());
        append(where, args, "snapshot.snapshot_json->>'categoryCode'=?", query.categoryCode());
        append(where, args, "snapshot.snapshot_json->>'regionCode'=?", query.regionCode());
        String joined = " FROM hhy.content_posts p LEFT JOIN LATERAL ("
                + "SELECT v.snapshot_json FROM hhy.content_versions v WHERE v.content_id=p.id "
                + "ORDER BY v.created_at DESC,v.id DESC LIMIT 1) snapshot ON true" + where;
        Long total = jdbc.queryForObject("SELECT count(*)" + joined, Long.class, args.toArray());
        StringBuilder pageWhere = new StringBuilder(where);
        List<Object> pageArgs = new ArrayList<>(args);
        appendOwnerCursor(pageWhere, pageArgs, query.sort(), query.cursor());
        pageArgs.add(query.pageSize() + 1);
        pageArgs.add(query.cursor() == null ? (query.page() - 1) * query.pageSize() : 0);
        String pageJoined = " FROM hhy.content_posts p LEFT JOIN LATERAL ("
                + "SELECT v.snapshot_json FROM hhy.content_versions v WHERE v.content_id=p.id "
                + "ORDER BY v.created_at DESC,v.id DESC LIMIT 1) snapshot ON true" + pageWhere;
        List<PageItem> rows = jdbc.query("SELECT p.id,p.created_at,p.updated_at" + pageJoined
                        + " ORDER BY " + ownerOrder(query.sort()) + " LIMIT ? OFFSET ?",
                (rs, row) -> new PageItem(
                        rs.getLong(1), ownerSortValue(query.sort(),
                                instant(rs.getObject(2, OffsetDateTime.class)),
                                instant(rs.getObject(3, OffsetDateTime.class)))),
                pageArgs.toArray());
        boolean more = rows.size() > query.pageSize();
        if (more) rows = new ArrayList<>(rows.subList(0, query.pageSize()));
        return new PageIds(rows, total == null ? 0 : total, more);
    }

    @Override
    public Optional<OwnedContent> lockOwned(long contentId) {
        return jdbc.query("SELECT id,owner_id,type,status,version FROM hhy.content_posts WHERE id=? FOR UPDATE",
                (rs, row) -> new OwnedContent(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5)),
                contentId).stream().findFirst();
    }

    @Override
    public void lockOwnerQuota(long ownerId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                "r12-publishing-owner:" + ownerId);
    }

    @Override
    public String publishingTier(long ownerId) {
        return jdbc.query("""
                SELECT lower(membership_sku.term_type)
                FROM hhy.user_memberships membership
                LEFT JOIN LATERAL (
                  SELECT sku.term_type
                  FROM hhy.membership_entitlement_segments segment
                  JOIN hhy.order_items item ON item.order_id=segment.source_order_id
                  JOIN hhy.membership_skus sku
                    ON sku.sku_id=item.sku_id AND sku.plan_id=membership.plan_id
                  WHERE segment.user_id=membership.user_id AND segment.source_order_id IS NOT NULL
                  ORDER BY segment.ends_at DESC NULLS LAST,segment.id DESC,item.id DESC
                  LIMIT 1
                ) membership_sku ON true
                WHERE membership.user_id=? AND membership.status='ACTIVE'
                  AND (membership.ends_at IS NULL OR membership.ends_at>clock_timestamp())
                ORDER BY membership.updated_at DESC,membership.id DESC LIMIT 1
                """, (rs, row) -> rs.getString(1), ownerId).stream().findFirst().orElse("normal");
    }

    @Override
    public boolean transitionOwned(
            long contentId, long ownerId, long expectedVersion, String fromStatus,
            String toStatus, String reviewStatus, Instant now) {
        return jdbc.update("""
                UPDATE hhy.content_posts
                SET status=?,review_status=COALESCE(?,review_status),version=version+1,updated_at=?
                WHERE id=? AND owner_id=? AND version=? AND status=?
                """, toStatus, reviewStatus, time(now), contentId, ownerId, expectedVersion, fromStatus) == 1;
    }

    @Override
    public void submissionSnapshot(long contentId, long ownerId, Instant now) {
        int inserted = jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                SELECT p.id,p.version::text,
                       COALESCE(latest.snapshot_json,'{}'::jsonb)
                         || jsonb_build_object('contentType',p.type,'title',p.title,'summary',p.summary),
                       ?,?
                FROM hhy.content_posts p
                LEFT JOIN LATERAL (
                  SELECT v.snapshot_json FROM hhy.content_versions v
                  WHERE v.content_id=p.id ORDER BY v.created_at DESC,v.id DESC LIMIT 1
                ) latest ON true
                WHERE p.id=?
                """, "user:" + ownerId, time(now), contentId);
        if (inserted != 1) throw new IllegalStateException("R12 submission snapshot parent disappeared");
    }

    @Override
    public void statusLog(
            long contentId, String fromStatus, String toStatus, String reason,
            long ownerId, long transitionVersion) {
        jdbc.update("""
                INSERT INTO hhy.content_status_logs(
                  content_id,from_status,to_status,reason,operator,transition_version
                ) VALUES (?,?,?,?,?,?)
                """, contentId, fromStatus, toStatus, reason, "user:" + ownerId, transitionVersion);
    }

    @Override
    public boolean approvedSnapshotIsCurrent(long contentId) {
        Boolean value = jdbc.queryForObject("""
                SELECT EXISTS(
                  SELECT 1
                  FROM hhy.content_review_records review
                  JOIN hhy.content_versions approved ON approved.id=review.snapshot_version_id
                  WHERE review.content_id=? AND review.decision='APPROVE'
                    AND approved.id=(
                      SELECT latest.id FROM hhy.content_versions latest
                      WHERE latest.content_id=?
                      ORDER BY latest.version_no::bigint DESC,latest.id DESC LIMIT 1
                    )
                )
                """, Boolean.class, contentId, contentId);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public long latestCorrectionVersion(long contentId) {
        Long value = jdbc.queryForObject("""
                SELECT max(transition_version) FROM hhy.content_status_logs
                WHERE content_id=? AND to_status IN ('REJECTED','RECTIFICATION')
                """, Long.class, contentId);
        return value == null ? -1L : value;
    }

    @Override
    public long submissionsSince(long ownerId, Instant since) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM hhy.content_status_logs history
                JOIN hhy.content_posts content ON content.id=history.content_id
                WHERE content.owner_id=? AND history.to_status='PENDING_REVIEW' AND history.created_at>=?
                """, Long.class, ownerId, time(since));
        return count == null ? 0 : count;
    }

    @Override
    public ReviewPage reviews(long contentId, ReviewQuery query) {
        StringBuilder where = new StringBuilder(" WHERE review.content_id=?");
        List<Object> args = new ArrayList<>();
        args.add(contentId);
        append(where, args, "review.decision=?", query.status());
        if (query.keyword() != null) {
            where.append(" AND COALESCE(review.reason,'') ILIKE ?");
            args.add("%" + query.keyword() + "%");
        }
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM hhy.content_review_records review" + where,
                Long.class, args.toArray());
        StringBuilder pageWhere = new StringBuilder(where);
        List<Object> pageArgs = new ArrayList<>(args);
        appendReviewCursor(pageWhere, pageArgs, query.sort(), query.cursor());
        pageArgs.add(query.pageSize() + 1);
        pageArgs.add(query.cursor() == null ? (query.page() - 1) * query.pageSize() : 0);
        List<ReviewFact> rows = jdbc.query("""
                SELECT review.id,review.decision,review.reason,review.admin_id,review.version_no,review.created_at
                FROM hhy.content_review_records review
                """ + pageWhere + " ORDER BY " + reviewOrder(query.sort()) + " LIMIT ? OFFSET ?",
                (rs, row) -> new ReviewFact(
                        rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, Long.class), rs.getString(5),
                        instant(rs.getObject(6, OffsetDateTime.class))), pageArgs.toArray());
        boolean more = rows.size() > query.pageSize();
        if (more) rows = new ArrayList<>(rows.subList(0, query.pageSize()));
        return new ReviewPage(rows, total == null ? 0 : total, more);
    }

    @Override
    public long copySkeleton(long sourceContentId, long ownerId, Instant now) {
        Long targetId = jdbc.queryForObject("""
                INSERT INTO hhy.content_posts(
                  owner_id,type,title,summary,status,review_status,refresh_times,version,created_at,updated_at
                )
                SELECT owner_id,type,title,summary,'DRAFT',NULL,0,0,?,?
                FROM hhy.content_posts WHERE id=? AND owner_id=?
                RETURNING id
                """, Long.class, time(now), time(now), sourceContentId, ownerId);
        if (targetId == null) throw new IllegalStateException("R12 copied content id was not returned");
        copyDetail(sourceContentId, targetId, now);
        jdbc.update("""
                INSERT INTO hhy.content_media(
                  content_id,media_id,media_type,sort_order,removed_at,created_at,updated_at
                )
                SELECT ?,media_id,media_type,sort_order,NULL,?,?
                FROM hhy.content_media WHERE content_id=? AND removed_at IS NULL
                ORDER BY sort_order,id
                """, targetId, time(now), time(now), sourceContentId);
        int version = jdbc.update("""
                INSERT INTO hhy.content_versions(content_id,version_no,snapshot_json,created_by,created_at)
                SELECT ?,'0',COALESCE(latest.snapshot_json,'{}'::jsonb),?,?
                FROM hhy.content_posts source
                LEFT JOIN LATERAL (
                  SELECT value.snapshot_json FROM hhy.content_versions value
                  WHERE value.content_id=source.id
                  ORDER BY value.version_no::bigint DESC,value.id DESC LIMIT 1
                ) latest ON true
                WHERE source.id=?
                """, targetId, "user:" + ownerId, time(now), sourceContentId);
        if (version != 1) throw new IllegalStateException("R12 copied content snapshot was not created");
        jdbc.update("""
                INSERT INTO hhy.content_stats(
                  content_id,organic_views,redpacket_views,task_views,favorites,chats,contacts
                ) VALUES (?,'0','0','0','0','0','0')
                """, targetId);
        return targetId;
    }

    @Override
    public List<ContactEnvelope> activeContacts(long contentId) {
        return jdbc.query("""
                SELECT channel,value_cipher,display_mask,sort_order
                FROM hhy.content_contacts
                WHERE content_id=? AND removed_at IS NULL ORDER BY sort_order,id
                """, (rs, row) -> new ContactEnvelope(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)), contentId);
    }

    @Override
    public void copyContacts(long contentId, List<ContactEnvelope> contacts, Instant now) {
        for (ContactEnvelope contact : contacts) {
            jdbc.update("""
                    INSERT INTO hhy.content_contacts(
                      content_id,channel,value_cipher,display_mask,sort_order,removed_at,created_at,updated_at
                    ) VALUES (?,?,?,?,?,NULL,?,?)
                    """, contentId, contact.channel(), contact.valueCipher(), contact.displayMask(),
                    contact.sortOrder(), time(now), time(now));
        }
    }

    @Override
    public void outbox(
            long actorId, String eventType, long contentId, String fromStatus, String toStatus,
            long version, String reason, CommandAudit audit, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
                ) VALUES (?, 'CONTENT', ?, ?, 1, '{"source":"r12-publishing-api"}'::jsonb,
                  jsonb_build_object(
                    'actorId',CAST(? AS bigint),'actorRole','USER',
                    'resourceId',CAST(? AS text),'fromStatus',CAST(? AS text),
                    'toStatus',CAST(? AS text),'contentVersion',CAST(? AS bigint),
                    'reason',CAST(? AS text),'requestId',CAST(? AS text),
                    'clientIp',CAST(? AS text),'device',CAST(? AS text),
                    'result','SUCCESS','occurredAt',CAST(? AS text)))
                """, Long.toString(contentId), UUID.randomUUID().toString(), eventType,
                actorId, Long.toString(contentId), fromStatus, toStatus, version, reason,
                audit.requestId(), audit.clientIp(), audit.device(), occurredAt.toString());
    }

    private void copyDetail(long sourceId, long targetId, Instant now) {
        String type = jdbc.queryForObject("SELECT type FROM hhy.content_posts WHERE id=?", String.class, sourceId);
        int changed = switch (type == null ? "" : type) {
            case "PROJECT" -> jdbc.update("""
                    INSERT INTO hhy.project_details(
                      content_id,cooperation,conditions,region,website,created_at,updated_at
                    ) SELECT ?,cooperation,conditions,region,website,?,?
                      FROM hhy.project_details WHERE content_id=?
                    """, targetId, time(now), time(now), sourceId);
            case "APP" -> jdbc.update("""
                    INSERT INTO hhy.app_details(
                      content_id,app_name,platform,version_text,download_url,website,created_at,updated_at
                    ) SELECT ?,app_name,platform,version_text,download_url,website,?,?
                      FROM hhy.app_details WHERE content_id=?
                    """, targetId, time(now), time(now), sourceId);
            case "GROUP" -> jdbc.update("""
                    INSERT INTO hhy.group_details(
                      content_id,platform,size_range,join_requirement,qr_media_id,group_link,group_no,
                      created_at,updated_at
                    ) SELECT ?,platform,size_range,join_requirement,qr_media_id,group_link,group_no,?,?
                      FROM hhy.group_details WHERE content_id=?
                    """, targetId, time(now), time(now), sourceId);
            case "TEAM_LEADER" -> jdbc.update("""
                    INSERT INTO hhy.team_leader_details(
                      content_id,team_name,size_range,skills,cooperation_types,nickname,logo_media_id,region,
                      personal_intro,team_intro,cooperation_requirement,past_cases,accept_private_chat,
                      created_at,updated_at
                    ) SELECT ?,team_name,size_range,skills,cooperation_types,nickname,logo_media_id,region,
                      personal_intro,team_intro,cooperation_requirement,past_cases,accept_private_chat,?,?
                      FROM hhy.team_leader_details WHERE content_id=?
                    """, targetId, time(now), time(now), sourceId);
            default -> 0;
        };
        if (changed != 1) throw new IllegalStateException("R12 copied content detail is unavailable");
    }

    private static void appendOwnerCursor(
            StringBuilder where, List<Object> args, String sort, CursorKey cursor) {
        if (cursor == null) return;
        String comparison = sort.endsWith(":asc") ? ">" : "<";
        switch (sort) {
            case "createdAt:asc", "createdAt:desc" -> {
                requireCursorTime(cursor);
                where.append(" AND (p.created_at,p.id) ").append(comparison).append(" (?,?)");
                args.add(time(cursor.sortValue()));
                args.add(cursor.id());
            }
            case "updatedAt:asc", "updatedAt:desc" -> {
                requireCursorTime(cursor);
                where.append(" AND (p.updated_at,p.id) ").append(comparison).append(" (?,?)");
                args.add(time(cursor.sortValue()));
                args.add(cursor.id());
            }
            case "id:asc", "id:desc" -> {
                where.append(" AND p.id ").append(comparison).append(" ?");
                args.add(cursor.id());
            }
            default -> throw new IllegalArgumentException("Unsupported R12 owner sort");
        }
    }

    private static void appendReviewCursor(
            StringBuilder where, List<Object> args, String sort, CursorKey cursor) {
        if (cursor == null) return;
        String comparison = sort.endsWith(":asc") ? ">" : "<";
        switch (sort) {
            case "createdAt:asc", "createdAt:desc", "updatedAt:asc", "updatedAt:desc" -> {
                requireCursorTime(cursor);
                where.append(" AND (review.created_at,review.id) ")
                        .append(comparison).append(" (?,?)");
                args.add(time(cursor.sortValue()));
                args.add(cursor.id());
            }
            case "id:asc", "id:desc" -> {
                where.append(" AND review.id ").append(comparison).append(" ?");
                args.add(cursor.id());
            }
            default -> throw new IllegalArgumentException("Unsupported R12 review sort");
        }
    }

    private static String ownerOrder(String sort) {
        return switch (sort) {
            case "createdAt:desc" -> "p.created_at DESC,p.id DESC";
            case "createdAt:asc" -> "p.created_at ASC,p.id ASC";
            case "updatedAt:desc" -> "p.updated_at DESC,p.id DESC";
            case "updatedAt:asc" -> "p.updated_at ASC,p.id ASC";
            case "id:desc" -> "p.id DESC";
            case "id:asc" -> "p.id ASC";
            default -> throw new IllegalArgumentException("Unsupported R12 owner sort");
        };
    }

    private static String reviewOrder(String sort) {
        return switch (sort) {
            case "createdAt:desc", "updatedAt:desc" -> "review.created_at DESC,review.id DESC";
            case "createdAt:asc", "updatedAt:asc" -> "review.created_at ASC,review.id ASC";
            case "id:desc" -> "review.id DESC";
            case "id:asc" -> "review.id ASC";
            default -> throw new IllegalArgumentException("Unsupported R12 review sort");
        };
    }

    private static Instant ownerSortValue(String sort, Instant createdAt, Instant updatedAt) {
        return switch (sort) {
            case "createdAt:asc", "createdAt:desc" -> createdAt;
            case "updatedAt:asc", "updatedAt:desc" -> updatedAt;
            case "id:asc", "id:desc" -> null;
            default -> throw new IllegalArgumentException("Unsupported R12 owner sort");
        };
    }

    private static void requireCursorTime(CursorKey cursor) {
        if (cursor.sortValue() == null) throw new IllegalArgumentException("R12 cursor time is required");
    }

    private static void append(StringBuilder sql, List<Object> args, String expression, Object value) {
        if (value != null) {
            sql.append(" AND ").append(expression);
            args.add(value);
        }
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
