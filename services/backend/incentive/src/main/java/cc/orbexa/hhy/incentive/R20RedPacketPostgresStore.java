package cc.orbexa.hhy.incentive;

import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CommandResultResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CreateRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.OrderRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.PatchRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.QuoteRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.SubmitReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.UserCommand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/** Durable R20 campaign facts. All commands and their idempotency claim share one transaction. */
public final class R20RedPacketPostgresStore implements R20RedPacketStore {
    private static final int SERVICE_FEE_BPS = 500;
    private final DataSource dataSource;
    private final R20RedPacketContracts.Codec codec;
    private final Clock clock;

    public R20RedPacketPostgresStore(
            DataSource dataSource, R20RedPacketContracts.Codec codec, Clock clock) {
        this.dataSource = dataSource;
        this.codec = codec;
        this.clock = clock;
    }

    @Override
    public PageSlice<CampaignResource> userCampaigns(long userId, PageQuery query) {
        return page(query, "c.owner_id = ?", List.<Object>of(userId));
    }

    @Override
    public CampaignResource userCampaign(long userId, long campaignId) {
        return read(connection -> resource(connection, campaignId, userId, false));
    }

    @Override
    public CampaignResource create(UserCommand command, CreateRequest request, String requestHash) {
        return transaction(connection -> {
            Idempotency claim = claim(connection, userScope(command), command.idempotencyKey(), requestHash);
            if (claim.responseRef() != null) return resource(connection, parse(claim.responseRef()), command.userId(), false);
            long contentId = parse(request.contentId());
            if (!contentOwnedBy(connection, contentId, command.userId())) {
                throw notFound("内容不存在或不属于当前账号");
            }
            long principal = Math.multiplyExact(request.totalCount(), request.amountPerClaimCent());
            long fee = fee(principal);
            long id;
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO hhy.red_packet_campaigns(
                      content_id,owner_id,status,total_count,current_amount,required_seconds,
                      amount_per_claim_cent,
                      principal_cent,service_fee_cent,start_at,end_at,targeting_json,version)
                    VALUES (?,?, 'DRAFT',?,?,10,?,?,?,?,?,?::jsonb,0) RETURNING id
                    """)) {
                statement.setLong(1, contentId);
                statement.setLong(2, command.userId());
                statement.setLong(3, request.totalCount());
                statement.setLong(4, request.amountPerClaimCent());
                statement.setLong(5, request.amountPerClaimCent());
                statement.setLong(6, principal);
                statement.setLong(7, fee);
                statement.setObject(8, time(request.startAt()));
                statement.setObject(9, time(request.endAt()));
                statement.setString(10, codec.json(request.targeting()));
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw internal("红包活动创建未返回结果");
                    id = rows.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO hhy.red_packet_stock(campaign_id,total,claimed,reserved,version) VALUES (?, ?,0,0,0)")) {
                statement.setLong(1, id);
                statement.setLong(2, request.totalCount());
                statement.executeUpdate();
            }
            version(connection, id, request, requestHash);
            complete(connection, claim.id(), Long.toString(id));
            outbox(connection, id, "red_packet_campaign", "red.packet.campaign.created.v1",
                    Map.of("campaignId", id, "ownerId", command.userId()));
            return resource(connection, id, command.userId(), false);
        });
    }

    @Override
    public CampaignResource patch(
            UserCommand command, long campaignId, PatchRequest request, String requestHash) {
        return transaction(connection -> {
            Idempotency claim = claim(connection, userScope(command), command.idempotencyKey(), requestHash);
            if (claim.responseRef() != null) return resource(connection, parse(claim.responseRef()), command.userId(), false);
            CampaignHead current = campaign(connection, campaignId, command.userId(), false, true);
            if (current.version() != request.expectedVersion()) throw conflict("红包活动版本已变化");
            if (!("DRAFT".equals(current.status()) || "PRE_REVIEW_REJECTED".equals(current.status()))) {
                throw rule("当前红包活动状态不可编辑");
            }
            long total = request.totalCount() == null ? current.totalCount() : request.totalCount();
            long amount = request.amountPerClaimCent() == null
                    ? current.amountPerClaimCent() : request.amountPerClaimCent();
            long principal = Math.multiplyExact(total, amount);
            long fee = fee(principal);
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    UPDATE hhy.red_packet_campaigns
                    SET total_count=?, current_amount=?, amount_per_claim_cent=?,
                        principal_cent=?, service_fee_cent=?,
                        start_at=?, end_at=?, targeting_json=COALESCE(?::jsonb,targeting_json),
                        version=version+1, updated_at=clock_timestamp()
                    WHERE id=? AND owner_id=? AND version=?
                    """)) {
                statement.setLong(1, total);
                statement.setLong(2, amount);
                statement.setLong(3, amount);
                statement.setLong(4, principal);
                statement.setLong(5, fee);
                statement.setObject(6, request.startAt() == null ? time(current.startAt()) : time(request.startAt()));
                statement.setObject(7, request.endAt() == null ? time(current.endAt()) : time(request.endAt()));
                if (request.targeting() == null) statement.setNull(8, Types.VARCHAR);
                else statement.setString(8, codec.json(request.targeting()));
                statement.setLong(9, campaignId);
                statement.setLong(10, command.userId());
                statement.setLong(11, request.expectedVersion());
                if (statement.executeUpdate() != 1) throw conflict("红包活动版本已变化");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE hhy.red_packet_stock SET total=?,version=version+1,updated_at=clock_timestamp() "
                            + "WHERE campaign_id=? AND claimed+reserved<=?")) {
                statement.setLong(1, total);
                statement.setLong(2, campaignId);
                statement.setLong(3, total);
                if (statement.executeUpdate() != 1) throw rule("红包数量不能低于已锁定或已领取名额");
            }
            snapshot(connection, campaignId, requestHash);
            complete(connection, claim.id(), Long.toString(campaignId));
            outbox(connection, campaignId, "red_packet_campaign", "red.packet.campaign.updated.v1",
                    Map.of("campaignId", campaignId, "ownerId", command.userId()));
            return resource(connection, campaignId, command.userId(), false);
        });
    }

    @Override
    public CampaignResource submitReview(
            UserCommand command, long campaignId, SubmitReviewRequest request, String requestHash) {
        return transaction(connection -> {
            Idempotency claim = claim(connection, userScope(command), command.idempotencyKey(), requestHash);
            if (claim.responseRef() != null) return resource(connection, parse(claim.responseRef()), command.userId(), false);
            CampaignHead current = campaign(connection, campaignId, command.userId(), false, true);
            if (current.version() != request.expectedVersion()) throw conflict("红包活动版本已变化");
            if (!("DRAFT".equals(current.status()) || "PRE_REVIEW_REJECTED".equals(current.status()))) {
                throw rule("当前红包活动状态不可提交预审核");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE hhy.red_packet_campaigns SET status='PRE_REVIEWING',review_reason=?,version=version+1,updated_at=clock_timestamp() "
                            + "WHERE id=? AND owner_id=? AND version=?")) {
                statement.setString(1, request.remark());
                statement.setLong(2, campaignId);
                statement.setLong(3, command.userId());
                statement.setLong(4, request.expectedVersion());
                if (statement.executeUpdate() != 1) throw conflict("红包活动版本已变化");
            }
            complete(connection, claim.id(), Long.toString(campaignId));
            outbox(connection, campaignId, "red_packet_campaign", "red.packet.campaign.review.submitted.v1",
                    Map.of("campaignId", campaignId, "ownerId", command.userId()));
            return resource(connection, campaignId, command.userId(), false);
        });
    }

    @Override
    public CommandResultResource quote(
            UserCommand command, long campaignId, QuoteRequest request, String requestHash) {
        return transaction(connection -> {
            Idempotency claim = claim(connection, userScope(command), command.idempotencyKey(), requestHash);
            if (claim.responseRef() != null) return quoteResult(connection, parse(claim.responseRef()));
            CampaignHead current = campaign(connection, campaignId, command.userId(), false, true);
            if (current.version() != request.expectedVersion()) throw conflict("红包活动版本已变化");
            if (!"PRE_REVIEW_APPROVED".equals(current.status())) throw rule("审核通过后才可以创建报价");
            if (request.totalCount() != current.totalCount()
                    || request.amountPerClaimCent() != current.amountPerClaimCent()) {
                throw rule("初始报价必须与审核通过的活动金额和数量一致");
            }
            long principal = Math.multiplyExact(request.totalCount(), request.amountPerClaimCent());
            long fee = fee(principal);
            if (initialQuoteId(connection, campaignId, true) != null) {
                throw rule("红包初始报价已存在");
            }
            long quoteId;
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO hhy.red_packet_quotes(
                      campaign_id,quote_type,principal,service_fee,payable,expires_at,
                      total_count,amount_per_claim_cent,idempotency_key,request_hash,version)
                    VALUES (?,'INITIAL',?,?,?,clock_timestamp()+interval '30 minutes',?,?,?,?,0)
                    RETURNING id
                    """)) {
                statement.setLong(1, campaignId);
                statement.setLong(2, principal);
                statement.setLong(3, fee);
                statement.setLong(4, principal + fee);
                statement.setLong(5, request.totalCount());
                statement.setLong(6, request.amountPerClaimCent());
                statement.setString(7, command.idempotencyKey());
                statement.setString(8, requestHash);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw internal("红包报价创建失败");
                    quoteId = rows.getLong(1);
                }
            }
            complete(connection, claim.id(), Long.toString(quoteId));
            outbox(connection, quoteId, "red_packet_quote", "red.packet.quote.created.v1",
                    Map.of("quoteId", quoteId, "campaignId", campaignId));
            return quoteResult(connection, quoteId);
        });
    }

    @Override
    public CommandResultResource order(
            UserCommand command, long campaignId, OrderRequest request, String requestHash) {
        return transaction(connection -> {
            Idempotency claim = claim(connection, userScope(command), command.idempotencyKey(), requestHash);
            if (claim.responseRef() != null) return orderResult(connection, parse(claim.responseRef()));
            CampaignHead current = campaign(connection, campaignId, command.userId(), false, true);
            if (current.version() != request.expectedVersion()) throw conflict("红包活动版本已变化");
            if (!"PRE_REVIEW_APPROVED".equals(current.status())) {
                throw rule("审核通过后才可以创建红包订单");
            }
            QuoteHead quote = quote(connection, parse(request.quoteId()), campaignId, true);
            if (quote.expiresAt() != null && !quote.expiresAt().isAfter(now())) throw rule("红包报价已过期");
            if (quote.hasOrder()) throw rule("红包报价已关联订单");
            long orderId;
            String orderNo = "R20-RP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO hhy.orders(
                      order_no,user_id,biz_type,biz_id,amount_cent,status,currency,
                      no_refund_confirmed,no_refund_agreement_version,no_refund_confirmed_at,
                      idempotency_key,request_hash,legacy_without_idempotency,version)
                    VALUES (?,?, 'RED_PACKET',? ,?,'PENDING_PAYMENT','CNY',true,'R20-RED-PACKET-NO-REFUND-V1',?, ?, ?, false,0)
                    RETURNING id,version
                    """)) {
                statement.setString(1, orderNo);
                statement.setLong(2, command.userId());
                statement.setLong(3, campaignId);
                statement.setLong(4, quote.payable());
                statement.setObject(5, time(now()));
                statement.setString(6, command.idempotencyKey());
                statement.setString(7, requestHash);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw internal("红包订单创建失败");
                    orderId = rows.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO hhy.red_packet_orders(campaign_id,order_id,quote_id,type,version,idempotency_key,request_hash) VALUES (?,?,?,'INITIAL',0,?,?)")) {
                statement.setLong(1, campaignId);
                statement.setLong(2, orderId);
                statement.setLong(3, quote.id());
                statement.setString(4, command.idempotencyKey());
                statement.setString(5, requestHash);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE hhy.red_packet_campaigns SET status='WAITING_PAYMENT',version=version+1,updated_at=clock_timestamp() "
                            + "WHERE id=? AND version=?")) {
                statement.setLong(1, campaignId);
                statement.setLong(2, request.expectedVersion());
                if (statement.executeUpdate() != 1) throw conflict("红包活动版本已变化");
            }
            complete(connection, claim.id(), Long.toString(orderId));
            outbox(connection, orderId, "red_packet_order", "red.packet.order.created.v1",
                    Map.of("orderId", orderId, "campaignId", campaignId, "paymentChannel", request.paymentChannel()));
            return new CommandResultResource(Long.toString(orderId), orderNo,
                    "PENDING_PAYMENT", 0, now());
        });
    }

    @Override
    public PageSlice<CampaignResource> adminCampaigns(PageQuery query) {
        return page(query, "1=1", List.of());
    }

    @Override
    public CampaignResource adminCampaign(long campaignId) {
        return read(connection -> resource(connection, campaignId, 0, true));
    }

    @Override
    public CampaignResource review(
            AdminCommand command, long campaignId, AdminReviewRequest request, String requestHash) {
        return transaction(connection -> {
            String scope = "r20rp:admin:" + command.adminId() + ":" + command.operationId();
            Idempotency claim = claim(connection, scope, command.idempotencyKey(), requestHash);
            if (claim.responseRef() != null) return resource(connection, parse(claim.responseRef()), 0, true);
            CampaignHead current = campaign(connection, campaignId, 0, true, true);
            if (current.version() != request.expectedVersion()) throw conflict("红包活动版本已变化");
            if (!"PRE_REVIEWING".equals(current.status())) {
                throw rule("当前红包活动不在预审核中");
            }
            String status = switch (request.decision()) {
                case "APPROVE" -> "PRE_REVIEW_APPROVED";
                case "REJECT" -> "PRE_REVIEW_REJECTED";
                case "ESCALATE" -> "PRE_REVIEWING";
                default -> throw rule("审核决定不受支持");
            };
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE hhy.red_packet_campaigns SET status=?,review_decision=?,review_reason=?,review_evidence_json=?::jsonb,reviewed_by=?,reviewed_at=?,version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?")) {
                statement.setString(1, status);
                statement.setString(2, request.decision());
                statement.setString(3, request.reason());
                statement.setString(4, codec.json(Map.of("evidenceIds", request.evidenceIds())));
                statement.setLong(5, command.adminId());
                statement.setObject(6, time(now()));
                statement.setLong(7, campaignId);
                statement.setLong(8, request.expectedVersion());
                if (statement.executeUpdate() != 1) throw conflict("红包活动版本已变化");
            }
            complete(connection, claim.id(), Long.toString(campaignId));
            outbox(connection, campaignId, "red_packet_campaign", "red.packet.campaign.reviewed.v1",
                    Map.of("campaignId", campaignId, "adminId", command.adminId(), "decision", request.decision()));
            return resource(connection, campaignId, 0, true);
        });
    }

    private PageSlice<CampaignResource> page(PageQuery query, String ownerClause, List<Object> ownerArgs) {
        return read(connection -> {
            String filters = ownerClause;
            List<Object> args = new ArrayList<>(ownerArgs);
            if (query.status() != null) { filters += " AND c.status=?"; args.add(query.status()); }
            if (query.keyword() != null) {
                filters += " AND (CAST(c.id AS text) LIKE ? OR CAST(c.content_id AS text) LIKE ?)";
                args.add("%" + query.keyword() + "%"); args.add("%" + query.keyword() + "%");
            }
            String order = switch (query.sort()) {
                case "createdAt:asc" -> "c.created_at ASC";
                case "updatedAt:asc" -> "c.updated_at ASC";
                case "updatedAt:desc" -> "c.updated_at DESC";
                case "totalCount:asc" -> "c.total_count ASC";
                case "totalCount:desc" -> "c.total_count DESC";
                case "amountPerClaimCent:asc" -> "c.amount_per_claim_cent ASC";
                case "amountPerClaimCent:desc" -> "c.amount_per_claim_cent DESC";
                case "priority:desc,createdAt:asc" ->
                        "CASE WHEN c.status='PRE_REVIEWING' THEN 1 ELSE 0 END DESC,c.created_at ASC,c.id ASC";
                default -> "c.created_at DESC";
            };
            String sql = "SELECT c.id FROM hhy.red_packet_campaigns c WHERE " + filters
                    + " ORDER BY " + order + " LIMIT ? OFFSET ?";
            List<CampaignResource> items = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, args); statement.setInt(args.size() + 1, query.pageSize());
                statement.setLong(args.size() + 2, query.offset());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) items.add(resource(connection, rows.getLong(1), 0, true));
                }
            }
            long total;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM hhy.red_packet_campaigns c WHERE " + filters)) {
                bind(statement, args);
                try (ResultSet rows = statement.executeQuery()) { rows.next(); total = rows.getLong(1); }
            }
            return new PageSlice<>(items, total);
        });
    }

    private CampaignResource resource(Connection connection, long campaignId, long ownerId, boolean admin) {
        String sql = """
                SELECT c.id,c.content_id,c.owner_id,c.status,c.total_count,
                  COALESCE(s.total-s.claimed-s.reserved,c.total_count),
                  COALESCE(c.amount_per_claim_cent,0),COALESCE(c.principal_cent,0),
                  COALESCE(c.service_fee_cent,0),c.start_at,c.end_at,c.version
                FROM hhy.red_packet_campaigns c
                LEFT JOIN hhy.red_packet_stock s ON s.campaign_id=c.id
                WHERE c.id=?
                """ + (admin ? "" : " AND c.owner_id=?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, campaignId); if (!admin) statement.setLong(2, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("红包活动不存在");
                return new CampaignResource(
                        Long.toString(rows.getLong(1)), Long.toString(rows.getLong(2)),
                        nullableLong(rows, 3), rows.getString(4), rows.getLong(5), rows.getLong(6),
                        rows.getLong(7), rows.getLong(8), rows.getLong(9),
                        instant(rows, 10), instant(rows, 11), rows.getLong(12));
            }
        } catch (SQLException failure) { throw internal("红包活动读取失败", failure); }
    }

    private CampaignHead campaign(Connection connection, long id, long ownerId, boolean admin, boolean lock) {
        String sql = "SELECT id,status,total_count,amount_per_claim_cent,start_at,end_at,version FROM hhy.red_packet_campaigns WHERE id=?"
                + (admin ? "" : " AND owner_id=?") + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id); if (!admin) statement.setLong(2, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("红包活动不存在");
                return new CampaignHead(rows.getLong(1), rows.getString(2), rows.getLong(3), rows.getLong(4),
                        instant(rows, 5), instant(rows, 6), rows.getLong(7));
            }
        } catch (SQLException failure) { throw internal("红包活动读取失败", failure); }
    }

    private QuoteHead quote(Connection connection, long id, long campaignId, boolean lock) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT q.id,q.principal+q.service_fee,q.expires_at,EXISTS("
                        + "SELECT 1 FROM hhy.red_packet_orders o WHERE o.quote_id=q.id) "
                        + "FROM hhy.red_packet_quotes q WHERE q.id=? AND q.campaign_id=?" + (lock ? " FOR UPDATE" : ""))) {
            statement.setLong(1, id); statement.setLong(2, campaignId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("红包报价不存在");
                return new QuoteHead(rows.getLong(1), rows.getLong(2), instant(rows, 3), rows.getBoolean(4));
            }
        } catch (SQLException failure) { throw internal("红包报价读取失败", failure); }
    }

    private Long initialQuoteId(Connection connection, long campaignId, boolean lock) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM hhy.red_packet_quotes WHERE campaign_id=? AND quote_type='INITIAL'"
                        + (lock ? " FOR UPDATE" : ""))) {
            statement.setLong(1, campaignId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : null;
            }
        } catch (SQLException failure) { throw internal("红包报价读取失败", failure); }
    }

    private CommandResultResource quoteResult(Connection connection, long id) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT campaign_id,version,created_at FROM hhy.red_packet_quotes WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("红包报价不存在");
                return new CommandResultResource(Long.toString(id), "R20-QUOTE-" + id,
                        "QUOTED", rows.getLong(2), instant(rows, 3));
            }
        } catch (SQLException failure) { throw internal("红包报价结果读取失败", failure); }
    }

    private CommandResultResource orderResult(Connection connection, long id) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_no,status,version,created_at FROM hhy.orders WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("红包订单不存在");
                return new CommandResultResource(Long.toString(id), rows.getString(1), rows.getString(2),
                        rows.getLong(3), instant(rows, 4));
            }
        } catch (SQLException failure) { throw internal("红包订单结果读取失败", failure); }
    }

    private void version(Connection connection, long id, CreateRequest request, String requestHash) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hhy.red_packet_campaign_versions(campaign_id,version_no,amount,total_count,amount_per_claim_cent,targeting_json,request_hash) VALUES (?, '1', ?, ?, ?, ?::jsonb, ?)")) {
            statement.setLong(1, id); statement.setLong(2, Math.multiplyExact(request.totalCount(), request.amountPerClaimCent()));
            statement.setLong(3, request.totalCount()); statement.setLong(4, request.amountPerClaimCent());
            statement.setString(5, codec.json(request.targeting())); statement.setString(6, requestHash); statement.executeUpdate();
        } catch (SQLException failure) { throw internal("红包活动版本创建失败", failure); }
    }

    private static boolean contentOwnedBy(Connection connection, long contentId, long userId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM hhy.content_posts WHERE id=? AND owner_id=? AND status NOT IN ('DELETED','REMOVED')")) {
            statement.setLong(1, contentId);
            statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        } catch (SQLException failure) { throw internal("红包内容校验失败", failure); }
    }

    private void snapshot(Connection connection, long id, String requestHash) {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO hhy.red_packet_campaign_versions(
                  campaign_id,version_no,amount,total_count,amount_per_claim_cent,
                  targeting_json,request_hash)
                SELECT id,version::text,principal_cent,total_count,amount_per_claim_cent,
                       targeting_json,?
                FROM hhy.red_packet_campaigns WHERE id=?
                """)) {
            statement.setString(1, requestHash);
            statement.setLong(2, id);
            if (statement.executeUpdate() != 1) throw internal("红包活动版本快照创建失败");
        } catch (SQLException failure) { throw internal("红包活动版本快照创建失败", failure); }
    }

    private Idempotency claim(Connection connection, String scope, String key, String requestHash) {
        try {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at) "
                            + "VALUES (?,?,?,clock_timestamp()+interval '24 hours') "
                            + "ON CONFLICT (scope,idem_key) DO NOTHING RETURNING id")) {
                insert.setString(1, scope); insert.setString(2, key); insert.setString(3, requestHash);
                try (ResultSet rows = insert.executeQuery()) {
                    if (rows.next()) return new Idempotency(rows.getLong(1), null);
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id,request_hash,response_ref,expires_at FROM hhy.idempotency_records "
                            + "WHERE scope=? AND idem_key=? FOR UPDATE")) {
                select.setString(1, scope); select.setString(2, key);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) throw internal("红包幂等记录不存在");
                    long id = rows.getLong(1);
                    Instant expiresAt = instant(rows, 4);
                    if (expiresAt != null && !expiresAt.isAfter(now())) {
                        try (PreparedStatement remove = connection.prepareStatement(
                                "DELETE FROM hhy.idempotency_records WHERE id=?")) {
                            remove.setLong(1, id);
                            if (remove.executeUpdate() != 1) throw internal("红包过期幂等记录清理失败");
                        }
                        try (PreparedStatement retry = connection.prepareStatement(
                                "INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at) "
                                        + "VALUES (?,?,?,clock_timestamp()+interval '24 hours') RETURNING id")) {
                            retry.setString(1, scope); retry.setString(2, key); retry.setString(3, requestHash);
                            try (ResultSet inserted = retry.executeQuery()) {
                                if (!inserted.next()) throw internal("红包幂等记录创建失败");
                                return new Idempotency(inserted.getLong(1), null);
                            }
                        }
                    }
                    if (!requestHash.equals(rows.getString(2))) throw conflict("相同幂等键对应的红包请求已变化");
                    String response = rows.getString(3);
                    if (response == null) throw conflict("相同幂等键的红包请求正在处理中");
                    return new Idempotency(id, response);
                }
            }
        } catch (SQLException failure) { throw internal("红包幂等记录失败", failure); }
    }

    private void complete(Connection connection, long id, String responseRef) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE hhy.idempotency_records SET response_ref=? WHERE id=? AND response_ref IS NULL")) {
            statement.setString(1, responseRef); statement.setLong(2, id);
            if (statement.executeUpdate() != 1) throw internal("红包幂等结果写入失败");
        } catch (SQLException failure) { throw internal("红包幂等结果写入失败", failure); }
    }

    private void outbox(Connection connection, long id, String type, String eventType, Map<String, Object> payload) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hhy.outbox_events(aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload,status,attempts,available_at) VALUES (?,?,? ,?,1,'{}'::jsonb,?::jsonb,'PENDING',0,clock_timestamp())")) {
            statement.setString(1, Long.toString(id)); statement.setString(2, type);
            statement.setString(3, UUID.randomUUID().toString()); statement.setString(4, eventType);
            statement.setString(5, codec.json(payload)); statement.executeUpdate();
        } catch (SQLException failure) { throw internal("红包事件写入失败", failure); }
    }

    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit(); connection.setAutoCommit(false);
            try { T value = work.run(connection); connection.commit(); connection.setAutoCommit(previous); return value; }
            catch (RuntimeException failure) { rollback(connection); throw failure; }
            catch (SQLException failure) { rollback(connection); throw internal("红包事务失败", failure); }
        } catch (SQLException failure) { throw internal("红包数据库连接失败", failure); }
    }

    private <T> T read(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) { return work.run(connection); }
        catch (SQLException failure) { throw internal("红包数据库读取失败", failure); }
    }

    private static void rollback(Connection connection) {
        try { connection.rollback(); }
        catch (SQLException ignored) { /* preserve the original domain failure */ }
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Long number) statement.setLong(i + 1, number);
            else statement.setString(i + 1, value.toString());
        }
    }

    private Instant now() { return clock.instant(); }

    private static Object time(Instant instant) { return instant == null ? null : Timestamp.from(instant); }

    private static Instant instant(ResultSet rows, int index) throws SQLException {
        Object value = rows.getObject(index);
        if (value == null) return null;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return Instant.parse(value.toString());
    }

    private static String nullableLong(ResultSet rows, int index) throws SQLException {
        long value = rows.getLong(index); return rows.wasNull() ? null : Long.toString(value);
    }

    private static long parse(String value) {
        try { long result = Long.parseLong(value); if (result < 1) throw new NumberFormatException(); return result; }
        catch (NumberFormatException failure) { throw rule("标识不符合要求"); }
    }

    private static long fee(long principal) { return Math.multiplyExact(principal, SERVICE_FEE_BPS) / 10_000; }

    private static String userScope(UserCommand command) { return "r20rp:user:" + command.userId() + ":" + command.operationId(); }

    private static StoreException notFound(String message) { return new StoreException(Kind.NOT_FOUND, message); }
    private static StoreException conflict(String message) { return new StoreException(Kind.CONFLICT, message); }
    private static StoreException rule(String message) { return new StoreException(Kind.BUSINESS_RULE, message); }
    private static StoreException internal(String message) { return new StoreException(Kind.INTERNAL, message); }
    private static StoreException internal(String message, Throwable cause) { return new StoreException(Kind.INTERNAL, message, cause); }

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws SQLException; }

    private record CampaignHead(long id, String status, long totalCount, long amountPerClaimCent,
                                Instant startAt, Instant endAt, long version) { }
    private record QuoteHead(long id, long payable, Instant expiresAt, boolean hasOrder) { }
    private record Idempotency(long id, String responseRef) { }
}
