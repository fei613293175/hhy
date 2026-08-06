package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.UserAuthContracts.AuthScene;
import cc.orbexa.hhy.access.user.UserAuthVerificationService;
import cc.orbexa.hhy.access.user.UserIdempotencySnapshotCipher;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public final class R24WithdrawalController {
    private static final String ACCOUNT_SCOPE = "r24:payout-account:";
    private static final String QUOTE_SCOPE = "r24:withdrawal-quote:";
    private static final String CREATE_SCOPE = "r24:withdrawal-create:";
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final UserAuthVerificationService verification;
    private final UserIdempotencySnapshotCipher snapshots;
    private final R24PayoutAccountCipher accountCipher;

    public R24WithdrawalController(JdbcTemplate jdbc, Clock clock,
            UserAuthVerificationService verification, UserIdempotencySnapshotCipher snapshots,
            R24PayoutAccountCipher accountCipher) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.verification = verification;
        this.snapshots = snapshots;
        this.accountCipher = accountCipher;
    }

    @GetMapping("/me/reward-ledger")
    public ApiResponse<Map<String, Object>> rewardLedger(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword, HttpServletRequest request) {
        long userId = verifiedUser(principal);
        String state = status == null ? "" : status.trim();
        String term = keyword == null ? "" : keyword.trim();
        String like = "%" + term + "%";
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id::text AS "id", source_type AS "sourceType", amount AS "amountCent", status,
                       biz_id::text AS "bizId", balance_after AS "balanceAfterCent", created_at AS "createdAt"
                FROM hhy.reward_ledger WHERE user_id = ? AND (? = '' OR status = ?)
                  AND (? = '' OR COALESCE(source_type, '') ILIKE ? OR CAST(biz_id AS text) ILIKE ?)
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, userId, state, state, term, like, like, pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", pageMeta(page, pageSize, items.size())));
    }

    @GetMapping("/me/payout-account")
    public ApiResponse<Map<String, Object>> payoutAccount(@AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        long userId = verifiedUser(principal);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS "id", user_id::text AS "userId", real_name AS "accountName",
                       account_masked AS "payoutAccountMasked", status, version, updated_at AS "updatedAt"
                FROM hhy.payout_accounts WHERE user_id = ? AND status = 'ACTIVE'
                ORDER BY updated_at DESC, id DESC LIMIT 1
                """, userId);
        if (rows.isEmpty()) throw notFound("支付宝账户不存在");
        return success(request, rows.get(0));
    }

    @PutMapping("/me/payout-account")
    @Transactional
    public ApiResponse<Map<String, Object>> updatePayoutAccount(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            @Valid @RequestBody PayoutAccountRequest body, HttpServletRequest request) {
        long userId = verifiedUser(principal);
        if (!body.alipayAccount().matches("[0-9A-Za-z._@+-]{5,64}")) throw validation("支付宝账户信息格式不正确");
        String phone = jdbc.queryForObject("SELECT phone FROM hhy.users WHERE id = ?", String.class, userId);
        verification.verifySms(phone, AuthScene.SENSITIVE_OPERATION, body.smsCode());
        String hash = digest(body.accountName() + "\n" + body.alipayAccount() + "\n" + body.expectedVersion());
        IdempotencyClaim claim = claim(ACCOUNT_SCOPE + userId, key, hash, "payout-account");
        if (claim.replay()) return payoutAccount(principal, request);
        String accountHash = accountCipher.digest(body.alipayAccount());
        String encrypted = accountCipher.encrypt(userId, "alipayAccount", body.alipayAccount());
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, version FROM hhy.payout_accounts
                WHERE user_id = ? AND status = 'ACTIVE' FOR UPDATE
                """, userId);
        if (existing.isEmpty()) {
            jdbc.update("""
                    INSERT INTO hhy.payout_accounts(user_id, account_cipher, account_hash, account_masked, real_name, status, version)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
                    """, userId, encrypted, accountHash,
                    R24PayoutAccountCipher.mask(body.alipayAccount()), body.accountName());
        } else {
            long version = ((Number) existing.get(0).get("version")).longValue();
            if (body.expectedVersion() != null && body.expectedVersion() != version) throw conflict("支付宝账户版本已变化，请刷新后重试");
            int changed = jdbc.update("""
                    UPDATE hhy.payout_accounts SET account_cipher = ?, account_hash = ?, account_masked = ?,
                    real_name = ?, status = 'ACTIVE', version = version + 1, updated_at = now()
                    WHERE id = ? AND version = ?
                    """, encrypted, accountHash,
                    R24PayoutAccountCipher.mask(body.alipayAccount()), body.accountName(),
                    ((Number) existing.get(0).get("id")).longValue(), version);
            if (changed != 1) throw conflict("支付宝账户版本已变化，请刷新后重试");
        }
        complete(claim, "payout-account");
        return payoutAccount(principal, request);
    }

    @PostMapping("/withdrawals/quote")
    @Transactional
    public ApiResponse<Map<String, Object>> quote(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            @Valid @RequestBody WithdrawalQuoteRequest body, HttpServletRequest request) {
        long userId = verifiedUser(principal);
        long accountId = accountId(body.payoutAccountId());
        requireAccount(userId, accountId);
        long amount = body.amountCent();
        ensureWithdrawalEnabled();
        ensureAvailable(userId, amount);
        Profile profile = profile(userId);
        long min = config("withdrawal.min_amount_cent." + profile.sku(), 100L);
        long max = config("withdrawal.max_amount_cent." + profile.sku(), 500000L);
        if (amount < min || amount > max) throw validation("提现金额超出当前账户限额");
        long fee = Math.max(config("withdrawal.fee.fixed_cent." + profile.sku(), 0L),
                amount * config("withdrawal.fee.bps." + profile.sku(), 0L) / 10000L);
        if (fee >= amount) throw validation("提现金额不足以支付手续费");
        String hash = digest(amount + "\n" + accountId);
        IdempotencyClaim claim = claim(QUOTE_SCOPE + userId, key, hash, "withdrawal-quote");
        if (claim.replay()) return success(request, quoteResource(snapshot(claim, QUOTE_SCOPE + userId, key, hash, "withdrawal-quote")));
        String quoteId = "Q-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hhy.withdrawal_quotes(id, user_id, payout_account_id, amount, fee, net_amount, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, now() + interval '10 minutes')
                """, quoteId, userId, accountId, amount, fee, amount - fee);
        complete(claim, quoteId);
        return success(request, quoteResource(quoteId));
    }

    @PostMapping("/withdrawals")
    @Transactional
    public ApiResponse<Map<String, Object>> createWithdrawal(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            @Valid @RequestBody WithdrawalCreateRequest body, HttpServletRequest request) {
        long userId = verifiedUser(principal);
        long accountId = accountId(body.payoutAccountId());
        requireAccount(userId, accountId);
        ensureWithdrawalEnabled();
        String hash = digest(body.amountCent() + "\n" + body.quoteId() + "\n" + accountId);
        IdempotencyClaim claim = claim(CREATE_SCOPE + userId, key, hash, "withdrawal-create");
        if (claim.replay()) return success(request, withdrawal(userId, snapshot(claim, CREATE_SCOPE + userId, key, hash, "withdrawal-create")));
        Map<String, Object> quote = jdbc.queryForMap("SELECT id, amount, fee, net_amount, payout_account_id FROM hhy.withdrawal_quotes WHERE id = ? AND user_id = ? AND status = 'ACTIVE' AND expires_at > now() FOR UPDATE", body.quoteId(), userId);
        if (((Number) quote.get("amount")).longValue() != body.amountCent() || ((Number) quote.get("payout_account_id")).longValue() != accountId) throw conflict("提现报价与账户或金额不匹配");
        long amount = body.amountCent();
        enforcePeriodLimits(userId, amount, profile(userId));
        int moved = jdbc.update("""
                UPDATE hhy.reward_accounts SET available = available - ?, withdrawing = withdrawing + ?, version = version + 1, updated_at = now()
                WHERE user_id = ? AND available >= ?
                """, amount, amount, userId, amount);
        if (moved != 1) throw new BusinessException("WITHDRAWAL-422-BALANCE_INSUFFICIENT", "可提现余额不足", 422, false);
        String withdrawalNo = "WD-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hhy.withdrawal_requests(withdraw_no, user_id, amount, fee, net_amount, status, version, quote_id, payout_account_id)
                VALUES (?, ?, ?, ?, ?, 'RISK_REVIEWING', 0, ?, ?)
                """, withdrawalNo, userId, amount,
                ((Number) quote.get("fee")).longValue(), ((Number) quote.get("net_amount")).longValue(), body.quoteId(), accountId);
        jdbc.update("UPDATE hhy.withdrawal_quotes SET status = 'CONSUMED', consumed_at = now(), version = version + 1 WHERE id = ?", body.quoteId());
        jdbc.update("""
                INSERT INTO hhy.outbox_events(
                  aggregate_id, aggregate_type, event_id, event_type, payload)
                VALUES (?, 'WITHDRAWAL', ?, 'WithdrawalSubmitted',
                  jsonb_build_object('withdrawalNo', ?, 'userId', ?, 'amountCent', ?))
                """, withdrawalNo, UUID.randomUUID().toString(), withdrawalNo, userId, amount);
        complete(claim, withdrawalNo);
        return success(request, withdrawal(userId, withdrawalNo));
    }

    @GetMapping("/me/withdrawals")
    public ApiResponse<Map<String, Object>> withdrawals(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status, HttpServletRequest request) {
        long userId = verifiedUser(principal); String state = status == null ? "" : status.trim();
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id::text AS "id", withdraw_no AS "withdrawalNo", user_id::text AS "userId",
                withdrawal.status, withdrawal.amount AS "amountCent", withdrawal.fee AS "feeCent", withdrawal.net_amount AS "netAmountCent", withdrawal.payout_account_id::text AS "payoutAccountId",
                account.account_masked AS "payoutAccountMasked", failure_code AS "failureCode", completed_at AS "completedAt",
                withdrawal.version, withdrawal.created_at AS "createdAt", withdrawal.updated_at AS "updatedAt"
                FROM hhy.withdrawal_requests withdrawal
                LEFT JOIN hhy.payout_accounts account ON account.id = withdrawal.payout_account_id
                WHERE withdrawal.user_id = ? AND (? = '' OR withdrawal.status = ?)
                ORDER BY withdrawal.created_at DESC, withdrawal.id DESC LIMIT ? OFFSET ?
                """,
                userId, state, state, pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", pageMeta(page, pageSize, items.size())));
    }

    private Map<String, Object> withdrawal(long userId, String withdrawalNo) {
        return jdbc.queryForMap("""
                SELECT id::text AS "id", withdraw_no AS "withdrawalNo", user_id::text AS "userId", status,
                       withdrawal.amount AS "amountCent", withdrawal.fee AS "feeCent", withdrawal.net_amount AS "netAmountCent",
                       withdrawal.payout_account_id::text AS "payoutAccountId", account.account_masked AS "payoutAccountMasked",
                       withdrawal.failure_code AS "failureCode", withdrawal.completed_at AS "completedAt", withdrawal.version,
                       withdrawal.created_at AS "createdAt", withdrawal.updated_at AS "updatedAt"
                FROM hhy.withdrawal_requests withdrawal
                LEFT JOIN hhy.payout_accounts account ON account.id = withdrawal.payout_account_id
                WHERE withdrawal.user_id = ? AND withdrawal.withdraw_no = ?
                """, userId, withdrawalNo);
    }
    private long accountId(String value) { try { return Long.parseLong(value); } catch (Exception e) { throw validation("支付宝账户标识无效"); } }
    private void requireAccount(long userId, long accountId) { Integer count = jdbc.queryForObject("SELECT count(*) FROM hhy.payout_accounts WHERE id = ? AND user_id = ? AND status = 'ACTIVE'", Integer.class, accountId, userId); if (count == null || count != 1) throw notFound("支付宝账户不存在或不可用"); }
    private void ensureAvailable(long userId, long amount) { Long available = jdbc.queryForObject("SELECT available FROM hhy.reward_accounts WHERE user_id = ? FOR UPDATE", Long.class, userId); if (available == null || available < amount) throw new BusinessException("WITHDRAWAL-422-BALANCE_INSUFFICIENT", "可提现余额不足", 422, false); }
    private void ensureWithdrawalEnabled() { String value = configText("system.withdrawal.enabled", "true"); if (!Boolean.parseBoolean(value)) throw new BusinessException("COMMON-422-BUSINESS_RULE", "提现功能暂未开放", 422, false); }
    private void enforcePeriodLimits(long userId, long amount, Profile profile) {
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE created_at >= date_trunc('day', now())) AS daily_count,
                       COALESCE(sum(amount) FILTER (WHERE created_at >= date_trunc('day', now())), 0) AS daily_total,
                       COALESCE(sum(amount) FILTER (WHERE created_at >= date_trunc('month', now())), 0) AS monthly_total
                FROM hhy.withdrawal_requests
                WHERE user_id = ? AND status NOT IN ('CANCELLED', 'REJECTED', 'FAILED')
                """, userId);
        long dailyCount = ((Number) totals.get("daily_count")).longValue();
        long dailyTotal = ((Number) totals.get("daily_total")).longValue();
        long monthlyTotal = ((Number) totals.get("monthly_total")).longValue();
        if (dailyCount >= config("withdrawal.daily_count." + profile.sku(), 1L)
                || dailyTotal + amount > config("withdrawal.daily_total_cent." + profile.sku(), 1000000L)
                || monthlyTotal + amount > config("withdrawal.monthly_total_cent." + profile.sku(), 10000000L)) {
            throw new BusinessException("COMMON-422-BUSINESS_RULE", "提现次数或累计金额已达到当前周期上限", 422, false);
        }
    }
    private Profile profile(long userId) { List<String> rows = jdbc.queryForList("SELECT lower(plan.code) FROM hhy.user_memberships membership JOIN hhy.membership_plans plan ON plan.id = membership.plan_id WHERE membership.user_id = ? AND membership.status = 'ACTIVE' AND membership.ends_at > now() ORDER BY membership.ends_at DESC LIMIT 1", String.class, userId); String code = rows.isEmpty() || rows.get(0) == null ? "normal" : rows.get(0); if (!List.of("month", "quarter", "year").contains(code)) code = "normal"; return new Profile(code); }
    private long config(String key, long fallback) { try { return Long.parseLong(configText(key, Long.toString(fallback))); } catch (Exception e) { return fallback; } }
    private String configText(String key, String fallback) { List<String> values = jdbc.queryForList("SELECT value_json #>> '{}' FROM hhy.system_configs WHERE key = ? AND (scope = 'GLOBAL' OR scope IS NULL) ORDER BY scope DESC NULLS LAST LIMIT 1", String.class, key); return values.isEmpty() || values.get(0) == null ? fallback : values.get(0); }
    private Map<String,Object> quoteResource(String id) { Map<String,Object> q = jdbc.queryForMap("SELECT id, amount, fee, net_amount, expires_at, version FROM hhy.withdrawal_quotes WHERE id = ?", id); Map<String,Object> out = new HashMap<>(); out.put("id", q.get("id")); out.put("status", "QUOTED"); out.put("amountCent", q.get("amount")); out.put("feeCent", q.get("fee")); out.put("netAmountCent", q.get("net_amount")); out.put("expiresAt", q.get("expires_at")); out.put("version", q.get("version")); return out; }
    private String snapshot(IdempotencyClaim claim, String scope, String key, String hash, String type) { return new String(snapshots.decrypt(scope, key, hash, type, claim.responseRef()), StandardCharsets.UTF_8); }
    private IdempotencyClaim claim(String scope, String key, String hash, String type) { jdbc.update("INSERT INTO hhy.idempotency_records(scope, idem_key, request_hash, expires_at) VALUES (?, ?, ?, now() + interval '30 minutes') ON CONFLICT (scope, idem_key) DO NOTHING", scope, key, hash); Map<String,Object> row = jdbc.queryForMap("SELECT id, request_hash, response_ref FROM hhy.idempotency_records WHERE scope = ? AND idem_key = ?", scope, key); if (!hash.equals(row.get("request_hash"))) throw new BusinessException("WITHDRAWAL-409-DUPLICATE", "相同幂等键不能用于不同请求", 409, false); return new IdempotencyClaim(((Number)row.get("id")).longValue(), row.get("response_ref") != null, (String)row.get("response_ref"), scope, key, hash, type); }
    private void complete(IdempotencyClaim c, String value) { jdbc.update("UPDATE hhy.idempotency_records SET response_ref = ? WHERE id = ?", snapshots.encrypt(c.scope(), c.key(), c.hash(), c.type(), value.getBytes(StandardCharsets.UTF_8)), c.id()); }
    private long verifiedUser(UserPrincipal principal) { if (principal == null) throw new BusinessException("COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false); if (!principal.active()) throw new BusinessException("COMMON-403-FORBIDDEN", "当前账号状态不允许访问此能力", 403, false); List<String> identities = jdbc.queryForList("SELECT status FROM hhy.identity_profiles WHERE user_id = ? ORDER BY updated_at DESC, id DESC LIMIT 1", String.class, principal.userId()); if (identities.isEmpty() || !"VERIFIED".equals(identities.get(0))) throw new BusinessException("IDENTITY-422-NOT_VERIFIED", "请先完成实名认证", 422, false); return principal.userId(); }
    private <T> ApiResponse<T> success(HttpServletRequest request, T data) { Object id = request.getAttribute("requestId"); return ApiResponse.success(id == null ? "missing" : id.toString(), data, Instant.now(clock)); }
    private static Map<String,Object> pageMeta(int page,int size,int count) { return Map.of("page",page,"pageSize",size,"hasNext",count==size); }
    private static String digest(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static BusinessException validation(String message) { return new BusinessException("COMMON-400-VALIDATION", message, 400, false); }
    private static BusinessException conflict(String message) { return new BusinessException("COMMON-409-VERSION_CONFLICT", message, 409, false); }
    private static BusinessException notFound(String message) { return new BusinessException("COMMON-404-NOT_FOUND", message, 404, false); }
    private record Profile(String sku) { }
    private record IdempotencyClaim(long id, boolean replay, String responseRef, String scope, String key, String hash, String type) { }
    public record PayoutAccountRequest(@NotBlank @Size(max=2000) String accountName, @NotBlank String alipayAccount, @NotBlank @Size(min=4,max=10) String smsCode, Long expectedVersion) { }
    public record WithdrawalQuoteRequest(@Positive long amountCent, @NotBlank String payoutAccountId) { }
    public record WithdrawalCreateRequest(@Positive long amountCent, @NotBlank String quoteId, @NotBlank String payoutAccountId) { }
}
