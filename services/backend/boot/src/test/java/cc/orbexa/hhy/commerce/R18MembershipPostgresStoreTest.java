package cc.orbexa.hhy.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.orbexa.hhy.commerce.R12MembershipContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R18MembershipContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipOrderRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipSkuPatchRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipUpgradeOrderRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipUpgradeQuoteRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class R18MembershipPostgresStoreTest {
    private static final Clock CLOCK = Clock.systemUTC();

    @Test
    void purchaseUpgradeSnapshotsIdempotencyFulfillmentAndAdminMutationAreTransactional() {
        Fixture fixture = fixture();
        String suffix = suffix();
        long userId = fixture.user("16" + suffix.substring(0, 9), "M" + suffix.substring(0, 12));
        long planId = fixture.plan("PRO-" + suffix);
        long monthSku = fixture.sku(
                planId, "MONTH-" + suffix, "Pro月卡", 9800, 30, "MONTH", 2_592_000);
        long quarterSku = fixture.sku(
                planId, "QUARTER-" + suffix, "Pro季卡", 25_800, 90, "QUARTER", 7_776_000);

        CommandResultResource purchase = fixture.service.createPurchase(
                userId, new MembershipOrderRequest(Long.toString(monthSku), "ALIPAY"),
                "r18-buy-" + suffix, "r18-request-buy-" + suffix);
        CommandResultResource replay = fixture.service.createPurchase(
                userId, new MembershipOrderRequest(Long.toString(monthSku), "ALIPAY"),
                "r18-buy-" + suffix, "r18-request-buy-replay-" + suffix);
        assertEquals(purchase.resourceId(), replay.resourceId());
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.membership_benefit_snapshots WHERE order_id=?",
                Long.parseLong(purchase.resourceId())));

        fixture.pay(purchase.businessNo());
        MembershipResource current = fixture.service.current(userId);
        assertEquals("ACTIVE", current.status());
        assertEquals(Long.toString(monthSku), current.skuId());
        assertEquals(10, current.benefits().getFirst().value());
        assertEquals("COMPLETED", fixture.status(purchase.businessNo()));

        fixture.jdbc.update("""
                UPDATE hhy.membership_sku_benefits SET value_json='99'::jsonb
                WHERE membership_sku_id=(SELECT id FROM hhy.membership_skus WHERE sku_id=?)
                """, monthSku);
        assertEquals(10, fixture.service.current(userId).benefits().getFirst().value());

        MembershipResource quote = fixture.service.createUpgradeQuote(
                userId, new MembershipUpgradeQuoteRequest(Long.toString(quarterSku)),
                "r18-quote-" + suffix, "r18-request-quote-" + suffix);
        assertEquals(16_000L, quote.paidValueCent());
        assertEquals(9_800L, quote.remainingValueCent());

        CommandResultResource upgrade = fixture.service.createUpgradeOrder(
                userId,
                new MembershipUpgradeOrderRequest(quote.id(), "WECHAT_PAY"),
                "r18-upgrade-" + suffix, "r18-request-upgrade-" + suffix);
        fixture.pay(upgrade.businessNo());
        MembershipResource upgraded = fixture.service.current(userId);
        assertEquals(Long.toString(quarterSku), upgraded.skuId());
        assertEquals("COMPLETED", fixture.status(upgrade.businessNo()));
        assertTrue(fixture.count(
                "SELECT count(*) FROM hhy.membership_value_conversions WHERE upgrade_order_id=?",
                Long.parseLong(upgrade.resourceId())) >= 1);

        long membershipSkuId = fixture.jdbc.queryForObject(
                "SELECT id FROM hhy.membership_skus WHERE sku_id=?", Long.class, quarterSku);
        MembershipResource patched = fixture.service.patchSku(
                fixture.actor(), Long.toString(membershipSkuId),
                new MembershipSkuPatchRequest(
                        "Pro季卡新版", 26_800L, 92L, null, "ACTIVE", 0L),
                "r18-patch-" + suffix);
        MembershipResource patchReplay = fixture.service.patchSku(
                fixture.actor(), Long.toString(membershipSkuId),
                new MembershipSkuPatchRequest(
                        "Pro季卡新版", 26_800L, 92L, null, "ACTIVE", 0L),
                "r18-patch-" + suffix);
        assertEquals(patched, patchReplay);
        assertEquals(1L, fixture.count(
                "SELECT count(*) FROM hhy.admin_operation_logs"
                        + " WHERE action='adminMembershipPatchMembershipSkusById' AND resource_id=?",
                membershipSkuId));
        String encrypted = fixture.jdbc.queryForObject(
                "SELECT response_payload_ciphertext FROM hhy.idempotency_records"
                        + " WHERE scope LIKE 'r18adm:%' AND idem_key=?",
                String.class, "r18-patch-" + suffix);
        assertNotNull(encrypted);
        assertTrue(!encrypted.contains("Pro季卡新版"));
    }

    private static Fixture fixture() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).defaultSchema("public")
                .locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long adminId = jdbc.queryForObject("""
                INSERT INTO hhy.admin_users(username,password_hash,status)
                VALUES (?,'r18-test-password-hash','ACTIVE') RETURNING id
                """, Long.class, "r18-admin-" + suffix());
        Codec codec = new Codec();
        R18MembershipStore store = new R18MembershipPostgresStore(dataSource, codec, CLOCK);
        return new Fixture(jdbc, new R18MembershipService(store, codec), adminId);
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

    private record Fixture(JdbcTemplate jdbc, R18MembershipService service, long adminId) {
        long user(String phone, String inviteCode) {
            return jdbc.queryForObject("""
                    INSERT INTO hhy.users(phone,status,invite_code)
                    VALUES (?,'ACTIVE',?) RETURNING id
                    """, Long.class, phone, inviteCode);
        }

        long plan(String code) {
            return jdbc.queryForObject("""
                    INSERT INTO hhy.membership_plans(code,name,public_badge,status,version)
                    VALUES (?,'Pro会员','Pro','ACTIVE',0) RETURNING id
                    """, Long.class, code);
        }

        long sku(
                long planId,
                String code,
                String name,
                long price,
                int days,
                String termType,
                long seconds) {
            long productId = jdbc.queryForObject("""
                    INSERT INTO hhy.products(
                      product_code,type,name,status,display_order,version)
                    VALUES (?,'MEMBERSHIP',?,'ACTIVE',1,0) RETURNING id
                    """, Long.class, "PRODUCT-" + code, name);
            long skuId = jdbc.queryForObject("""
                    INSERT INTO hhy.product_skus(
                      product_id,code,name,price_cent,duration_days,benefits_json,status,version)
                    VALUES (?,?,?,?,?,CAST(? AS jsonb),'ACTIVE',0) RETURNING id
                    """, Long.class, productId, code, name, price, days,
                    "[{\"benefitCode\":\"PUBLISH_LIMIT\",\"name\":\"发布额度\","
                            + "\"value\":10,\"unit\":\"COUNT\"}]");
            long membershipSkuId = jdbc.queryForObject("""
                    INSERT INTO hhy.membership_skus(
                      plan_id,sku_id,term_type,term_seconds,version)
                    VALUES (?,?,?,?,0) RETURNING id
                    """, Long.class, planId, skuId, termType, seconds);
            List<Long> benefitIds = jdbc.queryForList(
                    "SELECT id FROM hhy.membership_benefits WHERE code='PUBLISH_LIMIT'",
                    Long.class);
            Long benefitId;
            if (benefitIds.isEmpty()) {
                benefitId = jdbc.queryForObject("""
                        INSERT INTO hhy.membership_benefits(code,value_type,description)
                        VALUES ('PUBLISH_LIMIT',1,'发布额度') RETURNING id
                        """, Long.class);
            } else {
                benefitId = benefitIds.getFirst();
            }
            jdbc.update("""
                    INSERT INTO hhy.membership_sku_benefits(
                      membership_sku_id,benefit_id,value_json,enabled)
                    VALUES (?,?,'10'::jsonb,true)
                    """, membershipSkuId, benefitId);
            return skuId;
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
                    adminId, 1, "r18-admin", "adminMembershipPatchMembershipSkusById",
                    "r18-admin-request-" + suffix(), "127.0.0.1");
        }
    }

    private static final class Codec implements R18MembershipStore.Codec {
        private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
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
        public Object value(String json) {
            return read(json, Object.class);
        }

        @Override
        public List<BenefitResource> benefits(String json) {
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
        public MembershipResource membership(byte[] json) {
            return read(json, MembershipResource.class);
        }

        private byte[] bytes(Object value) {
            try {
                return mapper.writeValueAsBytes(value);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        private <T> T read(String json, Class<T> type) {
            try {
                return mapper.readValue(json, type);
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
}
