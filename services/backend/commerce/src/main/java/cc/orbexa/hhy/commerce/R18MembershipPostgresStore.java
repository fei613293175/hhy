package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R12MembershipContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipBenefitsPutRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipGrantRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipSkuPatchRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class R18MembershipPostgresStore implements R18MembershipStore {
    private static final String MEMBERSHIP_RESPONSE = "r18.MembershipResource.v1";
    private static final int MAX_BENEFITS = 100;
    private final DataSource dataSource;
    private final Codec codec;
    private final Clock clock;

    public R18MembershipPostgresStore(DataSource dataSource, Codec codec, Clock clock) {
        this.dataSource = dataSource;
        this.codec = codec;
        this.clock = clock;
    }

    @Override
    public PageSlice<MembershipResource> skus(PageQuery query) {
        StringBuilder where = new StringBuilder(" WHERE true");
        List<Object> values = new ArrayList<>();
        if (query.status() != null) {
            where.append(" AND product_sku.status=?");
            values.add(query.status());
        }
        if (query.keyword() != null) {
            where.append(" AND (product_sku.name ILIKE ? OR product_sku.code ILIKE ?)");
            values.add("%" + query.keyword() + "%");
            values.add("%" + query.keyword() + "%");
        }
        try (Connection connection = dataSource.getConnection()) {
            long total = count(connection, """
                    FROM hhy.membership_skus membership_sku
                    JOIN hhy.product_skus product_sku ON product_sku.id=membership_sku.sku_id
                    """ + where, values);
            String sql = """
                    SELECT membership_sku.id
                    FROM hhy.membership_skus membership_sku
                    JOIN hhy.product_skus product_sku ON product_sku.id=membership_sku.sku_id
                    """ + where + skuOrder(query.sort()) + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = bind(statement, values);
                statement.setInt(index++, query.pageSize());
                statement.setLong(index, query.offset());
                List<MembershipResource> result = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(sku(connection, rows.getLong(1), false));
                }
                return new PageSlice<>(result, total);
            }
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    @Override
    public Optional<MembershipResource> current(long userId) {
        try (Connection connection = dataSource.getConnection()) {
            return current(connection, userId, false);
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    @Override
    public CommandResultResource createPurchase(
            UserCommand context, long skuId, String paymentChannel, String requestHash) {
        return transaction(connection -> {
            ExistingOrder existing = existingOrder(
                    connection, context.userId(), "MEMBERSHIP", context.idempotencyKey());
            if (existing != null) return replay(existing, requestHash);
            SkuHead sku = activeSkuByProductId(connection, skuId, false);
            return createOrder(
                    connection, context, "MEMBERSHIP", sku,
                    sku.priceCent(), 0, sku.priceCent(),
                    paymentChannel, requestHash, null);
        });
    }

    @Override
    public MembershipResource createUpgradeQuote(
            UserCommand context, long targetSkuId, String requestHash) {
        return transaction(connection -> {
            MembershipResource replay = quoteByIdempotency(
                    connection, context.userId(), context.idempotencyKey(), requestHash);
            if (replay != null) return replay;
            MembershipHead membership = membershipHead(connection, context.userId(), true)
                    .orElseThrow(() -> notFound("当前账号没有可升级会员"));
            if (!"ACTIVE".equals(membership.status()) || !membership.endsAt().isAfter(now())) {
                throw rule("当前会员状态不允许升级");
            }
            SkuHead target = activeSkuByProductId(connection, targetSkuId, false);
            if (target.priceCent() <= 0) throw rule("免费会员SKU不支持补差升级");
            SkuHead current = latestSku(connection, context.userId())
                    .orElseThrow(() -> rule("当前会员缺少可升级的期限SKU"));
            if (target.termSeconds() <= current.termSeconds()) {
                throw rule("目标期限必须高于当前会员期限");
            }
            long remaining = remainingPaidValue(connection, context.userId());
            long payable = Math.max(0L, target.priceCent() - remaining);
            long id;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hhy.membership_upgrade_quotes(
                      user_id,target_sku_id,remaining_value,payable,expires_at,status,
                      membership_version,idempotency_key,request_hash,version)
                    VALUES (?,?,?,?,?,'OPEN',?,?,?,0) RETURNING id
                    """)) {
                statement.setLong(1, context.userId());
                statement.setLong(2, target.membershipSkuId());
                statement.setLong(3, remaining);
                statement.setLong(4, payable);
                statement.setObject(5, offset(now().plusSeconds(900)));
                statement.setLong(6, membership.version());
                statement.setString(7, context.idempotencyKey());
                statement.setString(8, requestHash);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    id = rows.getLong(1);
                }
            }
            MembershipResource result = quote(connection, id);
            outbox(connection, context.requestId(), context.userId(),
                    "membership.upgrade-quote.created.v1", "MEMBERSHIP_QUOTE", id, result);
            return result;
        });
    }

    @Override
    public CommandResultResource createUpgradeOrder(
            UserCommand context,
            long quoteId,
            String paymentChannel,
            String requestHash) {
        return transaction(connection -> {
            ExistingOrder existing = existingOrder(
                    connection, context.userId(), "MEMBERSHIP_UPGRADE", context.idempotencyKey());
            if (existing != null) return replay(existing, requestHash);
            QuoteHead quote = quoteHead(connection, context.userId(), quoteId, true);
            if (!"OPEN".equals(quote.status()) || !quote.expiresAt().isAfter(now())) {
                throw rule("升级报价已失效");
            }
            MembershipHead membership = membershipHead(connection, context.userId(), true)
                    .orElseThrow(() -> notFound("当前会员不存在"));
            if (membership.version() != quote.membershipVersion()) {
                throw conflict("会员状态已变化，请重新报价");
            }
            SkuHead target = skuHead(connection, quote.targetMembershipSkuId(), true);
            CommandResultResource result = createOrder(
                    connection, context, "MEMBERSHIP_UPGRADE", target,
                    target.priceCent(), target.priceCent() - quote.payable(), quote.payable(),
                    paymentChannel, requestHash, quoteId);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE hhy.membership_upgrade_quotes
                    SET status='CONSUMED',version=version+1
                    WHERE id=? AND status='OPEN' AND version=?
                    """)) {
                statement.setLong(1, quoteId);
                statement.setLong(2, quote.version());
                if (statement.executeUpdate() != 1) throw conflict("升级报价已被使用");
            }
            return result;
        });
    }

    @Override
    public PageSlice<MembershipResource> userMemberships(PageQuery query) {
        StringBuilder where = new StringBuilder(" WHERE true");
        List<Object> values = new ArrayList<>();
        if (query.status() != null) {
            where.append(" AND membership.status=?");
            values.add(query.status());
        }
        if (query.keyword() != null) {
            where.append(" AND (membership.user_id::text ILIKE ? OR plan.name ILIKE ?)");
            values.add("%" + query.keyword() + "%");
            values.add("%" + query.keyword() + "%");
        }
        try (Connection connection = dataSource.getConnection()) {
            String from = """
                    FROM hhy.user_memberships membership
                    JOIN hhy.membership_plans plan ON plan.id=membership.plan_id
                    """;
            long total = count(connection, from + where, values);
            String sql = "SELECT membership.user_id " + from + where
                    + membershipOrder(query.sort()) + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = bind(statement, values);
                statement.setInt(index++, query.pageSize());
                statement.setLong(index, query.offset());
                List<MembershipResource> result = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(current(connection, rows.getLong(1), false).orElseThrow());
                    }
                }
                return new PageSlice<>(result, total);
            }
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    @Override
    public MembershipResource patchSku(
            AdminCommand context,
            long membershipSkuId,
            MembershipSkuPatchRequest request,
            String requestHash) {
        return adminMutation(context, membershipSkuId, requestHash, connection -> {
            SkuHead before = skuHead(connection, membershipSkuId, true);
            requireVersion(before.version(), request.expectedVersion());
            updateSku(connection, before, request.name(), request.priceCent(),
                    request.durationDays(), request.status());
            if (request.benefits() != null) {
                replaceBenefits(connection, membershipSkuId, request.benefits());
            }
            return sku(connection, membershipSkuId, false);
        }, "membership.sku.updated.v1");
    }

    @Override
    public MembershipResource replaceBenefits(
            AdminCommand context,
            long membershipSkuId,
            MembershipBenefitsPutRequest request,
            String requestHash) {
        return adminMutation(context, membershipSkuId, requestHash, connection -> {
            SkuHead before = skuHead(connection, membershipSkuId, true);
            requireVersion(before.version(), request.expectedVersion());
            updateSku(connection, before, request.name(), request.priceCent(),
                    request.durationDays(), request.status());
            replaceBenefits(connection, membershipSkuId, request.benefits());
            return sku(connection, membershipSkuId, false);
        }, "membership.sku-benefits.replaced.v1");
    }

    @Override
    public MembershipResource grant(
            AdminCommand context, MembershipGrantRequest request, String requestHash) {
        long userId = Long.parseLong(request.userId());
        return adminMutation(context, userId, requestHash, connection -> {
            if (!exists(connection, "SELECT 1 FROM hhy.users WHERE id=?", userId)) {
                throw notFound("用户不存在");
            }
            SkuHead sku = request.skuId() == null
                    ? defaultActiveSku(connection)
                    : activeSkuByProductId(connection, Long.parseLong(request.skuId()), true);
            long durationSeconds = request.durationDays() == null
                    ? sku.termSeconds() : Math.multiplyExact(request.durationDays(), 86_400L);
            Instant start = now();
            Optional<MembershipHead> existing = membershipHead(connection, userId, true);
            long membershipId;
            if (existing.isPresent()) {
                MembershipHead current = existing.orElseThrow();
                start = current.endsAt().isAfter(start) ? current.endsAt() : start;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE hhy.user_memberships
                        SET plan_id=?,status='ACTIVE',starts_at=CASE WHEN status='ACTIVE' THEN starts_at ELSE ? END,
                            ends_at=?,version=version+1
                        WHERE id=? AND version=?
                        """)) {
                    statement.setLong(1, sku.planId());
                    statement.setObject(2, offset(now()));
                    statement.setObject(3, offset(start.plusSeconds(durationSeconds)));
                    statement.setLong(4, current.id());
                    statement.setLong(5, current.version());
                    if (statement.executeUpdate() != 1) throw conflict("用户会员已变化");
                    membershipId = current.id();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO hhy.user_memberships(
                          user_id,plan_id,status,starts_at,ends_at,version)
                        VALUES (?,?,'ACTIVE',?,?,0) RETURNING id
                        """)) {
                    statement.setLong(1, userId);
                    statement.setLong(2, sku.planId());
                    statement.setObject(3, offset(now()));
                    statement.setObject(4, offset(start.plusSeconds(durationSeconds)));
                    try (ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        membershipId = rows.getLong(1);
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hhy.membership_entitlement_segments(
                      user_id,membership_sku_id,type,paid_amount,starts_at,ends_at,consumed,version)
                    VALUES (?,?,'GIFT',0,?,?,0,0)
                    """)) {
                statement.setLong(1, userId);
                statement.setLong(2, sku.membershipSkuId());
                statement.setObject(3, offset(start));
                statement.setObject(4, offset(start.plusSeconds(durationSeconds)));
                statement.executeUpdate();
            }
            MembershipResource result = current(connection, userId, false).orElseThrow();
            return result;
        }, "membership.granted.v1");
    }

    private <T> T adminMutation(
            AdminCommand context,
            long resourceId,
            String requestHash,
            Mutation<T> mutation,
            String eventType) {
        return transaction(connection -> {
            Claim claim = claim(connection, context, requestHash);
            if (claim.replay()) {
                verifyCompleted(claim);
                return cast(codec.membership(codec.decrypt(
                        context.scope(), context.idempotencyKey(), requestHash,
                        MEMBERSHIP_RESPONSE, claim.ciphertext())));
            }
            T result = mutation.run(connection);
            if (!(result instanceof MembershipResource membership)) {
                throw new StoreException(Kind.INTERNAL, "R18幂等响应类型错误");
            }
            audit(connection, context, context.operationId(), "membership",
                    resourceId, null, membership);
            complete(connection, claim, context, requestHash, membership);
            outbox(connection, context.requestId(), context.adminId(),
                    eventType, "MEMBERSHIP", resourceId, membership);
            return result;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private CommandResultResource createOrder(
            Connection connection,
            UserCommand context,
            String bizType,
            SkuHead sku,
            long original,
            long discount,
            long payable,
            String paymentChannel,
            String requestHash,
            Long quoteId) throws SQLException {
        String orderNo = "R18-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        long orderId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.orders(
                  order_no,user_id,biz_type,biz_id,amount_cent,status,currency,
                  no_refund_confirmed,no_refund_agreement_version,no_refund_confirmed_at,
                  idempotency_key,request_hash,legacy_without_idempotency,version)
                VALUES (?,?,?,?,?,'PENDING_PAYMENT','CNY',true,'membership-no-refund-v1',?,
                        ?,?,false,0) RETURNING id
                """)) {
            statement.setString(1, orderNo);
            statement.setLong(2, context.userId());
            statement.setString(3, bizType);
            statement.setLong(4, quoteId == null ? sku.membershipSkuId() : quoteId);
            statement.setLong(5, payable);
            statement.setObject(6, offset(now()));
            statement.setString(7, context.idempotencyKey());
            statement.setString(8, requestHash);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                orderId = rows.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.order_items(
                  order_id,sku_id,item_name,quantity,unit_price,subtotal_amount_cent,snapshot_json)
                VALUES (?,?,?,1,?,?,jsonb_build_object(
                  'name',?,'skuCode',?,'paymentChannel',?,'membershipSkuId',?))
                """)) {
            statement.setLong(1, orderId);
            statement.setLong(2, sku.productSkuId());
            statement.setString(3, sku.name());
            statement.setLong(4, original);
            statement.setLong(5, original);
            statement.setString(6, sku.name());
            statement.setString(7, sku.code());
            statement.setString(8, paymentChannel);
            statement.setLong(9, sku.membershipSkuId());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.order_price_snapshots(
                  order_id,original,discount,service_fee,payable,rule_versions,rule_versions_json)
                VALUES (?,?,?,0,?,'membership-price-v1','["membership-price-v1"]')
                """)) {
            statement.setLong(1, orderId);
            statement.setLong(2, original);
            statement.setLong(3, discount);
            statement.setLong(4, payable);
            statement.executeUpdate();
        }
        List<BenefitResource> benefits = benefits(connection, sku.membershipSkuId());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.membership_benefit_snapshots(
                  order_id,benefits_json,reward_matrix_json,snapshot_version)
                VALUES (?,CAST(? AS jsonb),'{}'::jsonb,1)
                """)) {
            statement.setLong(1, orderId);
            statement.setString(2, codec.json(benefits));
            statement.executeUpdate();
        }
        CommandResultResource result = new CommandResultResource(
                Long.toString(orderId), orderNo, "PENDING_PAYMENT", 0, now());
        outbox(connection, context.requestId(), context.userId(),
                "membership.order.created.v1", "ORDER", orderId, result);
        return result;
    }

    private Optional<MembershipResource> current(
            Connection connection, long userId, boolean lock) throws SQLException {
        Optional<MembershipHead> head = membershipHead(connection, userId, lock);
        if (head.isEmpty()) return Optional.empty();
        MembershipHead membership = head.orElseThrow();
        Optional<SkuHead> sku = latestSku(connection, userId);
        List<BenefitResource> benefits = historicalBenefits(connection, userId)
                .orElseGet(() -> sku.isEmpty() ? List.of() : uncheckedBenefits(
                        connection, sku.orElseThrow().membershipSkuId()));
        Values values = values(connection, userId);
        return Optional.of(new MembershipResource(
                Long.toString(membership.id()),
                sku.map(value -> Long.toString(value.productSkuId())).orElse(null),
                membership.name(), membership.status(), membership.startsAt(), membership.endsAt(),
                benefits, values.paid(), values.remaining(), membership.version()));
    }

    private MembershipResource sku(
            Connection connection, long membershipSkuId, boolean lock) throws SQLException {
        SkuHead sku = skuHead(connection, membershipSkuId, lock);
        return new MembershipResource(
                Long.toString(sku.membershipSkuId()), Long.toString(sku.productSkuId()),
                sku.name(), sku.status(), null, null, benefits(connection, membershipSkuId),
                sku.priceCent(), null, sku.version());
    }

    private MembershipResource quote(Connection connection, long quoteId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT quote.id,quote.status,quote.remaining_value,quote.payable,quote.version,
                       product_sku.id AS product_sku_id,product_sku.name,
                       membership_sku.id AS membership_sku_id
                FROM hhy.membership_upgrade_quotes quote
                JOIN hhy.membership_skus membership_sku ON membership_sku.id=quote.target_sku_id
                JOIN hhy.product_skus product_sku ON product_sku.id=membership_sku.sku_id
                WHERE quote.id=?
                """)) {
            statement.setLong(1, quoteId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("升级报价不存在");
                return new MembershipResource(
                        Long.toString(rows.getLong("id")),
                        Long.toString(rows.getLong("product_sku_id")),
                        rows.getString("name"), rows.getString("status"), null, null,
                        benefits(connection, rows.getLong("membership_sku_id")),
                        rows.getLong("payable"), rows.getLong("remaining_value"),
                        rows.getLong("version"));
            }
        }
    }

    private MembershipResource quoteByIdempotency(
            Connection connection, long userId, String key, String requestHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,request_hash FROM hhy.membership_upgrade_quotes
                WHERE user_id=? AND idempotency_key=? FOR UPDATE
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                if (!requestHash.equals(rows.getString("request_hash"))) {
                    throw conflict("幂等键已绑定其他升级报价请求");
                }
                return quote(connection, rows.getLong("id"));
            }
        }
    }

    private Optional<MembershipHead> membershipHead(
            Connection connection, long userId, boolean lock) throws SQLException {
        String sql = """
                SELECT membership.id,plan.id AS plan_id,plan.name,membership.status,
                       membership.starts_at,membership.ends_at,membership.version
                FROM hhy.user_memberships membership
                JOIN hhy.membership_plans plan ON plan.id=membership.plan_id
                WHERE membership.user_id=?
                """ + (lock ? " FOR UPDATE OF membership" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new MembershipHead(
                        rows.getLong("id"), rows.getLong("plan_id"), rows.getString("name"),
                        rows.getString("status"), instant(rows, "starts_at"),
                        instant(rows, "ends_at"), rows.getLong("version")));
            }
        }
    }

    private SkuHead activeSkuByProductId(
            Connection connection, long productSkuId, boolean lock) throws SQLException {
        String sql = """
                SELECT membership_sku.id
                FROM hhy.membership_skus membership_sku
                JOIN hhy.membership_plans plan ON plan.id=membership_sku.plan_id
                JOIN hhy.product_skus product_sku ON product_sku.id=membership_sku.sku_id
                WHERE product_sku.id=? AND product_sku.status='ACTIVE' AND plan.status='ACTIVE'
                """ + (lock ? " FOR UPDATE OF membership_sku,product_sku" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productSkuId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("会员SKU不存在或未上架");
                return skuHead(connection, rows.getLong(1), lock);
            }
        }
    }

    private SkuHead defaultActiveSku(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT membership_sku.id
                FROM hhy.membership_skus membership_sku
                JOIN hhy.membership_plans plan ON plan.id=membership_sku.plan_id
                JOIN hhy.product_skus product_sku ON product_sku.id=membership_sku.sku_id
                WHERE product_sku.status='ACTIVE' AND plan.status='ACTIVE'
                ORDER BY membership_sku.term_seconds,membership_sku.id LIMIT 1
                FOR UPDATE OF membership_sku,product_sku
                """)) {
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("没有可赠送的会员SKU");
                return skuHead(connection, rows.getLong(1), true);
            }
        }
    }

    private SkuHead skuHead(Connection connection, long id, boolean lock) throws SQLException {
        String sql = """
                SELECT membership_sku.id AS membership_sku_id,membership_sku.plan_id,
                       membership_sku.term_seconds,membership_sku.version,
                       product_sku.id AS product_sku_id,product_sku.code,product_sku.name,
                       product_sku.price_cent,product_sku.status
                FROM hhy.membership_skus membership_sku
                JOIN hhy.product_skus product_sku ON product_sku.id=membership_sku.sku_id
                WHERE membership_sku.id=?
                """ + (lock ? " FOR UPDATE OF membership_sku,product_sku" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("会员SKU不存在");
                return new SkuHead(
                        rows.getLong("membership_sku_id"), rows.getLong("plan_id"),
                        rows.getLong("product_sku_id"), rows.getString("code"),
                        rows.getString("name"), rows.getLong("price_cent"),
                        rows.getLong("term_seconds"), rows.getString("status"),
                        rows.getLong("version"));
            }
        }
    }

    private Optional<SkuHead> latestSku(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT membership_sku_id FROM hhy.membership_entitlement_segments
                WHERE user_id=? AND membership_sku_id IS NOT NULL
                ORDER BY ends_at DESC,id DESC LIMIT 1
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? Optional.of(skuHead(connection, rows.getLong(1), false))
                        : Optional.empty();
            }
        }
    }

    private List<BenefitResource> benefits(Connection connection, long membershipSkuId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT benefit.code,benefit.description,sku_benefit.value_json::text
                FROM hhy.membership_sku_benefits sku_benefit
                JOIN hhy.membership_benefits benefit ON benefit.id=sku_benefit.benefit_id
                WHERE sku_benefit.membership_sku_id=? AND sku_benefit.enabled=true
                ORDER BY benefit.code,sku_benefit.id LIMIT 101
                """)) {
            statement.setLong(1, membershipSkuId);
            try (ResultSet rows = statement.executeQuery()) {
                List<BenefitResource> result = new ArrayList<>();
                while (rows.next()) {
                    if (result.size() == MAX_BENEFITS) throw invalid("会员权益超过合同上限");
                    String code = rows.getString(1);
                    String description = rows.getString(2);
                    String name = description == null || description.isBlank()
                            || description.length() > 120 ? code : description;
                    result.add(new BenefitResource(code, name, codec.value(rows.getString(3)), null));
                }
                return List.copyOf(result);
            }
        }
    }

    private List<BenefitResource> uncheckedBenefits(Connection connection, long membershipSkuId) {
        try {
            return benefits(connection, membershipSkuId);
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    private Optional<List<BenefitResource>> historicalBenefits(Connection connection, long userId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT snapshot.benefits_json::text
                FROM hhy.membership_entitlement_segments segment
                JOIN hhy.membership_benefit_snapshots snapshot ON snapshot.order_id=segment.source_order_id
                WHERE segment.user_id=? AND segment.source_order_id IS NOT NULL
                ORDER BY segment.ends_at DESC,segment.id DESC LIMIT 1
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(codec.benefits(rows.getString(1))) : Optional.empty();
            }
        }
    }

    private Values values(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) AS segment_count,
                       COALESCE(sum(paid_amount),0) AS paid,
                       COALESCE(sum(paid_amount-consumed),0) AS remaining
                FROM hhy.membership_entitlement_segments WHERE user_id=?
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong("segment_count") == 0
                        ? new Values(null, null)
                        : new Values(rows.getLong("paid"), rows.getLong("remaining"));
            }
        }
    }

    private long remainingPaidValue(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(sum(paid_amount-consumed),0)
                FROM hhy.membership_entitlement_segments
                WHERE user_id=? AND type='PAID' AND ends_at>clock_timestamp()
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private QuoteHead quoteHead(
            Connection connection, long userId, long quoteId, boolean lock) throws SQLException {
        String sql = """
                SELECT id,target_sku_id,remaining_value,payable,expires_at,status,
                       membership_version,version
                FROM hhy.membership_upgrade_quotes WHERE id=? AND user_id=?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, quoteId);
            statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("升级报价不存在");
                return new QuoteHead(
                        rows.getLong("id"), rows.getLong("target_sku_id"),
                        rows.getLong("remaining_value"), rows.getLong("payable"),
                        instant(rows, "expires_at"), rows.getString("status"),
                        rows.getLong("membership_version"), rows.getLong("version"));
            }
        }
    }

    private void updateSku(
            Connection connection,
            SkuHead before,
            String name,
            Long priceCent,
            Long durationDays,
            String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE hhy.product_skus
                SET name=COALESCE(?,name),price_cent=COALESCE(?,price_cent),
                    duration_days=COALESCE(?,duration_days),status=COALESCE(?,status),
                    version=version+1
                WHERE id=?
                """)) {
            nullable(statement, 1, name, Types.VARCHAR);
            nullable(statement, 2, priceCent, Types.BIGINT);
            nullable(statement, 3, durationDays, Types.BIGINT);
            nullable(statement, 4, status, Types.VARCHAR);
            statement.setLong(5, before.productSkuId());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE hhy.membership_skus
                SET term_seconds=COALESCE(?,term_seconds),version=version+1
                WHERE id=? AND version=?
                """)) {
            Long seconds = durationDays == null ? null : Math.multiplyExact(durationDays, 86_400L);
            nullable(statement, 1, seconds, Types.BIGINT);
            statement.setLong(2, before.membershipSkuId());
            statement.setLong(3, before.version());
            if (statement.executeUpdate() != 1) throw conflict("会员SKU已被其他请求更新");
        }
    }

    private void replaceBenefits(
            Connection connection, long membershipSkuId, Map<String, Object> benefits)
            throws SQLException {
        for (String code : benefits.keySet()) {
            if (!exists(connection,
                    "SELECT 1 FROM hhy.membership_benefits WHERE code=?", code)) {
                throw rule("权益代码不存在：" + code);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM hhy.membership_sku_benefits WHERE membership_sku_id=?")) {
            statement.setLong(1, membershipSkuId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.membership_sku_benefits(
                  membership_sku_id,benefit_id,value_json,enabled)
                SELECT ?,id,CAST(? AS jsonb),true FROM hhy.membership_benefits WHERE code=?
                """)) {
            for (Map.Entry<String, Object> benefit : benefits.entrySet()) {
                statement.setLong(1, membershipSkuId);
                statement.setString(2, codec.json(benefit.getValue()));
                statement.setString(3, benefit.getKey());
                if (statement.executeUpdate() != 1) throw rule("权益代码不存在：" + benefit.getKey());
            }
        }
    }

    private ExistingOrder existingOrder(
            Connection connection, long userId, String bizType, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,order_no,status,version,request_hash,created_at
                FROM hhy.orders
                WHERE user_id=? AND biz_type=? AND idempotency_key=?
                FOR UPDATE
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, bizType);
            statement.setString(3, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new ExistingOrder(
                        rows.getLong("id"), rows.getString("order_no"), rows.getString("status"),
                        rows.getLong("version"), rows.getString("request_hash"),
                        instant(rows, "created_at")) : null;
            }
        }
    }

    private static CommandResultResource replay(ExistingOrder existing, String requestHash) {
        if (!requestHash.equals(existing.requestHash())) {
            throw conflict("幂等键已绑定其他订单请求");
        }
        return new CommandResultResource(
                Long.toString(existing.id()), existing.orderNo(), existing.status(),
                existing.version(), existing.createdAt());
    }

    private Claim claim(Connection connection, AdminCommand context, String requestHash)
            throws SQLException {
        OffsetDateTime now = offset(now());
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at<=?
                """)) {
            statement.setString(1, context.scope());
            statement.setString(2, context.idempotencyKey());
            statement.setObject(3, now);
            statement.executeUpdate();
        }
        boolean inserted;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at)
                VALUES (?,?,?,?) ON CONFLICT(scope,idem_key) DO NOTHING RETURNING id
                """)) {
            statement.setString(1, context.scope());
            statement.setString(2, context.idempotencyKey());
            statement.setString(3, requestHash);
            statement.setObject(4, offset(now().plusSeconds(86_400)));
            try (ResultSet rows = statement.executeQuery()) {
                inserted = rows.next();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,request_hash,response_ref,response_type,response_payload_ciphertext
                FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at>? FOR UPDATE
                """)) {
            statement.setString(1, context.scope());
            statement.setString(2, context.idempotencyKey());
            statement.setObject(3, now);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StoreException(Kind.INTERNAL, "R18幂等记录消失");
                Claim claim = new Claim(
                        rows.getLong(1), rows.getString(2), rows.getString(3),
                        rows.getString(4), rows.getString(5), !inserted);
                if (!requestHash.equals(claim.requestHash())) throw conflict("幂等键已绑定其他请求");
                return claim;
            }
        }
    }

    private void complete(
            Connection connection,
            Claim claim,
            AdminCommand context,
            String requestHash,
            MembershipResource result) throws SQLException {
        String encrypted = codec.encrypt(
                context.scope(), context.idempotencyKey(), requestHash,
                MEMBERSHIP_RESPONSE, codec.json(result).getBytes(StandardCharsets.UTF_8));
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE hhy.idempotency_records
                SET response_ref=?,response_type=?,response_payload_ciphertext=?
                WHERE id=? AND response_ref IS NULL AND response_type IS NULL
                  AND response_payload_ciphertext IS NULL
                """)) {
            statement.setString(1, MEMBERSHIP_RESPONSE + ":ok");
            statement.setString(2, MEMBERSHIP_RESPONSE);
            statement.setString(3, encrypted);
            statement.setLong(4, claim.id());
            if (statement.executeUpdate() != 1) throw conflict("幂等响应已被其他请求完成");
        }
    }

    private static void verifyCompleted(Claim claim) {
        if (!(MEMBERSHIP_RESPONSE + ":ok").equals(claim.responseRef())
                || !MEMBERSHIP_RESPONSE.equals(claim.responseType())
                || claim.ciphertext() == null) {
            throw conflict("幂等请求仍在处理中");
        }
    }

    private void audit(
            Connection connection,
            AdminCommand context,
            String action,
            String resource,
            long resourceId,
            Object before,
            Object after) throws SQLException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("resource", after);
        envelope.put("operationId", context.operationId());
        envelope.put("requestId", context.requestId());
        envelope.put("sessionId", context.sessionId());
        envelope.put("username", context.username());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,before_json,after_json,ip)
                VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                """)) {
            statement.setLong(1, context.adminId());
            statement.setString(2, action);
            statement.setString(3, resource);
            statement.setLong(4, resourceId);
            nullable(statement, 5, before == null ? null : codec.json(before), Types.VARCHAR);
            statement.setString(6, codec.json(envelope));
            statement.setString(7, context.ip());
            statement.executeUpdate();
        }
    }

    private void outbox(
            Connection connection,
            String requestId,
            long actorId,
            String eventType,
            String aggregateType,
            long aggregateId,
            Object resource) throws SQLException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", Long.toString(aggregateId));
        payload.put("requestId", requestId);
        payload.put("actorId", actorId);
        payload.put("resource", resource);
        payload.put("occurredAt", now());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload)
                VALUES (?,?,?,?,1,jsonb_build_object('source','r18-membership-api','requestId',?),
                        CAST(? AS jsonb))
                """)) {
            statement.setString(1, Long.toString(aggregateId));
            statement.setString(2, aggregateType);
            statement.setString(3, UUID.randomUUID().toString());
            statement.setString(4, eventType);
            statement.setString(5, requestId);
            statement.setString(6, codec.json(payload));
            statement.executeUpdate();
        }
    }

    private static long count(Connection connection, String from, List<Object> values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) " + from)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static int bind(PreparedStatement statement, List<Object> values) throws SQLException {
        int index = 1;
        for (Object value : values) statement.setObject(index++, value);
        return index;
    }

    private static boolean exists(Connection connection, String sql, Object value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static String skuOrder(String sort) {
        return switch (sort) {
            case "createdAt:asc" -> " ORDER BY product_sku.created_at ASC,product_sku.id ASC";
            case "createdAt:desc" -> " ORDER BY product_sku.created_at DESC,product_sku.id DESC";
            case "updatedAt:asc" -> " ORDER BY product_sku.updated_at ASC,product_sku.id ASC";
            case "updatedAt:desc" -> " ORDER BY product_sku.updated_at DESC,product_sku.id DESC";
            case "name:asc" -> " ORDER BY product_sku.name ASC,product_sku.id ASC";
            case "name:desc" -> " ORDER BY product_sku.name DESC,product_sku.id DESC";
            case "priceCent:desc" -> " ORDER BY product_sku.price_cent DESC,product_sku.id DESC";
            default -> " ORDER BY product_sku.price_cent ASC,product_sku.id ASC";
        };
    }

    private static String membershipOrder(String sort) {
        return switch (sort) {
            case "createdAt:asc" -> " ORDER BY membership.created_at ASC,membership.id ASC";
            case "createdAt:desc" -> " ORDER BY membership.created_at DESC,membership.id DESC";
            case "updatedAt:asc" -> " ORDER BY membership.updated_at ASC,membership.id ASC";
            case "expiresAt:asc" -> " ORDER BY membership.ends_at ASC,membership.id ASC";
            case "expiresAt:desc" -> " ORDER BY membership.ends_at DESC,membership.id DESC";
            default -> " ORDER BY membership.updated_at DESC,membership.id DESC";
        };
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) throw conflict("会员SKU版本已变化");
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private <T> T transaction(Transaction<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception failure) {
                connection.rollback();
                if (failure instanceof StoreException storeFailure) throw storeFailure;
                if (failure instanceof SQLException sqlFailure) throw sql(sqlFailure);
                throw new StoreException(Kind.INTERNAL, "R18会员事务失败", failure);
            }
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    private static StoreException sql(SQLException failure) {
        String state = failure.getSQLState();
        if ("23505".equals(state) || "40001".equals(state)) {
            return conflict("会员资源或幂等键冲突", failure);
        }
        if ("23514".equals(state) || "23503".equals(state) || "22003".equals(state)) {
            return new StoreException(Kind.BUSINESS_RULE, "会员数据不满足业务约束", failure);
        }
        return new StoreException(Kind.INTERNAL, "R18会员存储失败", failure);
    }

    private static StoreException notFound(String message) {
        return new StoreException(Kind.NOT_FOUND, message);
    }

    private static StoreException conflict(String message) {
        return new StoreException(Kind.CONFLICT, message);
    }

    private static StoreException conflict(String message, Throwable cause) {
        return new StoreException(Kind.CONFLICT, message, cause);
    }

    private static StoreException rule(String message) {
        return new StoreException(Kind.BUSINESS_RULE, message);
    }

    private static StoreException invalid(String message) {
        return new StoreException(Kind.INVALID_DATA, message);
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void nullable(
            PreparedStatement statement, int index, Object value, int type) throws SQLException {
        if (value == null) statement.setNull(index, type);
        else statement.setObject(index, value);
    }

    @FunctionalInterface
    private interface Transaction<T> {
        T run(Connection connection) throws Exception;
    }

    @FunctionalInterface
    private interface Mutation<T> {
        T run(Connection connection) throws Exception;
    }

    private record SkuHead(
            long membershipSkuId,
            long planId,
            long productSkuId,
            String code,
            String name,
            long priceCent,
            long termSeconds,
            String status,
            long version) { }

    private record MembershipHead(
            long id,
            long planId,
            String name,
            String status,
            Instant startsAt,
            Instant endsAt,
            long version) { }

    private record QuoteHead(
            long id,
            long targetMembershipSkuId,
            long remaining,
            long payable,
            Instant expiresAt,
            String status,
            long membershipVersion,
            long version) { }

    private record ExistingOrder(
            long id,
            String orderNo,
            String status,
            long version,
            String requestHash,
            Instant createdAt) { }

    private record Claim(
            long id,
            String requestHash,
            String responseRef,
            String responseType,
            String ciphertext,
            boolean replay) { }

    private record Values(Long paid, Long remaining) { }
}
