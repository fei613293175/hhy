package cc.orbexa.hhy.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cc.orbexa.hhy.commerce.R19PropContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R19PropContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R19PropContracts.PropOrderRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropCreateRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPage;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPatchRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropResource;
import cc.orbexa.hhy.commerce.R19PropContracts.PropUseRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class R19PropPostgresStoreTest {
    private static final Clock CLOCK = Clock.systemUTC();

    @Test
    void orderUseOwnershipIdempotencyAdminMutationAndSlotCapacityAreTransactional() {
        Fixture fixture = fixture();
        String suffix = suffix();
        PropCreateRequest createRequest = new PropCreateRequest(
                "REFRESH", "后台刷新道具", 600L, "IMMEDIATE",
                "CREATE-PRODUCT-" + suffix, "CREATE-SKU-" + suffix,
                120L, 90L, Map.of("contentTypes", java.util.List.of("PROJECT")),
                "ACTIVE", "R19后台创建集成测试");
        PropResource created = fixture.service.create(
                fixture.actor(), createRequest, "r19-create-" + suffix);
        PropResource createReplay = fixture.service.create(
                fixture.actor(), createRequest, "r19-create-" + suffix);
        assertEquals(created, createReplay);
        long createdSku = Long.parseLong(created.id());
        assertEquals(1, fixture.jdbc.queryForObject(
                "SELECT duration_days FROM hhy.product_skus sku JOIN hhy.prop_skus ps "
                        + "ON ps.product_sku_id=sku.id WHERE ps.id=?", Integer.class, createdSku));
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.products WHERE product_code=?",
                createRequest.productCode()));

        BusinessException idempotencyConflict = assertThrows(BusinessException.class, () ->
                fixture.service.create(fixture.actor(), new PropCreateRequest(
                        "REFRESH", "变化后的名称", 600L, "IMMEDIATE",
                        createRequest.productCode(), createRequest.skuCode(), 120L, 90L,
                        Map.of(), "ACTIVE", "变化请求"), "r19-create-" + suffix));
        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", idempotencyConflict.code());

        String rollbackProductCode = "ROLLBACK-PRODUCT-" + suffix;
        assertThrows(BusinessException.class, () -> fixture.service.create(
                fixture.actor(), new PropCreateRequest(
                        "TOP", "冲突回滚", 86400L, "IMMEDIATE", rollbackProductCode,
                        createRequest.skuCode(), 500L, null, Map.of(), "ACTIVE", "冲突回滚测试"),
                "r19-create-rollback-" + suffix));
        assertEquals(0L, fixture.count(
                "SELECT count(*) FROM hhy.products WHERE product_code=?", rollbackProductCode));

        long userId = fixture.user("17" + suffix.substring(0, 9), "P" + suffix.substring(0, 12));
        long otherUserId = fixture.user("18" + suffix.substring(0, 9), "Q" + suffix.substring(0, 12));
        long contentId = fixture.content(userId, "R19 own " + suffix);
        long otherContentId = fixture.content(otherUserId, "R19 other " + suffix);
        long refreshSku = fixture.propSku("REFRESH", "刷新道具", 100, suffix + "R");

        PropPage store = fixture.service.propStore(1, 1, 20, null, null, null, "name:asc");
        assertEquals(refreshSku, Long.parseLong(store.items().stream()
                .filter(item -> item.id().equals(Long.toString(refreshSku)))
                .findFirst().orElseThrow().id()));

        CommandResultResource order = fixture.service.order(
                userId, new PropOrderRequest(Long.toString(refreshSku), 2L, "ALIPAY"),
                "r19-buy-" + suffix, "r19-request-buy-" + suffix);
        CommandResultResource replay = fixture.service.order(
                userId, new PropOrderRequest(Long.toString(refreshSku), 2L, "ALIPAY"),
                "r19-buy-" + suffix, "r19-request-buy-replay-" + suffix);
        assertEquals(order.resourceId(), replay.resourceId());
        fixture.pay(order.businessNo());
        assertEquals("COMPLETED", fixture.status(order.businessNo()));

        long inventoryId = fixture.jdbc.queryForObject(
                "SELECT id FROM hhy.user_props WHERE acquired_order_id=?",
                Long.class, Long.parseLong(order.resourceId()));
        CommandResultResource used = fixture.service.use(
                userId, Long.toString(inventoryId),
                new PropUseRequest(Long.toString(contentId), null, 0L),
                "r19-use-" + suffix, "r19-request-use-" + suffix);
        CommandResultResource useReplay = fixture.service.use(
                userId, Long.toString(inventoryId),
                new PropUseRequest(Long.toString(contentId), null, 0L),
                "r19-use-" + suffix, "r19-request-use-replay-" + suffix);
        assertEquals(used, useReplay);
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.prop_execution_logs WHERE user_prop_id=?",
                inventoryId));
        assertEquals(1L, fixture.jdbc.queryForObject(
                "SELECT quantity FROM hhy.user_props WHERE id=?", Long.class, inventoryId));

        BusinessException forbiddenTarget = assertThrows(BusinessException.class, () ->
                fixture.service.use(
                        userId, Long.toString(inventoryId),
                        new PropUseRequest(Long.toString(otherContentId), null, 1L),
                        "r19-use-other-" + suffix, "r19-request-other-" + suffix));
        assertEquals("COMMON-404-NOT_FOUND", forbiddenTarget.code());
        assertEquals(1L, fixture.jdbc.queryForObject(
                "SELECT quantity FROM hhy.user_props WHERE id=?", Long.class, inventoryId));

        PropResource patched = fixture.service.patch(
                fixture.actor(), Long.toString(refreshSku),
                new PropPatchRequest("下架测试", 0L, Map.of("status", "INACTIVE")),
                "r19-patch-" + suffix);
        PropResource patchReplay = fixture.service.patch(
                fixture.actor(), Long.toString(refreshSku),
                new PropPatchRequest("下架测试", 0L, Map.of("status", "INACTIVE")),
                "r19-patch-" + suffix);
        assertEquals(patched, patchReplay);
        assertEquals("INACTIVE", fixture.jdbc.queryForObject(
                "SELECT status FROM hhy.prop_skus WHERE id=?", String.class, refreshSku));
        assertEquals("INACTIVE", fixture.jdbc.queryForObject(
                "SELECT product.status FROM hhy.prop_products product "
                        + "JOIN hhy.prop_skus sku ON sku.prop_id=product.id WHERE sku.id=?",
                String.class, refreshSku));
        assertEquals("INACTIVE", fixture.jdbc.queryForObject(
                "SELECT product_sku.status FROM hhy.product_skus product_sku "
                        + "JOIN hhy.prop_skus sku ON sku.product_sku_id=product_sku.id WHERE sku.id=?",
                String.class, refreshSku));
        assertEquals("INACTIVE", fixture.jdbc.queryForObject(
                "SELECT product.status FROM hhy.products product "
                        + "JOIN hhy.product_skus product_sku ON product_sku.product_id=product.id "
                        + "JOIN hhy.prop_skus sku ON sku.product_sku_id=product_sku.id WHERE sku.id=?",
                String.class, refreshSku));
        assertEquals(false, fixture.service.propStore(
                userId, 1, 100, null, null, null, "name:asc").items().stream()
                .anyMatch(item -> item.id().equals(Long.toString(refreshSku))));
        assertEquals(true, fixture.service.adminProps(
                1, 100, null, "INACTIVE", null, "name:asc").items().stream()
                .anyMatch(item -> item.id().equals(Long.toString(refreshSku))));

        long headlineSku = fixture.propSku("HEADLINE", "头条道具", 500, suffix + "H");
        long headlineInventory = fixture.inventory(userId, headlineSku, 1);
        long firstSlot = fixture.slot("HOME", "FIRST-" + suffix);
        long secondSlot = fixture.slot("HOME", "SECOND-" + suffix);
        long blockerInventory = fixture.inventory(otherUserId, headlineSku, 1);
        Instant startsAt = Instant.now(CLOCK).plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(86400);
        fixture.booking(firstSlot, otherContentId, blockerInventory, startsAt, endsAt);

        fixture.service.use(
                userId, Long.toString(headlineInventory),
                new PropUseRequest(Long.toString(contentId), startsAt, 0L),
                "r19-headline-" + suffix, "r19-request-headline-" + suffix);
        long selectedSlot = fixture.jdbc.queryForObject(
                "SELECT slot_id FROM hhy.headline_slot_bookings "
                        + "WHERE content_id=? ORDER BY id DESC LIMIT 1",
                Long.class, contentId);
        assertEquals(secondSlot, selectedSlot);
        assertNotEquals(firstSlot, selectedSlot);
    }

    private static Fixture fixture() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).defaultSchema("public")
                .locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long adminId = jdbc.queryForObject("""
                INSERT INTO hhy.admin_users(username,password_hash,status)
                VALUES (?,'r19-test-password-hash','ACTIVE') RETURNING id
                """, Long.class, "r19-admin-" + suffix());
        Codec codec = new Codec();
        R19PropStore store = new R19PropPostgresStore(dataSource, codec, CLOCK);
        return new Fixture(jdbc, new R19PropService(store, codec), adminId);
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

    private record Fixture(JdbcTemplate jdbc, R19PropService service, long adminId) {
        long user(String phone, String inviteCode) {
            return jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, phone, inviteCode);
        }

        long content(long ownerId, String title) {
            TransactionTemplate transaction = new TransactionTemplate(
                    new DataSourceTransactionManager(jdbc.getDataSource()));
            return transaction.execute(status -> {
                long contentId = jdbc.queryForObject("""
                        INSERT INTO hhy.content_posts(owner_id,type,title,status)
                        VALUES (?,'PROJECT',?,'DRAFT') RETURNING id
                        """, Long.class, ownerId, title);
                jdbc.update("INSERT INTO hhy.project_details(content_id,cooperation) VALUES (?,?)",
                        contentId, "R19 integration fixture");
                return contentId;
            });
        }

        long propSku(String type, String name, long price, String code) {
            long productId = jdbc.queryForObject("""
                    INSERT INTO hhy.products(product_code,type,name,status,display_order,version)
                    VALUES (?,'PROP',?,'ACTIVE',1,0) RETURNING id
                    """, Long.class, "PRODUCT-" + code, name);
            long productSkuId = jdbc.queryForObject("""
                    INSERT INTO hhy.product_skus(
                      product_id,code,name,price_cent,duration_days,benefits_json,status,version)
                    VALUES (?,?,?,?,1,CAST(? AS jsonb),'ACTIVE',0) RETURNING id
                    """, Long.class, productId, "SKU-" + code, name, price,
                    "[{\"benefitCode\":\"PROP_USE\",\"name\":\"道具使用次数\","
                            + "\"value\":1,\"unit\":\"COUNT\"}]");
            long propId = jdbc.queryForObject("""
                    INSERT INTO hhy.prop_products(
                      type,name,duration_seconds,execution_type,status,version)
                    VALUES (?,?,?,'IMMEDIATE','ACTIVE',0) RETURNING id
                    """, Long.class, type, name, "REFRESH".equals(type) ? "600" : "86400");
            return jdbc.queryForObject("""
                    INSERT INTO hhy.prop_skus(
                      prop_id,product_sku_id,scope_json,status,version)
                    VALUES (?,?,CAST('{}' AS jsonb),'ACTIVE',0) RETURNING id
                    """, Long.class, propId, productSkuId);
        }

        long inventory(long userId, long propSkuId, long quantity) {
            return jdbc.queryForObject("""
                    INSERT INTO hhy.user_props(
                      user_id,prop_sku_id,quantity,status,expires_at,version)
                    VALUES (?,?,?,'AVAILABLE',clock_timestamp()+interval '7 days',0)
                    RETURNING id
                    """, Long.class, userId, propSkuId, quantity);
        }

        long slot(String pageCode, String slotCode) {
            return jdbc.queryForObject("""
                    INSERT INTO hhy.headline_slots(page_code,slot_code,capacity,status,version)
                    VALUES (?,?,1,'ACTIVE',0) RETURNING id
                    """, Long.class, pageCode, slotCode);
        }

        void booking(
                long slotId, long contentId, long userPropId, Instant startsAt, Instant endsAt) {
            long entitlementId = jdbc.queryForObject("""
                    INSERT INTO hhy.content_exposure_entitlements(
                      content_id,user_prop_id,type,starts_at,ends_at,status,version)
                    VALUES (?,?, 'HEADLINE',?,?, 'SCHEDULED',0) RETURNING id
                    """, Long.class, contentId, userPropId,
                    Timestamp.from(startsAt), Timestamp.from(endsAt));
            jdbc.update("""
                    INSERT INTO hhy.headline_slot_bookings(
                      slot_id,content_id,entitlement_id,starts_at,ends_at,status,version)
                    VALUES (?,?,?,?,?,'BOOKED',0)
                    """, slotId, contentId, entitlementId,
                    Timestamp.from(startsAt), Timestamp.from(endsAt));
        }

        void pay(String orderNo) {
            jdbc.update("""
                    UPDATE hhy.orders SET status='PAYMENT_PROCESSING',version=version+1
                    WHERE order_no=?
                    """, orderNo);
            jdbc.update("""
                    UPDATE hhy.orders
                    SET status='PAID',paid_amount_cent=amount_cent,
                        paid_at=clock_timestamp(),version=version+1
                    WHERE order_no=?
                    """, orderNo);
        }

        String status(String orderNo) {
            return jdbc.queryForObject(
                    "SELECT status FROM hhy.orders WHERE order_no=?", String.class, orderNo);
        }

        long count(String sql, Object... args) {
            return jdbc.queryForObject(sql, Long.class, args);
        }

        AdminActorContext actor() {
            return new AdminActorContext(
                    adminId, 1, "r19-admin", "adminPropsPatchPropsById",
                    "r19-admin-request-" + suffix(), "127.0.0.1");
        }
    }

    private static final class Codec implements R19PropStore.Codec {
        private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        @Override
        public byte[] canonicalBytes(Object value) {
            return bytes(value);
        }

        @Override
        public String json(Object value) {
            return new String(bytes(value), StandardCharsets.UTF_8);
        }

        @Override
        public Map<String, Object> object(String json) {
            try {
                return mapper.readValue(json, new TypeReference<>() { });
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        private byte[] bytes(Object value) {
            try {
                return mapper.writeValueAsBytes(value);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
