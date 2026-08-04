package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R19PropContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R19PropContracts.HeadlineSlotRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropCreateRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPatchRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropResource;
import cc.orbexa.hhy.commerce.R19PropContracts.PropUseRequest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/** PostgreSQL implementation of the R19 prop inventory and exposure facts. */
public final class R19PropPostgresStore implements R19PropStore {
    private final DataSource dataSource;
    private final Codec codec;
    private final java.time.Clock clock;

    public R19PropPostgresStore(DataSource dataSource, Codec codec, java.time.Clock clock) {
        this.dataSource = dataSource;
        this.codec = codec;
        this.clock = clock;
    }

    @Override
    public PageSlice<PropResource> store(PageQuery query) {
        return page(query, false, 0, "PROP_STORE");
    }

    @Override
    public PageSlice<PropResource> userProps(long userId, PageQuery query) {
        return page(query, true, userId, "USER_PROPS");
    }

    @Override
    public CommandResultResource order(
            UserCommand context, long propSkuId, long quantity,
            String paymentChannel, String requestHash) {
        return transaction(connection -> {
            ExistingOrder prior = existingOrder(
                    connection, context.userId(), "PROP", context.idempotencyKey());
            if (prior != null) {
                if (!requestHash.equals(prior.requestHash())) {
                    throw conflict("相同幂等键对应的道具订单请求已变化");
                }
                return prior.result();
            }
            SkuHead sku = skuHead(connection, propSkuId, true);
            if (sku == null || !"ACTIVE".equals(sku.status())
                    || !"ACTIVE".equals(sku.productSkuStatus())) {
                throw notFound("道具SKU不存在或已下架");
            }
            if (quantity < 1 || quantity > 1000) throw rule("道具数量不符合要求");
            long amount;
            try {
                amount = Math.multiplyExact(sku.priceCent(), quantity);
            } catch (ArithmeticException failure) {
                throw rule("道具订单金额超出范围");
            }
            String orderNo = "R19-PROP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
            String snapshot = codec.json(Map.of(
                    "name", sku.name(), "propType", sku.propType(), "skuId", Long.toString(propSkuId),
                    "paymentChannel", paymentChannel));
            long orderId;
            long version;
            Instant acceptedAt = now();
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO hhy.orders(
                      order_no,user_id,biz_type,biz_id,amount_cent,status,currency,
                      no_refund_confirmed,no_refund_agreement_version,no_refund_confirmed_at,
                      idempotency_key,request_hash,legacy_without_idempotency,version)
                    VALUES (?,?,?,?,?,'PENDING_PAYMENT','CNY',true,'PROP-NO-REFUND-V1',?, ?, ?, false,0)
                    RETURNING id,version
                    """)) {
                statement.setString(1, orderNo);
                statement.setLong(2, context.userId());
                statement.setString(3, "PROP");
                statement.setLong(4, propSkuId);
                statement.setLong(5, amount);
                statement.setObject(6, time(acceptedAt));
                statement.setString(7, context.idempotencyKey());
                statement.setString(8, requestHash);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new StoreException(Kind.INTERNAL, "道具订单创建未返回结果");
                    orderId = rows.getLong(1);
                    version = rows.getLong(2);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO hhy.order_items(
                      order_id,sku_id,item_name,quantity,unit_price,subtotal_amount_cent,snapshot_json)
                    VALUES (?,?,?,?,?,?,?::jsonb)
                    """)) {
                statement.setLong(1, orderId);
                statement.setLong(2, sku.productSkuId());
                statement.setString(3, sku.name());
                statement.setLong(4, quantity);
                statement.setLong(5, sku.priceCent());
                statement.setLong(6, amount);
                statement.setString(7, snapshot);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO hhy.order_price_snapshots(
                      order_id,original,discount,service_fee,payable,rule_versions,rule_versions_json)
                    VALUES (?,?,0,0,?,'R19-PROP-V1','[\"R19-PROP-V1\"]'::jsonb)
                    """)) {
                statement.setLong(1, orderId);
                statement.setLong(2, amount);
                statement.setLong(3, amount);
                statement.executeUpdate();
            }
            outbox(connection, orderId, "order", "prop.order.created.v1",
                    Map.of("orderId", orderId, "userId", context.userId(), "propSkuId", propSkuId));
            return new CommandResultResource(
                    Long.toString(orderId), orderNo, "PENDING_PAYMENT", version, acceptedAt);
        });
    }

    @Override
    public CommandResultResource use(
            UserCommand context, long userPropId, PropUseRequest request, String requestHash) {
        return transaction(connection -> {
            ExecutionHead prior = execution(connection, userPropId, context.idempotencyKey());
            if (prior != null) {
                if (!requestHash.equals(prior.requestHash())) {
                    throw conflict("相同幂等键对应的道具使用请求已变化");
                }
                return command(prior.result(), prior.status(), prior.id(), prior.createdAt());
            }
            InventoryHead inventory = inventory(connection, context.userId(), userPropId, true);
            if (inventory == null) throw notFound("用户道具不存在");
            if (inventory.version() != request.expectedVersion()) throw conflict("用户道具版本已变化");
            if (!"AVAILABLE".equals(inventory.status()) && !"RESERVED".equals(inventory.status())) {
                throw rule("当前道具状态不可使用");
            }
            if (inventory.expiresAt() != null && !inventory.expiresAt().isAfter(now())) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE hhy.user_props SET status='EXPIRED',version=version+1 WHERE id=? AND version=?")) {
                    statement.setLong(1, userPropId);
                    statement.setLong(2, inventory.version());
                    statement.executeUpdate();
                }
                throw rule("道具已过期");
            }
            long contentId = parseId(request.targetContentId(), "目标内容");
            if (!contentExists(connection, context.userId(), contentId)) {
                throw notFound("目标内容不存在或不可用");
            }

            String executionId;
            String businessNo;
            Instant acceptedAt = now();
            String type = inventory.propType();
            if ("REFRESH".equals(type)) {
                refresh(connection, context, inventory, contentId, requestHash, acceptedAt);
                executionId = Long.toString(contentId);
                businessNo = "R19-REFRESH-" + contentId;
            } else {
                EntitlementResult entitlement = entitlement(
                        connection, context, inventory, contentId, request, acceptedAt);
                executionId = Long.toString(entitlement.entitlementId());
                businessNo = "R19-EXPOSURE-" + entitlement.entitlementId();
            }
            decrementInventory(connection, userPropId, inventory.version(), inventory.quantity());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resourceId", executionId);
            result.put("businessNo", businessNo);
            result.put("status", "SUCCEEDED");
            result.put("version", inventory.version() + 1);
            result.put("acceptedAt", acceptedAt.toString());
            long logId;
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO hhy.prop_execution_logs(
                      user_prop_id,content_id,result,status,operation_id,idempotency_key,request_hash,version)
                    VALUES (?,?,?::jsonb,'SUCCEEDED',?,?,?,0)
                    RETURNING id
                    """)) {
                statement.setLong(1, userPropId);
                statement.setLong(2, contentId);
                statement.setString(3, codec.json(result));
                statement.setString(4, context.operationId());
                statement.setString(5, context.idempotencyKey());
                statement.setString(6, requestHash);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new StoreException(Kind.INTERNAL, "道具执行日志创建失败");
                    logId = rows.getLong(1);
                }
            }
            outbox(connection, logId, "prop_execution", "prop.execution.succeeded.v1",
                    Map.of("executionId", logId, "userPropId", userPropId, "contentId", contentId));
            return new CommandResultResource(executionId, businessNo, "SUCCEEDED", inventory.version() + 1, acceptedAt);
        });
    }

    @Override
    public PageSlice<PropResource> adminProps(PageQuery query) {
        return adminStorePage(query);
    }

    @Override
    public PropResource create(
            AdminCommand context, PropCreateRequest request, String requestHash) {
        return transaction(connection -> {
            Idempotency prior = claim(connection, context, requestHash);
            if (prior.responseRef() != null) return resourceRef(connection, prior.responseRef());
            long productId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO hhy.products(product_code,type,name,description,status,display_order,version) "
                            + "VALUES (?,'PROP',?,? ,?,0,0) RETURNING id")) {
                statement.setString(1, request.productCode());
                statement.setString(2, request.name());
                statement.setString(3, "R19道具商品：" + request.propType());
                statement.setString(4, request.status());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new StoreException(Kind.INTERNAL, "商品创建失败");
                    productId = rows.getLong(1);
                }
            }
            long productSkuId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO hhy.product_skus(product_id,code,name,price_cent,member_price_cent,duration,duration_days,attributes_json,benefits_json,status,version) "
                            + "VALUES (?,?,?,?,?,?,?,?::jsonb,'[]'::jsonb,?,0) RETURNING id")) {
                statement.setLong(1, productId);
                statement.setString(2, request.skuCode());
                statement.setString(3, request.name());
                statement.setLong(4, request.priceCent());
                if (request.memberPriceCent() == null) statement.setNull(5, Types.BIGINT);
                else statement.setLong(5, request.memberPriceCent());
                long durationDays = Math.max(1, (request.durationSeconds() + 86399) / 86400);
                statement.setInt(6, (int) Math.min(Integer.MAX_VALUE, durationDays));
                statement.setInt(7, (int) Math.min(Integer.MAX_VALUE, durationDays));
                statement.setString(8, codec.json(Map.of("name", request.name(), "benefits", List.of())));
                statement.setString(9, request.status());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new StoreException(Kind.INTERNAL, "SKU创建失败");
                    productSkuId = rows.getLong(1);
                }
            }
            long propId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO hhy.prop_products(type,name,duration_seconds,execution_type,status,version) "
                            + "VALUES (?,?,?,?,?,0) RETURNING id")) {
                statement.setString(1, request.propType());
                statement.setString(2, request.name());
                statement.setString(3, Long.toString(request.durationSeconds()));
                statement.setString(4, request.executionType());
                statement.setString(5, request.status());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new StoreException(Kind.INTERNAL, "道具定义创建失败");
                    propId = rows.getLong(1);
                }
            }
            long propSkuId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO hhy.prop_skus(prop_id,product_sku_id,scope_json,status,version) "
                            + "VALUES (?,?,?::jsonb,?,0) RETURNING id")) {
                statement.setLong(1, propId);
                statement.setLong(2, productSkuId);
                statement.setString(3, codec.json(request.scope()));
                statement.setString(4, request.status());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new StoreException(Kind.INTERNAL, "道具SKU绑定失败");
                    propSkuId = rows.getLong(1);
                }
            }
            Map<String, Object> after = Map.of(
                    "propId", propId, "propSkuId", propSkuId, "productId", productId,
                    "productSkuId", productSkuId, "reason", request.reason());
            audit(connection, context, propSkuId, Map.of(), after);
            complete(connection, context, requestHash, "SKU:" + propSkuId);
            outbox(connection, propSkuId, "prop", "prop.definition.created.v1", after);
            return propResource(connection, propSkuId);
        });
    }

    @Override
    public PropResource patch(
            AdminCommand context, long id, PropPatchRequest request, String requestHash) {
        return transaction(connection -> {
            Idempotency prior = claim(connection, context, requestHash);
            if (prior.responseRef() != null) return resourceRef(connection, prior.responseRef());
            // The list endpoint exposes SKU ids. Product and SKU sequences may
            // overlap, so resolving products first could patch the wrong row.
            SkuHead sku = skuHead(connection, id, true);
            if (sku == null) throw notFound("道具资源不存在");
            Map<String, Object> before = skuMap(sku);
            updatePropSku(connection, sku, request);
            PropResource result = propResource(connection, id);
            String responseRef = "SKU:" + id;
            audit(connection, context, id, before, skuMap(skuHead(connection, id, false)));
            complete(connection, context, requestHash, responseRef);
            outbox(connection, id, "prop", "prop.definition.changed.v1",
                    Map.of("resourceId", id, "operationId", context.operationId()));
            return result;
        });
    }

    @Override
    public PageSlice<PropResource> headlineSlots(PageQuery query) {
        return slotPage(query);
    }

    @Override
    public PropResource createHeadlineSlot(
            AdminCommand context, HeadlineSlotRequest request, String requestHash) {
        return transaction(connection -> {
            Idempotency prior = claim(connection, context, requestHash);
            if (prior.responseRef() != null) return resourceRef(connection, prior.responseRef());
            String pageCode = text(request.payload().get("pageCode"), 255, "页面编码");
            String slotCode = text(request.payload().get("slotCode"), 255, "资源位编码");
            int capacity = number(request.payload().getOrDefault("capacity", 1), "资源位容量");
            if (capacity < 1 || capacity > 1000) throw rule("资源位容量不符合要求");
            long id;
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO hhy.headline_slots(page_code,slot_code,capacity,status,version)
                    VALUES (?,?,?,'ACTIVE',0) RETURNING id
                    """)) {
                statement.setString(1, pageCode);
                statement.setString(2, slotCode);
                statement.setInt(3, capacity);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new StoreException(Kind.INTERNAL, "资源位创建失败");
                    id = rows.getLong(1);
                }
            }
            audit(connection, context, id, Map.of(), Map.of(
                    "pageCode", pageCode, "slotCode", slotCode, "capacity", capacity));
            complete(connection, context, requestHash, "SLOT:" + id);
            outbox(connection, id, "headline_slot", "headline.slot.created.v1",
                    Map.of("slotId", id, "pageCode", pageCode, "slotCode", slotCode));
            return slotResource(connection, id);
        });
    }

    @Override
    public PageSlice<PropResource> executions(PageQuery query) {
        return executionPage(query);
    }

    private PageSlice<PropResource> page(
            PageQuery query, boolean user, long userId, String mode) {
        if ("USER_PROPS".equals(mode)) return userPage(query, userId);
        return storePage(query);
    }

    private PageSlice<PropResource> storePage(PageQuery query) {
        return propStorePage(query, true);
    }

    private PageSlice<PropResource> adminStorePage(PageQuery query) {
        return propStorePage(query, false);
    }

    private PageSlice<PropResource> propStorePage(PageQuery query, boolean publicOnly) {
        List<Object> args = new ArrayList<>();
        String where = filters(query, args, "ps.status", "pp.name", "pp.type");
        String active = "ps.status='ACTIVE' AND pp.status='ACTIVE' "
                + "AND product_sku.status='ACTIVE' AND product.status='ACTIVE'";
        if (publicOnly) where = where.isBlank() ? " WHERE " + active : where + " AND " + active;
        String sql = """
                SELECT ps.id,pp.type,pp.name,ps.status,ps.version,ps.scope_json,
                       product_sku.price_cent,pp.duration_seconds,pp.execution_type,
                       product_sku.member_price_cent
                FROM hhy.prop_skus ps
                JOIN hhy.prop_products pp ON pp.id=ps.prop_id
                JOIN hhy.product_skus product_sku ON product_sku.id=ps.product_sku_id
                JOIN hhy.products product ON product.id=product_sku.product_id
                """ + where + " ORDER BY " + orderBy(query.sort(), "ps.created_at", "pp.name",
                        "product_sku.price_cent", "ps.updated_at", "ps.updated_at", "ps.id")
                + " LIMIT ? OFFSET ?";
        args.add(query.pageSize());
        args.add(query.offset());
        List<PropResource> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(propSkuResource(rows));
            }
            return new PageSlice<>(items, count(connection,
                    "prop_skus ps JOIN hhy.prop_products pp ON pp.id=ps.prop_id "
                            + "JOIN hhy.product_skus product_sku ON product_sku.id=ps.product_sku_id "
                            + "JOIN hhy.products product ON product.id=product_sku.product_id",
                    where, argsForCount(args, query), "ps.status", "pp.name", "pp.type"));
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    private PageSlice<PropResource> userPage(PageQuery query, long userId) {
        List<Object> args = new ArrayList<>();
        String where = filters(query, args, "up.status", "pp.name", "pp.type");
        where = where.isBlank() ? " WHERE up.user_id=?" : where + " AND up.user_id=?";
        args.add(userId);
        String sql = """
                SELECT up.id,pp.type,pp.name,up.quantity,up.status,up.expires_at,up.version,
                       ps.scope_json
                FROM hhy.user_props up
                JOIN hhy.prop_skus ps ON ps.id=up.prop_sku_id
                JOIN hhy.prop_products pp ON pp.id=ps.prop_id
                """ + where + " ORDER BY " + orderBy(query.sort(), "up.created_at", "pp.name",
                        "up.quantity", "up.expires_at", "up.updated_at", "up.id")
                + " LIMIT ? OFFSET ?";
        args.add(query.pageSize());
        args.add(query.offset());
        List<PropResource> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(userPropResource(rows));
            }
            List<Object> countArgs = new ArrayList<>();
            String countWhere = filters(query, countArgs, "up.status", "pp.name", "pp.type");
            countWhere = countWhere.isBlank() ? " WHERE up.user_id=?" : countWhere + " AND up.user_id=?";
            countArgs.add(userId);
            return new PageSlice<>(items, count(connection,
                    "user_props up JOIN hhy.prop_skus ps ON ps.id=up.prop_sku_id JOIN hhy.prop_products pp ON pp.id=ps.prop_id",
                    countWhere, countArgs, null, null, null));
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    private PageSlice<PropResource> slotPage(PageQuery query) {
        List<Object> args = new ArrayList<>();
        String where = filters(query, args, "hs.status", "hs.page_code", "hs.slot_code");
        String sql = "SELECT hs.id,hs.page_code,hs.slot_code,hs.capacity,hs.status,hs.version "
                + "FROM hhy.headline_slots hs" + where + " ORDER BY "
                + slotOrderBy(query.sort()) + " LIMIT ? OFFSET ?";
        args.add(query.pageSize()); args.add(query.offset());
        List<PropResource> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(slotResource(rows));
            }
            List<Object> countArgs = new ArrayList<>();
            String countWhere = filters(query, countArgs, "hs.status", "hs.page_code", "hs.slot_code");
            return new PageSlice<>(items, count(connection, "headline_slots hs", countWhere, countArgs, null, null, null));
        } catch (SQLException failure) { throw sql(failure); }
    }

    private PageSlice<PropResource> executionPage(PageQuery query) {
        List<Object> args = new ArrayList<>();
        String where = filters(query, args, "pel.status", "pel.operation_id", "pel.error");
        String sql = "SELECT pel.id,pel.operation_id,pel.status,pel.result,pel.error,pel.version,pel.created_at "
                + "FROM hhy.prop_execution_logs pel" + where + " ORDER BY "
                + executionOrderBy(query.sort()) + " LIMIT ? OFFSET ?";
        args.add(query.pageSize()); args.add(query.offset());
        List<PropResource> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(executionResource(rows));
            }
            List<Object> countArgs = new ArrayList<>();
            String countWhere = filters(query, countArgs, "pel.status", "pel.operation_id", "pel.error");
            return new PageSlice<>(items, count(connection, "prop_execution_logs pel", countWhere, countArgs, null, null, null));
        } catch (SQLException failure) { throw sql(failure); }
    }

    private static String filters(
            PageQuery query, List<Object> args, String statusColumn,
            String keywordA, String keywordB) {
        List<String> clauses = new ArrayList<>();
        if (query.status() != null) { clauses.add(statusColumn + "=?"); args.add(query.status()); }
        if (query.keyword() != null) {
            clauses.add("(" + keywordA + " ILIKE ? OR " + keywordB + " ILIKE ?)");
            args.add("%" + query.keyword() + "%"); args.add("%" + query.keyword() + "%");
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private static List<Object> argsForCount(List<Object> args, PageQuery query) {
        if (args.size() < 2) return List.of();
        return List.copyOf(args.subList(0, args.size() - 2));
    }

    private static long count(
            Connection connection, String from, String where, List<Object> args,
            String ignoredA, String ignoredB, String ignoredC) throws SQLException {
        String sql = "SELECT count(*) FROM hhy." + from + where;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private static String orderBy(
            String sort, String created, String name, String quantity, String expires,
            String updated, String id) {
        if (sort == null) return created + " DESC";
        return switch (sort) {
            case "createdAt:asc" -> created + " ASC, " + id + " ASC";
            case "createdAt:desc" -> created + " DESC, " + id + " DESC";
            case "updatedAt:asc" -> updated + " ASC, " + id + " ASC";
            case "updatedAt:desc" -> updated + " DESC, " + id + " DESC";
            case "name:asc" -> name + " ASC, " + id + " ASC";
            case "name:desc" -> name + " DESC, " + id + " DESC";
            case "quantity:asc" -> quantity + " ASC, " + id + " ASC";
            case "quantity:desc" -> quantity + " DESC, " + id + " DESC";
            case "expiresAt:asc" -> expires + " ASC NULLS LAST, " + id + " ASC";
            default -> created + " DESC";
        };
    }

    private static String slotOrderBy(String sort) {
        return switch (sort) {
            case "createdAt:asc" -> "hs.created_at ASC, hs.id ASC";
            case "name:asc" -> "hs.page_code ASC, hs.id ASC";
            case "name:desc" -> "hs.page_code DESC, hs.id DESC";
            default -> "hs.created_at DESC, hs.id DESC";
        };
    }

    private static String executionOrderBy(String sort) {
        return switch (sort) {
            case "createdAt:asc" -> "pel.created_at ASC, pel.id ASC";
            case "name:asc" -> "pel.operation_id ASC, pel.id ASC";
            case "name:desc" -> "pel.operation_id DESC, pel.id DESC";
            default -> "pel.created_at DESC, pel.id DESC";
        };
    }

    private void refresh(
            Connection connection, UserCommand context, InventoryHead inventory,
            long contentId, String requestHash, Instant acceptedAt) throws SQLException {
        long cooldown = config(connection, "props.refresh_cooldown_seconds", 600);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM hhy.content_refresh_records WHERE user_prop_id=? AND content_id=? AND executed_at>? LIMIT 1")) {
            statement.setLong(1, inventory.id()); statement.setLong(2, contentId);
            statement.setObject(3, time(acceptedAt.minusSeconds(cooldown)));
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) throw rule("刷新仍在冷却期内");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hhy.content_refresh_records(content_id,user_prop_id,layers,executed_at,idempotency_key,request_hash) VALUES (?,?,?, ?,?,?)")) {
            statement.setLong(1, contentId); statement.setLong(2, inventory.id());
            statement.setString(3, "REFRESH"); statement.setObject(4, time(acceptedAt));
            statement.setString(5, context.idempotencyKey()); statement.setString(6, requestHash);
            statement.executeUpdate();
        }
    }

    private EntitlementResult entitlement(
            Connection connection, UserCommand context, InventoryHead inventory,
            long contentId, PropUseRequest request, Instant acceptedAt) throws SQLException {
        long duration = config(connection, "props." + inventory.propType().toLowerCase() + "_duration_seconds", 86400);
        if (duration < 1 || duration > 2592000) throw rule("道具时长配置无效");
        Instant start = request.scheduledAt() == null ? acceptedAt : request.scheduledAt();
        if (start.isBefore(acceptedAt.minusSeconds(60)) || start.isAfter(acceptedAt.plusSeconds(2592000))) {
            throw rule("排期时间不符合要求");
        }
        if (configBoolean(connection, "props.same_type.stack_duration", true)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT max(ends_at) FROM hhy.content_exposure_entitlements WHERE content_id=? AND type=? AND status IN ('SCHEDULED','ACTIVE')")) {
                statement.setLong(1, contentId); statement.setString(2, inventory.propType());
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        Instant existing = instant(rows, 1);
                        if (existing != null && existing.isAfter(start)) start = existing;
                    }
                }
            }
        }
        Instant end = start.plusSeconds(duration);
        long entitlementId;
        String status = start.isAfter(acceptedAt) ? "SCHEDULED" : "ACTIVE";
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hhy.content_exposure_entitlements(content_id,user_prop_id,type,starts_at,ends_at,status,version) VALUES (?,?,?,?,?,?,0) RETURNING id")) {
            statement.setLong(1, contentId); statement.setLong(2, inventory.id());
            statement.setString(3, inventory.propType()); statement.setObject(4, time(start));
            statement.setObject(5, time(end)); statement.setString(6, status);
            try (ResultSet rows = statement.executeQuery()) { rows.next(); entitlementId = rows.getLong(1); }
        }
        if ("HEADLINE".equals(inventory.propType())) {
            Long slot = firstSlot(connection, start, end);
            if (slot == null) throw rule("没有可用头条资源位");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO hhy.headline_slot_bookings(slot_id,content_id,entitlement_id,starts_at,ends_at,status,version) VALUES (?,?,?,?,?,'BOOKED',0)")) {
                statement.setLong(1, slot); statement.setLong(2, contentId); statement.setLong(3, entitlementId);
                statement.setObject(4, time(start)); statement.setObject(5, time(end)); statement.executeUpdate();
            }
        }
        return new EntitlementResult(entitlementId);
    }

    private static Long firstSlot(Connection connection, Instant startsAt, Instant endsAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT slot.id
                FROM hhy.headline_slots slot
                WHERE slot.status='ACTIVE'
                  AND (SELECT count(*) FROM hhy.headline_slot_bookings booking
                       WHERE booking.slot_id=slot.id
                         AND booking.status IN ('BOOKED','ACTIVE')
                         AND booking.starts_at < ? AND booking.ends_at > ?) < slot.capacity
                ORDER BY slot.id
                LIMIT 1
                FOR UPDATE OF slot
                """)) {
            statement.setObject(1, time(endsAt));
            statement.setObject(2, time(startsAt));
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getLong(1) : null; }
        }
    }

    private static void decrementInventory(
            Connection connection, long id, long version, long quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE hhy.user_props SET quantity=quantity-1,status=CASE WHEN quantity=1 THEN 'CONSUMED' ELSE status END,version=version+1 WHERE id=? AND version=? AND quantity>0")) {
            statement.setLong(1, id); statement.setLong(2, version);
            if (statement.executeUpdate() != 1) throw conflict("用户道具库存已变化");
        }
    }

    private boolean contentExists(Connection connection, long userId, long contentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM hhy.content_posts WHERE id=? AND owner_id=? "
                        + "AND status NOT IN ('DELETED','OFFLINE_BY_PLATFORM','OFFLINE_BY_OWNER')")) {
            statement.setLong(1, contentId);
            statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private SkuHead skuHead(Connection connection, long id, boolean lock) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT ps.id,ps.product_sku_id,ps.status,ps.version,pp.type,pp.name,
                       product_sku.price_cent,product_sku.status,product_sku.member_price_cent
                FROM hhy.prop_skus ps
                JOIN hhy.prop_products pp ON pp.id=ps.prop_id
                JOIN hhy.product_skus product_sku ON product_sku.id=ps.product_sku_id
                WHERE ps.id=?""" + suffix)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new SkuHead(rows.getLong(1), rows.getLong(2), rows.getString(3),
                        rows.getLong(4), rows.getString(5), rows.getString(6), rows.getLong(7),
                        rows.getString(8), rows.getObject(9) == null ? null : rows.getLong(9));
            }
        }
    }

    private InventoryHead inventory(Connection connection, long userId, long id, boolean lock)
            throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT up.id,up.quantity,up.status,up.expires_at,up.version,pp.type
                FROM hhy.user_props up
                JOIN hhy.prop_skus ps ON ps.id=up.prop_sku_id
                JOIN hhy.prop_products pp ON pp.id=ps.prop_id
                WHERE up.id=? AND up.user_id=?""" + suffix)) {
            statement.setLong(1, id); statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new InventoryHead(rows.getLong(1), rows.getLong(2), rows.getString(3),
                        instant(rows, 4), rows.getLong(5), rows.getString(6));
            }
        }
    }

    private ExistingOrder existingOrder(Connection connection, long userId, String bizType, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,order_no,status,version,created_at,request_hash FROM hhy.orders WHERE user_id=? AND biz_type=? AND idempotency_key=? FOR UPDATE")) {
            statement.setLong(1, userId); statement.setString(2, bizType); statement.setString(3, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new ExistingOrder(rows.getLong(1), rows.getString(2), rows.getString(3),
                        rows.getLong(4), instant(rows, 5), rows.getString(6));
            }
        }
    }

    private ExecutionHead execution(Connection connection, long userPropId, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,result,status,request_hash,created_at FROM hhy.prop_execution_logs WHERE user_prop_id=? AND idempotency_key=? FOR UPDATE")) {
            statement.setLong(1, userPropId); statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new ExecutionHead(rows.getLong(1), rows.getString(2), rows.getString(3),
                        rows.getString(4), instant(rows, 5));
            }
        }
    }

    private void updatePropSku(Connection connection, SkuHead sku, PropPatchRequest request)
            throws SQLException {
        Map<String, Object> payload = request.payload();
        long effectivePrice = payload.containsKey("priceCent")
                ? ((Number) payload.get("priceCent")).longValue() : sku.priceCent();
        Long effectiveMemberPrice = payload.containsKey("memberPriceCent")
                ? (payload.get("memberPriceCent") == null ? null
                        : ((Number) payload.get("memberPriceCent")).longValue())
                : sku.memberPriceCent();
        if (effectiveMemberPrice != null && effectiveMemberPrice > effectivePrice) {
            throw rule("会员价不能高于普通价");
        }
        List<String> sets = new ArrayList<>(); List<Object> values = new ArrayList<>();
        if (payload.containsKey("status")) { sets.add("status=?"); values.add(text(payload.get("status"), 64, "状态")); }
        if (payload.containsKey("scope")) { sets.add("scope_json=?::jsonb"); values.add(codec.json(payload.get("scope"))); }
        values.add(sku.id()); values.add(request.expectedVersion());
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE hhy.prop_skus SET "
                        + (sets.isEmpty() ? "" : String.join(",", sets) + ",")
                        + "version=version+1 WHERE id=? AND version=?")) {
            bind(statement, values);
            if (statement.executeUpdate() != 1) throw conflict("道具SKU版本已变化");
        }
        if (payload.containsKey("name") || payload.containsKey("durationSeconds")
                || payload.containsKey("executionType") || payload.containsKey("status")) {
            List<String> productSets = new ArrayList<>(); List<Object> productValues = new ArrayList<>();
            if (payload.containsKey("name")) { productSets.add("name=?"); productValues.add(text(payload.get("name"), 255, "名称")); }
            if (payload.containsKey("durationSeconds")) { productSets.add("duration_seconds=?"); productValues.add(Long.toString(number(payload.get("durationSeconds"), "使用时长"))); }
            if (payload.containsKey("executionType")) { productSets.add("execution_type=?"); productValues.add(text(payload.get("executionType"), 255, "执行方式")); }
            if (payload.containsKey("status")) { productSets.add("status=?"); productValues.add(text(payload.get("status"), 64, "状态")); }
            productValues.add(sku.id());
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE hhy.prop_products SET " + String.join(",", productSets)
                            + ",version=version+1 WHERE id=(SELECT prop_id FROM hhy.prop_skus WHERE id=?)")) {
                bind(statement, productValues); statement.executeUpdate();
            }
        }
        if (payload.containsKey("name") || payload.containsKey("priceCent")
                || payload.containsKey("durationSeconds")
                || payload.containsKey("memberPriceCent") || payload.containsKey("status")) {
            List<String> skuSets = new ArrayList<>(); List<Object> skuValues = new ArrayList<>();
            if (payload.containsKey("name")) { skuSets.add("name=?"); skuValues.add(text(payload.get("name"), 255, "名称")); }
            if (payload.containsKey("priceCent")) { skuSets.add("price_cent=?"); skuValues.add(number(payload.get("priceCent"), "售价")); }
            if (payload.containsKey("memberPriceCent")) {
                if (payload.get("memberPriceCent") == null) skuSets.add("member_price_cent=NULL");
                else { skuSets.add("member_price_cent=?"); skuValues.add(payload.get("memberPriceCent")); }
            }
            if (payload.containsKey("status")) { skuSets.add("status=?"); skuValues.add(text(payload.get("status"), 64, "状态")); }
            if (payload.containsKey("durationSeconds")) {
                long seconds = ((Number) payload.get("durationSeconds")).longValue();
                long days = Math.max(1, (seconds + 86399) / 86400);
                skuSets.add("duration=?"); skuValues.add((int) Math.min(Integer.MAX_VALUE, days));
                skuSets.add("duration_days=?"); skuValues.add((int) Math.min(Integer.MAX_VALUE, days));
            }
            skuValues.add(sku.productSkuId());
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE hhy.product_skus SET " + String.join(",", skuSets)
                            + ",version=version+1 WHERE id=?")) {
                bind(statement, skuValues); statement.executeUpdate();
            }
        }
        if (payload.containsKey("name") || payload.containsKey("status")) {
            List<String> productSets = new ArrayList<>(); List<Object> productValues = new ArrayList<>();
            if (payload.containsKey("name")) { productSets.add("name=?"); productValues.add(payload.get("name")); }
            if (payload.containsKey("status")) { productSets.add("status=?"); productValues.add(payload.get("status")); }
            productValues.add(sku.productSkuId());
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE hhy.products SET " + String.join(",", productSets)
                            + ",version=version+1 WHERE id=(SELECT product_id FROM hhy.product_skus WHERE id=?)")) {
                bind(statement, productValues); statement.executeUpdate();
            }
        }
    }

    private PropResource resourceRef(Connection connection, String ref) throws SQLException {
        String[] parts = ref.split(":", 2);
        if (parts.length != 2) throw new StoreException(Kind.INTERNAL, "道具幂等响应引用无效");
        long id = parseId(parts[1], "资源");
        return switch (parts[0]) {
            case "SKU" -> propResource(connection, id);
            case "SLOT" -> slotResource(connection, id);
            default -> throw new StoreException(Kind.INTERNAL, "道具幂等响应类型无效");
        };
    }

    private PropResource propResource(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ps.id,pp.type,pp.name,ps.status,ps.version,ps.scope_json,sku.price_cent,pp.duration_seconds,pp.execution_type,sku.member_price_cent "
                        + "FROM hhy.prop_skus ps JOIN hhy.prop_products pp ON pp.id=ps.prop_id "
                        + "JOIN hhy.product_skus sku ON sku.id=ps.product_sku_id WHERE ps.id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("道具SKU不存在");
                return propSkuResource(rows);
            }
        }
    }

    private PropResource slotResource(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,page_code,slot_code,capacity,status,version FROM hhy.headline_slots WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw notFound("资源位不存在");
                return slotResource(rows);
            }
        }
    }

    private static PropResource slotResource(ResultSet rows) throws SQLException {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("pageCode", rows.getString(2));
        configuration.put("slotCode", rows.getString(3));
        configuration.put("capacity", rows.getInt(4));
        return new PropResource(Long.toString(rows.getLong(1)), "HEADLINE_SLOT",
                rows.getString(2) + "/" + rows.getString(3), rows.getInt(4),
                rows.getString(5), null, configuration, rows.getLong(6));
    }

    private PropResource propSkuResource(ResultSet rows) throws SQLException {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("priceCent", rows.getLong(7));
        Long memberPrice = rows.getObject(10) == null ? null : rows.getLong(10);
        configuration.put("memberPriceCent", memberPrice);
        configuration.put("durationSeconds", rows.getString(8));
        configuration.put("executionType", rows.getString(9));
        configuration.put("scope", object(rows.getString(6)));
        return new PropResource(Long.toString(rows.getLong(1)), rows.getString(2), rows.getString(3),
                0, rows.getString(4), null, configuration, rows.getLong(5));
    }

    private PropResource userPropResource(ResultSet rows) throws SQLException {
        return new PropResource(Long.toString(rows.getLong(1)), rows.getString(2), rows.getString(3),
                rows.getLong(4), rows.getString(5), instant(rows, 6),
                object(rows.getString(8)), rows.getLong(7));
    }

    private PropResource executionResource(ResultSet rows) throws SQLException {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("result", object(rows.getString(4)));
        configuration.put("error", rows.getString(5));
        return new PropResource(Long.toString(rows.getLong(1)), "EXECUTION",
                rows.getString(2), 1, rows.getString(3), instant(rows, 7), configuration, rows.getLong(6));
    }

    private Map<String, Object> object(String value) { return value == null ? Map.of() : codec.object(value); }

    private Map<String, Object> skuMap(SkuHead value) {
        if (value == null) return Map.of();
        return Map.of("id", value.id(), "productSkuId", value.productSkuId(), "propType", value.propType(),
                "name", value.name(), "status", value.status(), "version", value.version());
    }

    private Idempotency claim(Connection connection, AdminCommand context, String requestHash)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT request_hash,response_ref FROM hhy.idempotency_records WHERE scope=? AND idem_key=? FOR UPDATE")) {
            statement.setString(1, context.scope()); statement.setString(2, context.idempotencyKey());
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    String priorHash = rows.getString(1);
                    if (!requestHash.equals(priorHash)) throw conflict("相同幂等键对应的后台请求已变化");
                    return new Idempotency(priorHash, rows.getString(2));
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hhy.idempotency_records(scope,idem_key,request_hash,expires_at) VALUES (?,?,?,?)")) {
            statement.setString(1, context.scope()); statement.setString(2, context.idempotencyKey());
            statement.setString(3, requestHash); statement.setObject(4, time(now().plusSeconds(86400)));
            statement.executeUpdate();
        }
        return new Idempotency(requestHash, null);
    }

    private void complete(Connection connection, AdminCommand context, String requestHash, String ref)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE hhy.idempotency_records SET response_ref=? WHERE scope=? AND idem_key=? AND request_hash=?")) {
            statement.setString(1, ref); statement.setString(2, context.scope());
            statement.setString(3, context.idempotencyKey()); statement.setString(4, requestHash);
            if (statement.executeUpdate() != 1) throw conflict("幂等记录已被其他请求完成");
        }
    }

    private void audit(Connection connection, AdminCommand context, long id,
            Map<String, Object> before, Map<String, Object> after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hhy.admin_operation_logs(admin_id,action,resource,resource_id,before_json,after_json,ip) VALUES (?,?,?, ?,?::jsonb,?::jsonb,?)")) {
            statement.setLong(1, context.adminId()); statement.setString(2, context.operationId());
            statement.setString(3, "prop"); statement.setLong(4, id);
            statement.setString(5, codec.json(before)); statement.setString(6, codec.json(after));
            statement.setString(7, context.ip()); statement.executeUpdate();
        }
    }

    private void outbox(Connection connection, long aggregateId, String aggregateType,
            String eventType, Map<String, Object> payload) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hhy.outbox_events(aggregate_id,aggregate_type,event_id,event_type,event_version,headers,payload,status,attempts,available_at) VALUES (?,?,?, ?,1,'{}'::jsonb,?::jsonb,'PENDING',0,?)")) {
            statement.setString(1, Long.toString(aggregateId)); statement.setString(2, aggregateType);
            statement.setString(3, UUID.randomUUID().toString()); statement.setString(4, eventType);
            statement.setString(5, codec.json(payload)); statement.setObject(6, time(now())); statement.executeUpdate();
        }
    }

    private long config(Connection connection, String key, long fallback) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value_json::text FROM hhy.system_configs WHERE key=? AND scope='GLOBAL'")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return fallback;
                try { return Long.parseLong(rows.getString(1).replace("\"", "")); }
                catch (NumberFormatException ignored) { return fallback; }
            }
        }
    }

    private boolean configBoolean(Connection connection, String key, boolean fallback) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value_json::text FROM hhy.system_configs WHERE key=? AND scope='GLOBAL'")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return fallback;
                return Boolean.parseBoolean(rows.getString(1));
            }
        }
    }

    private CommandResultResource command(String json, String status, long logId, Instant createdAt) {
        Map<String, Object> result = object(json);
        String resourceId = String.valueOf(result.getOrDefault("resourceId", Long.toString(logId)));
        String businessNo = String.valueOf(result.getOrDefault("businessNo", "R19-EXEC-" + logId));
        long version = numberValue(result.get("version"), 0);
        Instant acceptedAt = parseInstant(result.get("acceptedAt"), createdAt);
        return new CommandResultResource(resourceId, businessNo, status, version, acceptedAt);
    }

    private static long numberValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static Instant parseInstant(Object value, Instant fallback) {
        try { return value == null ? fallback : Instant.parse(value.toString()); }
        catch (Exception ignored) { return fallback; }
    }

    private Instant now() { return Instant.now(clock); }

    private static Timestamp time(Instant value) { return Timestamp.from(value); }

    private static Instant instant(ResultSet rows, int column) throws SQLException {
        Timestamp timestamp = rows.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static int bind(PreparedStatement statement, List<Object> values) throws SQLException {
        int index = 1;
        for (Object value : values) {
            if (value == null) statement.setNull(index++, Types.VARCHAR);
            else if (value instanceof Long number) statement.setLong(index++, number);
            else if (value instanceof Integer number) statement.setInt(index++, number);
            else statement.setObject(index++, value);
        }
        return index;
    }

    private static long parseId(String value, String name) {
        try { long id = Long.parseLong(value); if (id > 0) return id; }
        catch (Exception ignored) { }
        throw new StoreException(Kind.INVALID_DATA, name + "标识无效");
    }

    private static String text(Object value, int max, String name) {
        String text = value == null ? "" : value.toString().strip();
        if (text.isEmpty() || text.length() > max) throw rule(name + "不符合要求");
        return text;
    }

    private static int number(Object value, String name) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value, 32, name)); }
        catch (NumberFormatException failure) { throw rule(name + "不符合要求"); }
    }

    private <T> T transaction(Work<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean auto = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                T result = work.run(connection); connection.commit(); return result;
            } catch (StoreException failure) {
                rollback(connection); throw failure;
            } catch (SQLException failure) {
                rollback(connection); throw sql(failure);
            } catch (RuntimeException failure) {
                rollback(connection); throw failure;
            } finally { connection.setAutoCommit(auto); }
        } catch (SQLException failure) { throw sql(failure); }
    }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    private static StoreException sql(SQLException failure) {
        String state = failure.getSQLState();
        if ("23505".equals(state) || "40001".equals(state)) return conflict("道具请求发生并发冲突", failure);
        if ("23514".equals(state) || "23".equals(state)) return rule("道具数据不满足业务不变量", failure);
        return new StoreException(Kind.INTERNAL, "R19道具存储失败", failure);
    }

    private static StoreException notFound(String message) { return new StoreException(Kind.NOT_FOUND, message); }
    private static StoreException conflict(String message) { return new StoreException(Kind.CONFLICT, message); }
    private static StoreException conflict(String message, Throwable cause) { return new StoreException(Kind.CONFLICT, message, cause); }
    private static StoreException rule(String message) { return new StoreException(Kind.BUSINESS_RULE, message); }
    private static StoreException rule(String message, Throwable cause) { return new StoreException(Kind.BUSINESS_RULE, message, cause); }

    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws SQLException; }
    private record ExistingOrder(long id, String orderNo, String status, long version, Instant createdAt, String requestHash) {
        CommandResultResource result() { return new CommandResultResource(Long.toString(id), orderNo, status, version, createdAt); }
    }
    private record SkuHead(long id, long productSkuId, String status, long version,
            String propType, String name, long priceCent, String productSkuStatus,
            Long memberPriceCent) { }
    private record InventoryHead(long id, long quantity, String status, Instant expiresAt,
            long version, String propType) { }
    private record EntitlementResult(long entitlementId) { }
    private record ExecutionHead(long id, String result, String status, String requestHash, Instant createdAt) { }
    private record Idempotency(String requestHash, String responseRef) { }
}
