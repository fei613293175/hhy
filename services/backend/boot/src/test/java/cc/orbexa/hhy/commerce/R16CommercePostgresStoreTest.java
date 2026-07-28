package cc.orbexa.hhy.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.commerce.R16CommerceContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R16CommerceContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class R16CommercePostgresStoreTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void realPostgresPersistsEncryptedReplayAuditOutboxAndOptimisticVersion() {
        Fixture fixture = fixture();
        String suffix = suffix();
        ProductCreateRequest create =
                new ProductCreateRequest("P-" + suffix, "商业商品", "APP", "真实商品", 10, "ACTIVE");
        ProductResource first = fixture.service.createProduct(
                fixture.actor("adminProductsPostProducts"), create, "r16-product-key-" + suffix);
        ProductResource replay = fixture.service.createProduct(
                fixture.actor("adminProductsPostProducts"), create, "r16-product-key-" + suffix);

        assertEquals(first, replay);
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.admin_operation_logs"
                        + " WHERE action='PRODUCT_CREATE' AND resource_id=?", Long.parseLong(first.id())));
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.outbox_events"
                        + " WHERE event_type='commerce.product.created.v1' AND aggregate_id=?", first.id()));
        String ciphertext = fixture.jdbc.queryForObject(
                "SELECT response_payload_ciphertext FROM hhy.idempotency_records"
                        + " WHERE scope LIKE 'r16adm:%' AND idem_key=?",
                String.class, "r16-product-key-" + suffix);
        assertNotNull(ciphertext);
        assertTrue(!ciphertext.contains("商业商品"));
        assertTrue(fixture.service.products(
                1, 100, null, "ACTIVE", "商业商品", "updatedAt:desc").items().stream()
                .anyMatch(item -> item.id().equals(first.id())));

        ProductPatchRequest patch =
                new ProductPatchRequest("商业商品二版", null, null, null, null, first.version());
        ProductResource changed = fixture.service.patchProduct(
                fixture.actor("adminProductsPatchProductsById"), first.id(), patch,
                "r16-patch-key-" + suffix);
        assertEquals(1, changed.version());
        BusinessException stale = assertThrows(BusinessException.class, () -> fixture.service.patchProduct(
                fixture.actor("adminProductsPatchProductsById"), first.id(), patch,
                "r16-stale-key-" + suffix));
        assertEquals("COMMON-409-VERSION_CONFLICT", stale.code());
    }

    @Test
    void realPostgresEnforcesOrderOwnerAndBuildsExactSnapshots() throws Exception {
        Fixture fixture = fixture();
        String suffix = suffix();
        long owner = fixture.user("17" + suffix.substring(0, 9), "O" + suffix.substring(0, 12));
        long stranger = fixture.user("18" + suffix.substring(0, 9), "S" + suffix.substring(0, 12));
        ProductResource product = fixture.service.createProduct(
                fixture.actor("adminProductsPostProducts"),
                new ProductCreateRequest("PO-" + suffix, "订单商品", "SERVICE", null, 0, "ACTIVE"),
                "r16-order-product-" + suffix);
        ProductSkuResource sku = fixture.service.createSku(
                fixture.actor("adminProductsPostSkus"),
                new ProductSkuCreateRequest(
                        product.id(), "SO-" + suffix, "订单SKU", 18800L, 16800L, 365L,
                        List.of(new BenefitResource("DAYS", "服务时长", 365, "天")),
                        true, 500, 200, null, null, "ACTIVE"),
                "r16-order-sku-" + suffix);
        assertTrue(fixture.service.skus(
                1, 100, null, "ACTIVE", "订单SKU", "updatedAt:desc").items().stream()
                .anyMatch(item -> item.id().equals(sku.id())));
        String orderNo = "R16-" + suffix;
        fixture.order(owner, Long.parseLong(sku.id()), orderNo);

        var detail = fixture.service.userOrder(owner, orderNo);
        assertEquals(Long.toString(owner), detail.userId());
        assertEquals(1, detail.items().size());
        assertEquals(18800, detail.priceSnapshot().payableAmountCent());
        assertEquals(List.of("quote-r16-v1"), detail.priceSnapshot().ruleVersions());
        assertTrue(!detail.noRefundEvidence().confirmed());
        assertEquals("", detail.noRefundEvidence().agreementVersion());

        BusinessException hidden = assertThrows(
                BusinessException.class, () -> fixture.service.userOrder(stranger, orderNo));
        assertEquals("COMMON-404-NOT_FOUND", hidden.code());
        assertTrue(fixture.service.userOrders(
                stranger, 1, 20, null, null, null, "createdAt:desc").items().isEmpty());
    }

    @Test
    void faultAfterAuditRollsBackBusinessIdempotencyAuditAndOutboxTogether() {
        Fixture fixture = fixture();
        String suffix = suffix();
        R16CommerceStore failingStore = new R16CommercePostgresStore(
                fixture.dataSource, fixture.codec, CLOCK,
                point -> {
                    if ("AFTER_AUDIT".equals(point)) throw new Probe();
                });
        R16CommerceService failing = new R16CommerceService(failingStore, fixture.codec);

        R16CommerceStore.StoreException failure =
                assertThrows(R16CommerceStore.StoreException.class, () -> failing.createProduct(
                fixture.actor("adminProductsPostProducts"),
                new ProductCreateRequest(
                        "PF-" + suffix, "故障商品", "APP", null, 0, "ACTIVE"),
                "r16-fault-key-" + suffix));
        assertTrue(failure.getCause() instanceof Probe);
        assertEquals(0L, fixture.count(
                "SELECT count(*) FROM hhy.products WHERE product_code=?", "PF-" + suffix));
        assertEquals(0L, fixture.count(
                "SELECT count(*) FROM hhy.idempotency_records WHERE idem_key=?",
                "r16-fault-key-" + suffix));
        assertEquals(0L, fixture.count(
                "SELECT count(*) FROM hhy.admin_operation_logs WHERE action='PRODUCT_CREATE'"
                        + " AND after_json->>'productCode'=?", "PF-" + suffix));
        assertEquals(0L, fixture.count(
                "SELECT count(*) FROM hhy.outbox_events"
                        + " WHERE payload->>'productCode'=?", "PF-" + suffix));
    }

    private static Fixture fixture() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema("public")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Long> admins = jdbc.queryForList(
                "SELECT id FROM hhy.admin_users WHERE status='ACTIVE' ORDER BY id LIMIT 1",
                Long.class);
        long adminId = admins.isEmpty()
                ? jdbc.queryForObject("""
                        INSERT INTO hhy.admin_users(username,password_hash,status)
                        VALUES (?,'r16-test-password-hash','ACTIVE') RETURNING id
                        """, Long.class, "r16-admin-" + suffix())
                : admins.getFirst();
        Codec codec = new Codec();
        R16CommerceStore store = new R16CommercePostgresStore(dataSource, codec, CLOCK);
        return new Fixture(dataSource, jdbc, codec, new R16CommerceService(store, codec), adminId);
    }

    private static DriverManagerDataSource dataSource() {
        String url = System.getenv("HHY_DB_MIGRATION_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank()
                        && "YES".equals(System.getenv("HHY_DB_SMOKE_CONFIRM")),
                "requires an explicitly confirmed disposable PostgreSQL database");
        return new DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_USER", ""),
                System.getenv().getOrDefault("HHY_DB_MIGRATION_TEST_PASSWORD", ""));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private record Fixture(
            DriverManagerDataSource dataSource,
            JdbcTemplate jdbc,
            Codec codec,
            R16CommerceService service,
            long adminId) {
        AdminActorContext actor(String operationId) {
            return new AdminActorContext(
                    adminId, 1, "r16-admin", operationId, "r16-request-" + suffix(), "127.0.0.1");
        }

        long user(String phone, String inviteCode) {
            return jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, phone, inviteCode);
        }

        long count(String sql, Object... args) {
            return jdbc.queryForObject(sql, Long.class, args);
        }

        void order(long userId, long skuId, String orderNo) throws Exception {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    long orderId;
                    try (var statement = connection.prepareStatement("""
                            INSERT INTO hhy.orders(
                              order_no,user_id,biz_type,biz_id,amount_cent,status,currency,
                              no_refund_confirmed,idempotency_key,request_hash,legacy_without_idempotency
                            ) VALUES (?,?,?,?,?,'PENDING_PAYMENT','CNY',false,?,?,false)
                            RETURNING id
                            """)) {
                        statement.setString(1, orderNo);
                        statement.setLong(2, userId);
                        statement.setString(3, "PRODUCT_SKU");
                        statement.setLong(4, skuId);
                        statement.setLong(5, 18800);
                        statement.setString(6, "r16-order-key-" + orderNo);
                        statement.setString(7, "a".repeat(64));
                        try (var rows = statement.executeQuery()) {
                            rows.next();
                            orderId = rows.getLong(1);
                        }
                    }
                    try (var statement = connection.prepareStatement("""
                            INSERT INTO hhy.order_items(
                              order_id,sku_id,quantity,unit_price,snapshot_json,
                              item_name,subtotal_amount_cent
                            ) VALUES (?,?,?,?,CAST(? AS jsonb),?,?)
                            """)) {
                        statement.setLong(1, orderId);
                        statement.setLong(2, skuId);
                        statement.setInt(3, 1);
                        statement.setLong(4, 18800);
                        statement.setString(5, "{\"name\":\"订单SKU\"}");
                        statement.setString(6, "订单SKU");
                        statement.setLong(7, 18800);
                        statement.executeUpdate();
                    }
                    try (var statement = connection.prepareStatement("""
                            INSERT INTO hhy.order_price_snapshots(
                              order_id,original,discount,service_fee,payable,
                              rule_versions,rule_versions_json
                            ) VALUES (?,?,?,?,?,?,CAST(? AS jsonb))
                            """)) {
                        statement.setLong(1, orderId);
                        statement.setLong(2, 18800);
                        statement.setLong(3, 0);
                        statement.setLong(4, 0);
                        statement.setLong(5, 18800);
                        statement.setString(6, "quote-r16-v1");
                        statement.setString(7, "[\"quote-r16-v1\"]");
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            }
        }
    }

    private static final class Codec implements R16CommerceStore.Codec {
        private final ObjectMapper mapper =
                new ObjectMapper().findAndRegisterModules()
                        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        @Override
        public byte[] canonicalBytes(Map<String, Object> value) {
            return bytes(value);
        }

        @Override
        public String json(Object value) {
            return new String(bytes(value), StandardCharsets.UTF_8);
        }

        @Override
        public List<BenefitResource> benefits(String json) {
            return read(json, new TypeReference<>() { });
        }

        @Override
        public List<String> strings(String json) {
            return read(json, new TypeReference<>() { });
        }

        @Override
        public String encrypt(
                String scope, String key, String hash, String type, byte[] plaintext) {
            return Base64.getEncoder().encodeToString(xor(plaintext));
        }

        @Override
        public byte[] decrypt(
                String scope, String key, String hash, String type, String envelope) {
            return xor(Base64.getDecoder().decode(envelope));
        }

        @Override
        public ProductResource product(byte[] json) {
            return read(json, ProductResource.class);
        }

        @Override
        public ProductSkuResource sku(byte[] json) {
            return read(json, ProductSkuResource.class);
        }

        private byte[] bytes(Object value) {
            try {
                return mapper.writeValueAsBytes(value);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        private <T> T read(String json, TypeReference<T> type) {
            try {
                return mapper.readValue(json, type);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        private <T> T read(byte[] json, Class<T> type) {
            try {
                return mapper.readValue(json, type);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        private static byte[] xor(byte[] input) {
            byte[] output = input.clone();
            for (int index = 0; index < output.length; index++) output[index] ^= 0x5a;
            return output;
        }
    }

    private static final class Probe extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
