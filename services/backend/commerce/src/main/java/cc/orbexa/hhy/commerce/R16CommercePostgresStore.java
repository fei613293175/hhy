package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R16CommerceContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.NoRefundEvidenceResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderItemResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderPriceSnapshotResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuResource;
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

public final class R16CommercePostgresStore implements R16CommerceStore {
    private static final int CONTRACT_LIMIT = 100;
    private static final String PRODUCT_RESPONSE = "r16.ProductResource.v1";
    private static final String SKU_RESPONSE = "r16.ProductSkuResource.v1";
    private final DataSource dataSource;
    private final Codec codec;
    private final Clock clock;
    private final FaultInjector faults;

    public R16CommercePostgresStore(DataSource dataSource, Codec codec, Clock clock) {
        this(dataSource, codec, clock, FaultInjector.NONE);
    }

    public R16CommercePostgresStore(
            DataSource dataSource, Codec codec, Clock clock, FaultInjector faults) {
        this.dataSource = dataSource;
        this.codec = codec;
        this.clock = clock;
        this.faults = faults;
    }

    @Override
    public PageSlice<ProductResource> products(PageQuery query) {
        return read(connection -> {
            Filter filter = filter(query, "product");
            List<ProductResource> result = new ArrayList<>();
            String sql = """
                    SELECT product.id
                    FROM hhy.products product
                    """ + filter.where() + " ORDER BY " + productSort(query.sort())
                    + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = bind(statement, filter.args(), 1);
                statement.setInt(index++, query.pageSize());
                statement.setLong(index, query.offset());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(product(connection, rows.getLong(1)));
                }
            }
            return new PageSlice<>(result, count(connection, "hhy.products product", filter));
        });
    }

    @Override
    public PageSlice<ProductSkuResource> skus(PageQuery query) {
        return read(connection -> {
            Filter filter = filter(query, "sku");
            List<ProductSkuResource> result = new ArrayList<>();
            String sql = """
                    SELECT sku.id
                    FROM hhy.product_skus sku
                    """ + filter.where() + " ORDER BY " + skuSort(query.sort())
                    + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = bind(statement, filter.args(), 1);
                statement.setInt(index++, query.pageSize());
                statement.setLong(index, query.offset());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(sku(connection, rows.getLong(1)));
                }
            }
            return new PageSlice<>(result, count(connection, "hhy.product_skus sku", filter));
        });
    }

    @Override
    public PageSlice<OrderResource> orders(Long ownerUserId, PageQuery query) {
        return read(connection -> {
            Filter filter = orderFilter(ownerUserId, query);
            List<OrderResource> result = new ArrayList<>();
            String sql = """
                    SELECT orders.id
                    FROM hhy.orders orders
                    """ + filter.where() + " ORDER BY " + orderSort(query.sort())
                    + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = bind(statement, filter.args(), 1);
                statement.setInt(index++, query.pageSize());
                statement.setLong(index, query.offset());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(order(connection, rows.getLong(1)));
                }
            }
            return new PageSlice<>(result, count(connection, "hhy.orders orders", filter));
        });
    }

    @Override
    public Optional<OrderResource> order(Long ownerUserId, String orderNo) {
        return read(connection -> {
            String sql = """
                    SELECT id FROM hhy.orders
                    WHERE order_no=? AND (? IS NULL OR user_id=?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, orderNo);
                if (ownerUserId == null) {
                    statement.setNull(2, Types.BIGINT);
                    statement.setNull(3, Types.BIGINT);
                } else {
                    statement.setLong(2, ownerUserId);
                    statement.setLong(3, ownerUserId);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    return Optional.of(order(connection, rows.getLong(1)));
                }
            }
        });
    }

    @Override
    public ProductResource createProduct(
            CommandContext context, ProductCreateRequest request, String requestHash) {
        return write(connection -> {
            Claim claim = claim(connection, context, requestHash);
            if (claim.replay()) return replayProduct(claim, context, requestHash);
            long id;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hhy.products(
                      product_code,type,name,description,status,display_order,version
                    ) VALUES (?,?,?,?,?,?,0)
                    RETURNING id
                    """)) {
                statement.setString(1, request.productCode());
                statement.setString(2, request.productType());
                statement.setString(3, request.name());
                statement.setString(4, request.description());
                statement.setString(5, request.status());
                nullableInteger(statement, 6, request.displayOrder());
                id = returnedId(statement, "R16 product insert returned no id");
            }
            faults.at("AFTER_BUSINESS_WRITE");
            ProductResource result = product(connection, id);
            audit(connection, context, "PRODUCT_CREATE", "PRODUCT", id, null, codec.json(result));
            faults.at("AFTER_AUDIT");
            outbox(connection, context, "commerce.product.created.v1", "PRODUCT", id, result);
            faults.at("AFTER_OUTBOX");
            complete(connection, claim, context, requestHash, PRODUCT_RESPONSE, codec.json(result));
            faults.at("AFTER_SNAPSHOT");
            return result;
        });
    }

    @Override
    public ProductResource patchProduct(
            CommandContext context, long productId, ProductPatchRequest request, String requestHash) {
        return write(connection -> {
            Claim claim = claim(connection, context, requestHash);
            if (claim.replay()) return replayProduct(claim, context, requestHash);
            lock(connection, "hhy.products", productId);
            ProductResource before = product(connection, productId);
            if (before.version() != request.expectedVersion()) throw conflict("商品版本已变化");
            String name = request.name() == null ? before.name() : request.name();
            String type = request.productType() == null ? before.productType() : request.productType();
            String description = request.description() == null ? before.description() : request.description();
            Integer displayOrder =
                    request.displayOrder() == null ? before.displayOrder() : request.displayOrder();
            String status = request.status() == null ? before.status() : request.status();
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE hhy.products
                    SET name=?,type=?,description=?,display_order=?,status=?,version=version+1
                    WHERE id=? AND version=?
                    """)) {
                statement.setString(1, name);
                statement.setString(2, type);
                statement.setString(3, description);
                nullableInteger(statement, 4, displayOrder);
                statement.setString(5, status);
                statement.setLong(6, productId);
                statement.setLong(7, request.expectedVersion());
                if (statement.executeUpdate() != 1) throw conflict("商品版本已变化");
            }
            faults.at("AFTER_BUSINESS_WRITE");
            ProductResource result = product(connection, productId);
            audit(connection, context, "PRODUCT_UPDATE", "PRODUCT", productId,
                    codec.json(before), codec.json(result));
            faults.at("AFTER_AUDIT");
            outbox(connection, context, "commerce.product.updated.v1", "PRODUCT", productId, result);
            faults.at("AFTER_OUTBOX");
            complete(connection, claim, context, requestHash, PRODUCT_RESPONSE, codec.json(result));
            faults.at("AFTER_SNAPSHOT");
            return result;
        });
    }

    @Override
    public ProductSkuResource createSku(
            CommandContext context, ProductSkuCreateRequest request, String requestHash) {
        return write(connection -> {
            Claim claim = claim(connection, context, requestHash);
            if (claim.replay()) return replaySku(claim, context, requestHash);
            long productId = Long.parseLong(request.productId());
            lock(connection, "hhy.products", productId);
            if (scalar(connection,
                    "SELECT count(*) FROM hhy.product_skus WHERE product_id=?", productId) >= CONTRACT_LIMIT) {
                throw business("单个商品最多允许100个SKU");
            }
            String benefitsJson = codec.json(request.benefits());
            long skuId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hhy.product_skus(
                      product_id,code,price_cent,duration,attributes_json,status,
                      name,member_price_cent,duration_days,benefits_json,
                      sale_starts_at,sale_ends_at,version
                    ) VALUES (
                      ?,?,?,?,CAST(? AS jsonb),?,?,?,?,CAST(? AS jsonb),?,?,0
                    )
                    RETURNING id
                    """)) {
                statement.setLong(1, productId);
                statement.setString(2, request.skuCode());
                statement.setLong(3, request.priceCent());
                nullableInteger(statement, 4, exactInteger(request.durationDays(), "SKU有效天数超出范围"));
                statement.setString(5, codec.json(Map.of(
                        "name", request.name(), "benefits", request.benefits())));
                statement.setString(6, request.status());
                statement.setString(7, request.name());
                nullableLong(statement, 8, request.memberPriceCent());
                nullableInteger(statement, 9, exactInteger(request.durationDays(), "SKU有效天数超出范围"));
                statement.setString(10, benefitsJson);
                nullableInstant(statement, 11, request.saleStartsAt());
                nullableInstant(statement, 12, request.saleEndsAt());
                skuId = returnedId(statement, "R16 SKU insert returned no id");
            }
            upsertCommission(connection, skuId, request.commissionEnabled(),
                    request.level1Bps(), request.level2Bps());
            faults.at("AFTER_BUSINESS_WRITE");
            ProductSkuResource result = sku(connection, skuId);
            audit(connection, context, "SKU_CREATE", "PRODUCT_SKU", skuId, null, codec.json(result));
            faults.at("AFTER_AUDIT");
            outbox(connection, context, "commerce.sku.created.v1", "PRODUCT_SKU", skuId, result);
            faults.at("AFTER_OUTBOX");
            complete(connection, claim, context, requestHash, SKU_RESPONSE, codec.json(result));
            faults.at("AFTER_SNAPSHOT");
            return result;
        });
    }

    @Override
    public ProductSkuResource patchSku(
            CommandContext context, long skuId, ProductSkuPatchRequest request, String requestHash) {
        return write(connection -> {
            Claim claim = claim(connection, context, requestHash);
            if (claim.replay()) return replaySku(claim, context, requestHash);
            lock(connection, "hhy.product_skus", skuId);
            ProductSkuResource before = sku(connection, skuId);
            if (before.version() != request.expectedVersion()) throw conflict("SKU版本已变化");
            String name = request.name() == null ? before.name() : request.name();
            long price = request.priceCent() == null ? before.priceCent() : request.priceCent();
            Long memberPrice = request.memberPriceCent() == null
                    ? before.memberPriceCent() : request.memberPriceCent();
            Long duration = request.durationDays() == null ? before.durationDays() : request.durationDays();
            List<BenefitResource> benefits =
                    request.benefits() == null ? before.benefits() : request.benefits();
            boolean commission = request.commissionEnabled() == null
                    ? before.commissionEnabled() : request.commissionEnabled();
            int level1 = commission
                    ? (request.level1Bps() == null ? zero(before.level1Bps()) : request.level1Bps())
                    : 0;
            int level2 = commission
                    ? (request.level2Bps() == null ? zero(before.level2Bps()) : request.level2Bps())
                    : 0;
            Instant starts = request.saleStartsAt() == null ? before.saleStartsAt() : request.saleStartsAt();
            Instant ends = request.saleEndsAt() == null ? before.saleEndsAt() : request.saleEndsAt();
            String status = request.status() == null ? before.status() : request.status();
            String benefitsJson = codec.json(benefits);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE hhy.product_skus
                    SET name=?,price_cent=?,member_price_cent=?,duration=?,duration_days=?,
                        benefits_json=CAST(? AS jsonb),
                        attributes_json=COALESCE(attributes_json,'{}'::jsonb)
                          || jsonb_build_object('name',CAST(? AS text),'benefits',CAST(? AS jsonb)),
                        sale_starts_at=?,sale_ends_at=?,status=?,version=version+1
                    WHERE id=? AND version=?
                    """)) {
                statement.setString(1, name);
                statement.setLong(2, price);
                nullableLong(statement, 3, memberPrice);
                nullableInteger(statement, 4, exactInteger(duration, "SKU有效天数超出范围"));
                nullableInteger(statement, 5, exactInteger(duration, "SKU有效天数超出范围"));
                statement.setString(6, benefitsJson);
                statement.setString(7, name);
                statement.setString(8, benefitsJson);
                nullableInstant(statement, 9, starts);
                nullableInstant(statement, 10, ends);
                statement.setString(11, status);
                statement.setLong(12, skuId);
                statement.setLong(13, request.expectedVersion());
                if (statement.executeUpdate() != 1) throw conflict("SKU版本已变化");
            }
            upsertCommission(connection, skuId, commission, level1, level2);
            faults.at("AFTER_BUSINESS_WRITE");
            ProductSkuResource result = sku(connection, skuId);
            audit(connection, context, "SKU_UPDATE", "PRODUCT_SKU", skuId,
                    codec.json(before), codec.json(result));
            faults.at("AFTER_AUDIT");
            outbox(connection, context, "commerce.sku.updated.v1", "PRODUCT_SKU", skuId, result);
            faults.at("AFTER_OUTBOX");
            complete(connection, claim, context, requestHash, SKU_RESPONSE, codec.json(result));
            faults.at("AFTER_SNAPSHOT");
            return result;
        });
    }

    private ProductResource product(Connection connection, long id) throws SQLException {
        ProductHead head;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,product_code,name,type,description,display_order,status,version
                FROM hhy.products WHERE id=?
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("商品不存在");
                head = new ProductHead(
                        rows.getLong("id"), rows.getString("product_code"), rows.getString("name"),
                        rows.getString("type"), rows.getString("description"),
                        integer(rows, "display_order"), rows.getString("status"), rows.getLong("version"));
            }
        }
        List<ProductSkuResource> skus = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM hhy.product_skus
                WHERE product_id=? ORDER BY id LIMIT 101
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (skus.size() == CONTRACT_LIMIT) {
                        throw invalid("商品SKU数量超过冻结合同上限");
                    }
                    skus.add(sku(connection, rows.getLong(1)));
                }
            }
        }
        return new ProductResource(
                Long.toString(head.id()), head.code(), head.name(), head.type(),
                head.description(), head.displayOrder(), head.status(), skus, head.version());
    }

    private ProductSkuResource sku(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sku.id,sku.product_id,sku.code,sku.name,sku.price_cent,
                       sku.member_price_cent,sku.duration_days,sku.benefits_json::text,
                       COALESCE(policy.enabled,false) AS commission_enabled,
                       policy.level1_bps,policy.level2_bps,
                       sku.sale_starts_at,sku.sale_ends_at,sku.status,sku.version
                FROM hhy.product_skus sku
                LEFT JOIN hhy.sku_commission_policies policy ON policy.sku_id=sku.id
                WHERE sku.id=?
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("SKU不存在");
                List<BenefitResource> benefits = codec.benefits(rows.getString(8));
                if (benefits.size() > CONTRACT_LIMIT) throw invalid("SKU权益数量超过冻结合同上限");
                return new ProductSkuResource(
                        Long.toString(rows.getLong(1)), Long.toString(rows.getLong(2)),
                        rows.getString(3), rows.getString(4), rows.getLong(5),
                        longValue(rows, 6), longValue(rows, 7), benefits,
                        rows.getBoolean(9), integer(rows, 10), integer(rows, 11),
                        instant(rows, 12), instant(rows, 13), rows.getString(14), rows.getLong(15));
            }
        }
    }

    private OrderResource order(Connection connection, long id) throws SQLException {
        OrderHead head;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT order_no,user_id,biz_type,status,currency,paid_amount_cent,
                       created_at,paid_at,version,no_refund_confirmed,
                       no_refund_agreement_version,no_refund_confirmed_at
                FROM hhy.orders WHERE id=?
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("订单不存在");
                head = new OrderHead(
                        rows.getString(1), rows.getLong(2), rows.getString(3), rows.getString(4),
                        rows.getString(5), longValue(rows, 6), instant(rows, 7), instant(rows, 8),
                        rows.getLong(9), rows.getBoolean(10), rows.getString(11), instant(rows, 12));
            }
        }
        List<OrderItemResource> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sku_id,item_name,quantity,unit_price,subtotal_amount_cent
                FROM hhy.order_items WHERE order_id=? ORDER BY id LIMIT 101
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (items.size() == CONTRACT_LIMIT) {
                        throw invalid("订单项数量超过冻结合同上限");
                    }
                    Long skuId = longValue(rows, 1);
                    items.add(new OrderItemResource(
                            skuId == null ? null : Long.toString(skuId), rows.getString(2),
                            rows.getInt(3), rows.getLong(4), rows.getLong(5)));
                }
            }
        }
        List<OrderPriceSnapshotResource> snapshots = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT original,discount,service_fee,payable,rule_versions_json::text
                FROM hhy.order_price_snapshots WHERE order_id=? ORDER BY id LIMIT 2
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    snapshots.add(new OrderPriceSnapshotResource(
                            rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4),
                            codec.strings(rows.getString(5))));
                }
            }
        }
        if (snapshots.size() != 1) throw invalid("订单报价快照缺失或重复");
        String agreement = head.agreementVersion() == null ? "" : head.agreementVersion();
        return new OrderResource(
                head.orderNo(), Long.toString(head.userId()), head.type(), head.status(), head.currency(),
                items, snapshots.getFirst(),
                new NoRefundEvidenceResource(head.confirmed(), agreement, head.confirmedAt()),
                head.paidAmount(), head.createdAt(), head.paidAt(), head.version());
    }

    private Claim claim(Connection connection, CommandContext context, String requestHash) throws SQLException {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        try (PreparedStatement expired = connection.prepareStatement("""
                DELETE FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at<=?
                """)) {
            expired.setString(1, context.scope());
            expired.setString(2, context.idempotencyKey());
            expired.setObject(3, now);
            expired.executeUpdate();
        }
        boolean inserted;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at)
                VALUES (?,?,?,?)
                ON CONFLICT(scope,idem_key) DO NOTHING
                RETURNING id
                """)) {
            statement.setString(1, context.scope());
            statement.setString(2, context.idempotencyKey());
            statement.setString(3, requestHash);
            statement.setObject(4, OffsetDateTime.ofInstant(
                    Instant.now(clock).plusSeconds(86_400), ZoneOffset.UTC));
            try (ResultSet rows = statement.executeQuery()) {
                inserted = rows.next();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,request_hash,response_ref,response_type,response_payload_ciphertext
                FROM hhy.idempotency_records
                WHERE scope=? AND idem_key=? AND expires_at>?
                FOR UPDATE
                """)) {
            statement.setString(1, context.scope());
            statement.setString(2, context.idempotencyKey());
            statement.setObject(3, now);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StoreException(Kind.INTERNAL, "R16幂等记录消失");
                Claim result = new Claim(
                        rows.getLong(1), rows.getString(2), rows.getString(3),
                        rows.getString(4), rows.getString(5), !inserted);
                if (!requestHash.equals(result.requestHash())) throw conflict("幂等键已绑定其他请求");
                return result;
            }
        }
    }

    private ProductResource replayProduct(
            Claim claim, CommandContext context, String requestHash) {
        verifyCompleted(claim, PRODUCT_RESPONSE);
        try {
            return codec.product(codec.decrypt(
                    context.scope(), context.idempotencyKey(), requestHash,
                    PRODUCT_RESPONSE, claim.ciphertext()));
        } catch (RuntimeException failure) {
            throw new StoreException(Kind.INTERNAL, "商品幂等响应快照不可用", failure);
        }
    }

    private ProductSkuResource replaySku(
            Claim claim, CommandContext context, String requestHash) {
        verifyCompleted(claim, SKU_RESPONSE);
        try {
            return codec.sku(codec.decrypt(
                    context.scope(), context.idempotencyKey(), requestHash,
                    SKU_RESPONSE, claim.ciphertext()));
        } catch (RuntimeException failure) {
            throw new StoreException(Kind.INTERNAL, "SKU幂等响应快照不可用", failure);
        }
    }

    private static void verifyCompleted(Claim claim, String responseType) {
        if (!(responseType + ":ok").equals(claim.responseRef())
                || !responseType.equals(claim.responseType())
                || claim.ciphertext() == null) {
            throw conflict("幂等请求仍在处理中");
        }
    }

    private void complete(
            Connection connection,
            Claim claim,
            CommandContext context,
            String requestHash,
            String responseType,
            String responseJson) throws SQLException {
        String encrypted = codec.encrypt(
                context.scope(), context.idempotencyKey(), requestHash,
                responseType, responseJson.getBytes(StandardCharsets.UTF_8));
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE hhy.idempotency_records
                SET response_ref=?,response_type=?,response_payload_ciphertext=?
                WHERE id=? AND response_ref IS NULL AND response_type IS NULL
                  AND response_payload_ciphertext IS NULL
                """)) {
            statement.setString(1, responseType + ":ok");
            statement.setString(2, responseType);
            statement.setString(3, encrypted);
            statement.setLong(4, claim.id());
            if (statement.executeUpdate() != 1) throw conflict("幂等响应已被其他请求完成");
        }
    }

    private void audit(
            Connection connection,
            CommandContext context,
            String action,
            String resource,
            long resourceId,
            String before,
            String after) throws SQLException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("resource", after == null ? before : after);
        envelope.put("operationId", context.operationId());
        envelope.put("requestId", context.requestId());
        envelope.put("sessionId", context.sessionId());
        envelope.put("username", context.username());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,before_json,after_json,ip
                ) VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                """)) {
            statement.setLong(1, context.adminId());
            statement.setString(2, action);
            statement.setString(3, resource);
            statement.setLong(4, resourceId);
            statement.setString(5, before);
            statement.setString(6, codec.json(envelope));
            statement.setString(7, context.ip());
            if (statement.executeUpdate() != 1) throw new StoreException(Kind.INTERNAL, "审计写入失败");
        }
    }

    private void outbox(
            Connection connection,
            CommandContext context,
            String eventType,
            String aggregateType,
            long aggregateId,
            Object resource) throws SQLException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", Long.toString(aggregateId));
        payload.put("operationId", context.operationId());
        payload.put("requestId", context.requestId());
        payload.put("actorId", context.adminId());
        payload.put("resource", resource);
        payload.put("occurredAt", Instant.now(clock));
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload
                ) VALUES (?,?,?,?,1,
                  jsonb_build_object('source','r16-commerce-api','requestId',?),
                  CAST(? AS jsonb))
                """)) {
            statement.setString(1, Long.toString(aggregateId));
            statement.setString(2, aggregateType);
            statement.setString(3, UUID.randomUUID().toString());
            statement.setString(4, eventType);
            statement.setString(5, context.requestId());
            statement.setString(6, codec.json(payload));
            if (statement.executeUpdate() != 1) throw new StoreException(Kind.INTERNAL, "Outbox写入失败");
        }
    }

    private static void upsertCommission(
            Connection connection, long skuId, boolean enabled, Integer level1, Integer level2)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.sku_commission_policies(
                  sku_id,level1_bps,level2_bps,enabled,version
                ) VALUES (?,?,?,?,0)
                ON CONFLICT (sku_id) DO UPDATE
                SET level1_bps=EXCLUDED.level1_bps,
                    level2_bps=EXCLUDED.level2_bps,
                    enabled=EXCLUDED.enabled,
                    version=hhy.sku_commission_policies.version+1
                """)) {
            statement.setLong(1, skuId);
            statement.setInt(2, zero(level1));
            statement.setInt(3, zero(level2));
            statement.setBoolean(4, enabled);
            if (statement.executeUpdate() != 1) {
                throw new StoreException(Kind.INTERNAL, "SKU分佣策略写入失败");
            }
        }
    }

    private static void lock(Connection connection, String table, long id) throws SQLException {
        if (!table.equals("hhy.products") && !table.equals("hhy.product_skus")) {
            throw new IllegalArgumentException("unsupported lock table");
        }
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("资源不存在");
            }
        }
    }

    private static Filter filter(PageQuery query, String entity) {
        String alias = entity.equals("product") ? "product" : "sku";
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (query.status() != null) {
            where.append(" AND ").append(alias).append(".status=?");
            args.add(query.status());
        }
        if (query.keyword() != null) {
            String keyword = "%" + query.keyword() + "%";
            if (entity.equals("product")) {
                where.append(" AND (product.product_code ILIKE ? OR product.name ILIKE ?)");
            } else {
                where.append(" AND (sku.code ILIKE ? OR sku.name ILIKE ?)");
            }
            args.add(keyword);
            args.add(keyword);
        }
        return new Filter(where.toString(), args);
    }

    private static Filter orderFilter(Long ownerUserId, PageQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (ownerUserId != null) {
            where.append(" AND orders.user_id=?");
            args.add(ownerUserId);
        }
        if (query.status() != null) {
            where.append(" AND orders.status=?");
            args.add(query.status());
        }
        if (query.keyword() != null) {
            where.append(" AND (orders.order_no ILIKE ? OR orders.biz_type ILIKE ?)");
            String keyword = "%" + query.keyword() + "%";
            args.add(keyword);
            args.add(keyword);
        }
        return new Filter(where.toString(), args);
    }

    private static String productSort(String sort) {
        return switch (sort) {
            case "createdAt:asc" -> "product.created_at ASC,product.id ASC";
            case "createdAt:desc" -> "product.created_at DESC,product.id DESC";
            case "updatedAt:asc" -> "product.updated_at ASC,product.id ASC";
            case "updatedAt:desc" -> "product.updated_at DESC,product.id DESC";
            case "name:asc" -> "product.name ASC,product.id ASC";
            case "name:desc" -> "product.name DESC,product.id DESC";
            case "displayOrder:asc" -> "product.display_order ASC NULLS LAST,product.id ASC";
            case "displayOrder:desc" -> "product.display_order DESC NULLS LAST,product.id DESC";
            default -> throw invalid("商品排序字段不受支持");
        };
    }

    private static String skuSort(String sort) {
        return switch (sort) {
            case "createdAt:asc" -> "sku.created_at ASC,sku.id ASC";
            case "createdAt:desc" -> "sku.created_at DESC,sku.id DESC";
            case "updatedAt:asc" -> "sku.updated_at ASC,sku.id ASC";
            case "updatedAt:desc" -> "sku.updated_at DESC,sku.id DESC";
            case "name:asc" -> "sku.name ASC,sku.id ASC";
            case "name:desc" -> "sku.name DESC,sku.id DESC";
            case "priceCent:asc" -> "sku.price_cent ASC,sku.id ASC";
            case "priceCent:desc" -> "sku.price_cent DESC,sku.id DESC";
            default -> throw invalid("SKU排序字段不受支持");
        };
    }

    private static String orderSort(String sort) {
        return switch (sort) {
            case "createdAt:asc" -> "orders.created_at ASC,orders.id ASC";
            case "createdAt:desc" -> "orders.created_at DESC,orders.id DESC";
            case "status:asc" -> "orders.status ASC,orders.id ASC";
            case "status:desc" -> "orders.status DESC,orders.id DESC";
            case "orderNo:asc" -> "orders.order_no ASC,orders.id ASC";
            case "orderNo:desc" -> "orders.order_no DESC,orders.id DESC";
            default -> throw invalid("订单排序字段不受支持");
        };
    }

    private static long count(Connection connection, String table, Filter filter) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT count(*) FROM " + table + filter.where())) {
            bind(statement, filter.args(), 1);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StoreException(Kind.INTERNAL, "分页总数查询失败");
                return rows.getLong(1);
            }
        }
    }

    private static long scalar(Connection connection, String sql, long value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StoreException(Kind.INTERNAL, "计数查询失败");
                return rows.getLong(1);
            }
        }
    }

    private static int bind(PreparedStatement statement, List<Object> args, int start)
            throws SQLException {
        int index = start;
        for (Object value : args) statement.setObject(index++, value);
        return index;
    }

    private static long returnedId(PreparedStatement statement, String message) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new StoreException(Kind.INTERNAL, message);
            return rows.getLong(1);
        }
    }

    private <T> T read(SqlAction<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                T result = action.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException failure) {
                rollback(connection, failure);
                throw map(failure);
            }
        } catch (SQLException failure) {
            throw map(failure);
        }
    }

    private <T> T write(SqlAction<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                T result = action.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException failure) {
                rollback(connection, failure);
                throw map(failure);
            }
        } catch (SQLException failure) {
            throw map(failure);
        }
    }

    private static RuntimeException map(Exception failure) {
        if (failure instanceof StoreException storeFailure) return storeFailure;
        if (failure instanceof SQLException sql) {
            return switch (sql.getSQLState() == null ? "" : sql.getSQLState()) {
                case "23505", "40001", "40P01" -> conflict("资源版本或唯一键发生冲突");
                case "23503" -> notFound("关联资源不存在");
                case "23514", "22023" -> business("请求违反领域约束");
                default -> new StoreException(Kind.INTERNAL, "R16数据库操作失败", sql);
            };
        }
        return new StoreException(Kind.INTERNAL, "R16持久化操作失败", failure);
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static Integer exactInteger(Long value, String message) {
        if (value == null) return null;
        if (value > Integer.MAX_VALUE) throw business(message);
        return value.intValue();
    }

    private static Integer integer(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static Integer integer(ResultSet rows, int column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static Long longValue(ResultSet rows, int column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rows, int column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void nullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER);
        else statement.setInt(index, value);
    }

    private static void nullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static void nullableInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static StoreException notFound(String message) {
        return new StoreException(Kind.NOT_FOUND, message);
    }

    private static StoreException conflict(String message) {
        return new StoreException(Kind.CONFLICT, message);
    }

    private static StoreException business(String message) {
        return new StoreException(Kind.BUSINESS_RULE, message);
    }

    private static StoreException invalid(String message) {
        return new StoreException(Kind.INVALID_DATA, message);
    }

    @FunctionalInterface
    private interface SqlAction<T> {
        T run(Connection connection) throws SQLException;
    }

    private record Filter(String where, List<Object> args) { }

    private record Claim(
            long id,
            String requestHash,
            String responseRef,
            String responseType,
            String ciphertext,
            boolean replay) { }

    private record ProductHead(
            long id,
            String code,
            String name,
            String type,
            String description,
            Integer displayOrder,
            String status,
            long version) { }

    private record OrderHead(
            String orderNo,
            long userId,
            String type,
            String status,
            String currency,
            Long paidAmount,
            Instant createdAt,
            Instant paidAt,
            long version,
            boolean confirmed,
            String agreementVersion,
            Instant confirmedAt) { }
}
