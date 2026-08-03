package cc.orbexa.hhy.commerce;

import cc.orbexa.hhy.commerce.R17PaymentContracts.AdminCommandContext;
import cc.orbexa.hhy.commerce.R17PaymentContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.CreatePaymentRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentResource;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderCallbackContext;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ProviderNotification;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ReconciliationRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.ResolvePaymentExceptionRequest;
import cc.orbexa.hhy.commerce.R17PaymentContracts.UserCommandContext;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/** Transactional PostgreSQL implementation for R17 payments. */
public final class R17PaymentPostgresStore implements R17PaymentStore {
    private static final String PAYMENT_COLUMNS = """
            p.id,p.gateway,p.provider_trade_no,p.status,p.amount,p.currency,
            p.paid_at,p.reconciliation_status,p.version,o.order_no
            """;
    private static final String PAYMENT_RESPONSE = "r17.PaymentResource.v1";
    private static final String COMMAND_RESPONSE = "r17.CommandResultResource.v1";
    private final DataSource dataSource;
    private final Codec codec;
    private final Clock clock;

    public R17PaymentPostgresStore(DataSource dataSource, Codec codec, Clock clock) {
        this.dataSource = dataSource;
        this.codec = codec;
        this.clock = clock;
    }

    @Override
    public Optional<PaymentResource> cashier(long userId, String orderNo) {
        String sql = """
                SELECT o.id AS order_id,o.order_no,o.amount_cent,o.currency,o.version AS order_version,
                       p.id,p.gateway,p.provider_trade_no,p.status,p.amount,p.paid_at,
                       p.reconciliation_status,p.version
                FROM hhy.orders o
                LEFT JOIN LATERAL (
                  SELECT payment.* FROM hhy.payment_transactions payment
                  WHERE payment.order_id=o.id ORDER BY payment.created_at DESC,payment.id DESC LIMIT 1
                ) p ON true
                WHERE o.user_id=? AND o.order_no=?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, orderNo);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                if (rows.getObject("id") != null) return Optional.of(readPayment(rows));
                return Optional.of(new PaymentResource(
                        "cashier-" + rows.getLong("order_id"), rows.getString("order_no"),
                        "UNSELECTED", null, "READY", rows.getLong("amount_cent"),
                        rows.getString("currency"), null, "NOT_RECONCILED",
                        rows.getLong("order_version")));
            }
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    @Override
    public PageSlice payments(Long userId, PageQuery query) {
        String from = " FROM hhy.payment_transactions p JOIN hhy.orders o ON o.id=p.order_id ";
        QueryParts parts = filters(userId, query, from, false);
        try (Connection connection = dataSource.getConnection()) {
            long total = count(connection, parts);
            String sql = "SELECT " + PAYMENT_COLUMNS + parts.sql()
                    + orderBy(query.sort()) + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = bind(statement, parts.values());
                statement.setInt(index++, query.pageSize());
                statement.setLong(index, query.offset());
                List<PaymentResource> result = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(readPayment(rows));
                }
                return new PageSlice(result, total);
            }
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    @Override
    public PaymentResource createPayment(
            UserCommandContext context,
            String orderNo,
            CreatePaymentRequest request,
            String requestHash) {
        return transaction(connection -> {
            OrderHead order = orderForUpdate(connection, context.userId(), orderNo);
            PaymentResource replay = paymentByIdempotency(
                    connection, order.id(), context.idempotencyKey(), requestHash);
            if (replay != null) return replay;
            if (!"PENDING_PAYMENT".equals(order.status())) {
                throw new StoreException(Kind.BUSINESS_RULE, "订单当前状态不允许创建支付");
            }
            if (!order.noRefundConfirmed()) {
                throw new StoreException(Kind.BUSINESS_RULE, "请先确认不可退款约定");
            }
            long id;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hhy.payment_transactions(
                      order_id,gateway,amount,status,currency,reconciliation_status,
                      return_url,idempotency_key,request_hash,legacy_without_idempotency)
                    VALUES (?,?,?,'PENDING',?,'NOT_RECONCILED',?,?,?,false)
                    RETURNING id
                    """)) {
                statement.setLong(1, order.id());
                statement.setString(2, request.gateway());
                statement.setLong(3, order.amountCent());
                statement.setString(4, order.currency());
                statement.setString(5, request.returnUrl());
                statement.setString(6, context.idempotencyKey());
                statement.setString(7, requestHash);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    id = rows.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE hhy.orders SET status='PAYMENT_PROCESSING',version=version+1
                    WHERE id=? AND status='PENDING_PAYMENT'
                    """)) {
                statement.setLong(1, order.id());
                if (statement.executeUpdate() != 1) {
                    throw new StoreException(Kind.CONFLICT, "订单状态已变化");
                }
            }
            return payment(connection, id);
        });
    }

    @Override
    public CallbackResult applyNotification(
            ProviderCallbackContext context,
            ProviderNotification notification,
            String payloadHash,
            String encryptedPayload) {
        return transaction(connection -> {
            CallbackExisting existing = callback(
                    connection, context.gateway(), notification.notificationId());
            if (existing != null) {
                if (!payloadHash.equals(existing.payloadHash())) {
                    throw new StoreException(Kind.CONFLICT, "回调编号对应的原始载荷不一致");
                }
                return new CallbackResult(
                        paymentByOrder(connection, null, notification.orderNo(), context.gateway()),
                        false, true);
            }
            OrderHead order = orderForUpdate(connection, null, notification.orderNo());
            PaymentResource current = paymentByOrder(
                    connection, null, notification.orderNo(), context.gateway());
            boolean mismatch = notification.amountCent() != null
                    && notification.amountCent() != order.amountCent()
                    || notification.currency() != null
                    && !notification.currency().equals(order.currency());
            String providerTradeNo = providerTradeNo(notification.rawPayload());
            long callbackId = insertCallback(
                    connection, context, notification, payloadHash, encryptedPayload,
                    providerTradeNo, !mismatch);
            if (mismatch) {
                createException(connection, order.id(), "AMOUNT_MISMATCH");
                return new CallbackResult(current, true, false);
            }
            String target = callbackStatus(notification.status());
            if ("PAID".equals(target)) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE hhy.payment_transactions
                        SET status='PAID',provider_trade_no=COALESCE(?,provider_trade_no),
                            paid_at=COALESCE(paid_at,clock_timestamp()),version=version+1
                        WHERE id=? AND status IN ('PENDING','UNKNOWN')
                        """)) {
                    nullable(statement, 1, providerTradeNo, Types.VARCHAR);
                    statement.setLong(2, Long.parseLong(current.id()));
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE hhy.orders
                        SET status='PAID',paid_amount_cent=amount_cent,
                            paid_at=COALESCE(paid_at,clock_timestamp()),version=version+1
                        WHERE id=? AND status='PAYMENT_PROCESSING'
                        """)) {
                    statement.setLong(1, order.id());
                    statement.executeUpdate();
                }
            } else if ("FAILED".equals(target)) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE hhy.payment_transactions
                        SET status='FAILED',provider_trade_no=COALESCE(?,provider_trade_no),version=version+1
                        WHERE id=? AND status IN ('PENDING','UNKNOWN')
                        """)) {
                    nullable(statement, 1, providerTradeNo, Types.VARCHAR);
                    statement.setLong(2, Long.parseLong(current.id()));
                    statement.executeUpdate();
                }
            }
            markCallbackProcessed(connection, callbackId);
            return new CallbackResult(
                    payment(connection, Long.parseLong(current.id())), false, false);
        });
    }

    @Override
    public PaymentResource queryPayment(
            AdminCommandContext context, long paymentId, Long expectedVersion, String requestHash) {
        return transaction(connection -> {
            PaymentResource payment = paymentForUpdate(connection, paymentId);
            if (expectedVersion != null && payment.version() != expectedVersion) {
                throw new StoreException(Kind.CONFLICT, "支付版本已变化");
            }
            audit(connection, context, "payment.query", paymentId, payment, payment);
            return payment;
        });
    }

    @Override
    public PageSlice callbacks(PageQuery query) {
        String from = """
                 FROM hhy.payment_callbacks c
                 JOIN hhy.orders o ON o.order_no=c.order_no
                 JOIN LATERAL (
                   SELECT payment.* FROM hhy.payment_transactions payment
                   WHERE payment.order_id=o.id AND payment.gateway=c.gateway
                   ORDER BY payment.created_at DESC,payment.id DESC LIMIT 1
                 ) p ON true
                """;
        return joinedPage(query, from, "c.callback_status", "c.notification_id");
    }

    @Override
    public PageSlice exceptions(PageQuery query) {
        String from = """
                 FROM hhy.payment_exception_orders e
                 JOIN hhy.orders o ON o.id=e.order_id
                 JOIN LATERAL (
                   SELECT payment.* FROM hhy.payment_transactions payment
                   WHERE payment.order_id=o.id
                   ORDER BY payment.created_at DESC,payment.id DESC LIMIT 1
                 ) p ON true
                """;
        return joinedPage(query, from, "e.status", "e.type");
    }

    @Override
    public CommandResultResource resolveException(
            AdminCommandContext context,
            long exceptionId,
            ResolvePaymentExceptionRequest request,
            String requestHash) {
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE hhy.payment_exception_orders
                    SET status='RESOLVED',resolution=?,reason=?,resolved_by_admin_id=?,
                        resolved_at=clock_timestamp(),version=version+1
                    WHERE id=? AND status<>'RESOLVED' AND version=?
                    RETURNING order_id,version
                    """)) {
                statement.setString(1, request.resolution());
                statement.setString(2, request.reason());
                statement.setLong(3, context.adminId());
                statement.setLong(4, exceptionId);
                statement.setLong(5, request.expectedVersion());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        if (!exists(connection, "payment_exception_orders", exceptionId)) {
                            throw new StoreException(Kind.NOT_FOUND, "异常账务不存在");
                        }
                        throw new StoreException(Kind.CONFLICT, "异常账务状态或版本已变化");
                    }
                    long orderId = rows.getLong(1);
                    long version = rows.getLong(2);
                    CommandResultResource result = new CommandResultResource(
                            Long.toString(exceptionId), Long.toString(orderId),
                            "RESOLVED", version, Instant.now(clock));
                    audit(connection, context, "payment.exception.resolve", exceptionId, null, result);
                    return result;
                }
            }
        });
    }

    @Override
    public CommandResultResource reconcile(
            AdminCommandContext context, ReconciliationRequest request, String requestHash) {
        return transaction(connection -> {
            boolean dryRun = Boolean.TRUE.equals(request.dryRun());
            String gateway = reconciliationGateway(request.parameters());
            long total = scalar(connection, """
                    SELECT count(*) FROM hhy.payment_transactions
                    WHERE (?='ALL' OR gateway=?)
                    """, gateway, gateway);
            long differences = scalar(connection, """
                    SELECT count(*)
                    FROM hhy.payment_transactions p JOIN hhy.orders o ON o.id=p.order_id
                    WHERE (?='ALL' OR p.gateway=?) AND (
                      (p.status='PAID') <> (o.status IN ('PAID','FULFILLING','COMPLETED'))
                      OR p.amount<>o.amount_cent OR p.currency<>o.currency)
                    """, gateway, gateway);
            long id;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO hhy.payment_reconciliation_records(
                      recon_date,gateway,result,diff_json,status,transaction_count,
                      difference_count,dry_run,initiated_by_admin_id,completed_at)
                    VALUES (current_date,?,jsonb_build_object('transactions',?,'differences',?),
                      '[]'::jsonb,'COMPLETED',?,?,?, ?,clock_timestamp())
                    ON CONFLICT(recon_date,gateway) DO UPDATE SET
                      result=EXCLUDED.result,diff_json=EXCLUDED.diff_json,status=EXCLUDED.status,
                      transaction_count=EXCLUDED.transaction_count,
                      difference_count=EXCLUDED.difference_count,dry_run=EXCLUDED.dry_run,
                      initiated_by_admin_id=EXCLUDED.initiated_by_admin_id,
                      completed_at=EXCLUDED.completed_at
                    RETURNING id
                    """)) {
                statement.setString(1, gateway);
                statement.setLong(2, total);
                statement.setLong(3, differences);
                statement.setLong(4, total);
                statement.setLong(5, differences);
                statement.setBoolean(6, dryRun);
                statement.setLong(7, context.adminId());
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    id = rows.getLong(1);
                }
            }
            CommandResultResource result = new CommandResultResource(
                    Long.toString(id), gateway + ":" + java.time.LocalDate.now(clock),
                    differences == 0 ? "MATCHED" : "DIFFERENCES_FOUND", null, Instant.now(clock));
            audit(connection, context, "payment.reconcile", id, null, result);
            return result;
        });
    }

    private PageSlice joinedPage(PageQuery query, String from, String statusColumn, String keywordColumn) {
        List<Object> values = new ArrayList<>();
        StringBuilder where = new StringBuilder(from).append(" WHERE true");
        if (query.status() != null) {
            where.append(" AND ").append(statusColumn).append("=?");
            values.add(query.status());
        }
        if (query.keyword() != null) {
            where.append(" AND (o.order_no ILIKE ? OR ").append(keywordColumn).append(" ILIKE ?)");
            values.add("%" + query.keyword() + "%");
            values.add("%" + query.keyword() + "%");
        }
        QueryParts parts = new QueryParts(where.toString(), values);
        try (Connection connection = dataSource.getConnection()) {
            long total = count(connection, parts);
            String sql = "SELECT " + PAYMENT_COLUMNS + parts.sql()
                    + " ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = bind(statement, values);
                statement.setInt(index++, query.pageSize());
                statement.setLong(index, query.offset());
                List<PaymentResource> items = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) items.add(readPayment(rows));
                }
                return new PageSlice(items, total);
            }
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    private QueryParts filters(Long userId, PageQuery query, String from, boolean ignored) {
        StringBuilder sql = new StringBuilder(from).append(" WHERE true");
        List<Object> values = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND o.user_id=?");
            values.add(userId);
        }
        if (query.status() != null) {
            sql.append(" AND p.status=?");
            values.add(query.status());
        }
        if (query.keyword() != null) {
            sql.append(" AND (o.order_no ILIKE ? OR COALESCE(p.provider_trade_no,'') ILIKE ?)");
            values.add("%" + query.keyword() + "%");
            values.add("%" + query.keyword() + "%");
        }
        return new QueryParts(sql.toString(), values);
    }

    private static String orderBy(String sort) {
        return switch (sort) {
            case "createdAt:asc" -> " ORDER BY p.created_at ASC,p.id ASC";
            case "amountCent:asc" -> " ORDER BY p.amount ASC,p.id ASC";
            case "amountCent:desc" -> " ORDER BY p.amount DESC,p.id DESC";
            default -> " ORDER BY p.created_at DESC,p.id DESC";
        };
    }

    private long count(Connection connection, QueryParts parts) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*)" + parts.sql())) {
            bind(statement, parts.values());
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

    private OrderHead orderForUpdate(Connection connection, Long userId, String orderNo) throws SQLException {
        String sql = """
                SELECT id,amount_cent,currency,status,no_refund_confirmed
                FROM hhy.orders WHERE order_no=?
                """ + (userId == null ? "" : " AND user_id=?") + " FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderNo);
            if (userId != null) statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StoreException(Kind.NOT_FOUND, "订单不存在或不可见");
                return new OrderHead(
                        rows.getLong(1), rows.getLong(2), rows.getString(3),
                        rows.getString(4), rows.getBoolean(5));
            }
        }
    }

    private PaymentResource paymentByIdempotency(
            Connection connection, long orderId, String key, String requestHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT request_hash,id FROM hhy.payment_transactions
                WHERE order_id=? AND idempotency_key=? FOR UPDATE
                """)) {
            statement.setLong(1, orderId);
            statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                if (!requestHash.equals(rows.getString(1))) {
                    throw new StoreException(Kind.CONFLICT, "幂等键已绑定不同请求");
                }
                return payment(connection, rows.getLong(2));
            }
        }
    }

    private PaymentResource paymentByOrder(
            Connection connection, Long userId, String orderNo, String gateway) throws SQLException {
        String sql = "SELECT " + PAYMENT_COLUMNS + """
                 FROM hhy.payment_transactions p JOIN hhy.orders o ON o.id=p.order_id
                 WHERE o.order_no=? AND p.gateway=?
                """ + (userId == null ? "" : " AND o.user_id=?")
                + " ORDER BY p.created_at DESC,p.id DESC LIMIT 1 FOR UPDATE OF p";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderNo);
            statement.setString(2, gateway);
            if (userId != null) statement.setLong(3, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StoreException(Kind.NOT_FOUND, "支付交易不存在");
                return readPayment(rows);
            }
        }
    }

    private PaymentResource payment(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + PAYMENT_COLUMNS
                        + " FROM hhy.payment_transactions p JOIN hhy.orders o ON o.id=p.order_id WHERE p.id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StoreException(Kind.NOT_FOUND, "支付交易不存在");
                return readPayment(rows);
            }
        }
    }

    private PaymentResource paymentForUpdate(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + PAYMENT_COLUMNS
                        + " FROM hhy.payment_transactions p JOIN hhy.orders o ON o.id=p.order_id WHERE p.id=? FOR UPDATE OF p")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StoreException(Kind.NOT_FOUND, "支付交易不存在");
                return readPayment(rows);
            }
        }
    }

    private static PaymentResource readPayment(ResultSet rows) throws SQLException {
        OffsetDateTime paid = rows.getObject("paid_at", OffsetDateTime.class);
        return new PaymentResource(
                Long.toString(rows.getLong("id")), rows.getString("order_no"),
                rows.getString("gateway"), rows.getString("provider_trade_no"),
                rows.getString("status"), rows.getLong("amount"), rows.getString("currency"),
                paid == null ? null : paid.toInstant(), rows.getString("reconciliation_status"),
                rows.getLong("version"));
    }

    private CallbackExisting callback(Connection connection, String gateway, String notificationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT payload_sha256 FROM hhy.payment_callbacks
                WHERE gateway=? AND notification_id=? FOR UPDATE
                """)) {
            statement.setString(1, gateway);
            statement.setString(2, notificationId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new CallbackExisting(rows.getString(1)) : null;
            }
        }
    }

    private long insertCallback(
            Connection connection,
            ProviderCallbackContext context,
            ProviderNotification notification,
            String payloadHash,
            String encryptedPayload,
            String providerTradeNo,
            boolean accepted) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.payment_callbacks(
                  gateway,provider_trade_no,payload_cipher,signature_valid,processed,
                  notification_id,event_type,order_no,amount_cent,currency,callback_status,
                  payload_sha256,provider_timestamp,provider_nonce,processed_at,
                  legacy_without_notification)
                VALUES (?,?,?,true,false,?,?,?,?,?,?,?,?,?,NULL,false) RETURNING id
                """)) {
            statement.setString(1, context.gateway());
            nullable(statement, 2, providerTradeNo, Types.VARCHAR);
            statement.setString(3, encryptedPayload);
            statement.setString(4, notification.notificationId());
            statement.setString(5, notification.eventType());
            statement.setString(6, notification.orderNo());
            nullable(statement, 7, notification.amountCent(), Types.BIGINT);
            nullable(statement, 8, notification.currency(), Types.VARCHAR);
            statement.setString(9, notification.status());
            statement.setString(10, payloadHash);
            statement.setObject(11, OffsetDateTime.parse(context.timestamp()));
            statement.setString(12, context.nonce());
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void markCallbackProcessed(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE hhy.payment_callbacks
                SET processed=true,processed_at=clock_timestamp() WHERE id=? AND processed=false
                """)) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) {
                throw new StoreException(Kind.CONFLICT, "回调处理状态已变化");
            }
        }
    }

    private static void createException(Connection connection, long orderId, String type)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.payment_exception_orders(order_id,type,status)
                SELECT ?,?,'OPEN'
                WHERE NOT EXISTS (
                  SELECT 1 FROM hhy.payment_exception_orders
                  WHERE order_id=? AND type=? AND status<>'RESOLVED')
                """)) {
            statement.setLong(1, orderId);
            statement.setString(2, type);
            statement.setLong(3, orderId);
            statement.setString(4, type);
            statement.executeUpdate();
        }
    }

    private static String providerTradeNo(Map<String, Object> payload) {
        Object value = payload.get("providerTradeNo");
        if (value == null) value = payload.get("trade_no");
        if (value == null) return null;
        String result = value.toString();
        return result.isBlank() || result.length() > 128 ? null : result;
    }

    private static String callbackStatus(String status) {
        return switch (status.toUpperCase(java.util.Locale.ROOT)) {
            case "PAID", "SUCCESS", "SUCCEEDED", "TRADE_SUCCESS" -> "PAID";
            case "FAILED", "CLOSED", "CANCELLED", "TRADE_CLOSED" -> "FAILED";
            default -> "PENDING";
        };
    }

    private static String reconciliationGateway(String parameters) {
        if (parameters == null || parameters.isBlank()) return "ALL";
        String normalized = parameters.strip().toUpperCase(java.util.Locale.ROOT);
        if (normalized.equals("ALIPAY") || normalized.equals("WECHAT_PAY") || normalized.equals("ALL")) {
            return normalized;
        }
        throw new StoreException(Kind.BUSINESS_RULE, "对账参数仅支持ALIPAY、WECHAT_PAY或ALL");
    }

    private static long scalar(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private void audit(
            Connection connection,
            AdminCommandContext context,
            String action,
            long resourceId,
            Object before,
            Object after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hhy.admin_operation_logs(
                  admin_id,action,resource,resource_id,before_json,after_json,ip)
                VALUES (?,?,?, ?,?::jsonb,?::jsonb,?)
                """)) {
            statement.setLong(1, context.adminId());
            statement.setString(2, action);
            statement.setString(3, "payment");
            statement.setLong(4, resourceId);
            nullable(statement, 5, before == null ? null : codec.json(before), Types.VARCHAR);
            nullable(statement, 6, after == null ? null : codec.json(after), Types.VARCHAR);
            statement.setString(7, context.ip());
            statement.executeUpdate();
        }
    }

    private static boolean exists(Connection connection, String table, long id) throws SQLException {
        String sql = switch (table) {
            case "payment_exception_orders" -> "SELECT 1 FROM hhy.payment_exception_orders WHERE id=?";
            default -> throw new IllegalArgumentException("Unsupported table");
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
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
                throw new StoreException(Kind.INTERNAL, "R17支付事务失败", failure);
            }
        } catch (SQLException failure) {
            throw sql(failure);
        }
    }

    private static StoreException sql(SQLException failure) {
        String state = failure.getSQLState();
        if ("23505".equals(state)) return new StoreException(Kind.CONFLICT, "资源或幂等键冲突", failure);
        if ("23514".equals(state) || "23503".equals(state)) {
            return new StoreException(Kind.BUSINESS_RULE, "支付数据不满足业务约束", failure);
        }
        return new StoreException(Kind.INTERNAL, "R17支付存储失败", failure);
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

    private record QueryParts(String sql, List<Object> values) { }
    private record OrderHead(long id, long amountCent, String currency, String status, boolean noRefundConfirmed) { }
    private record CallbackExisting(String payloadHash) { }
}
