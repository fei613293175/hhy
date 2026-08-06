package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.shared.api.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary used by the payout adapter and callback/query consumers. */
@Service
public final class R24PayoutSettlementService {
    private final JdbcTemplate jdbc;
    private final R24PayoutAccountCipher accountCipher;

    public R24PayoutSettlementService(JdbcTemplate jdbc, R24PayoutAccountCipher accountCipher) {
        this.jdbc = jdbc;
        this.accountCipher = accountCipher;
    }

    @Transactional(readOnly = true)
    public PayoutInstruction instruction(long withdrawalId) {
        Map<String, Object> row = payoutRow(withdrawalId, false);
        if (!"PAYOUT_PROCESSING".equals(row.get("withdrawal_status"))
                || !"PROCESSING".equals(row.get("payout_status"))) {
            throw businessRule("提现单当前状态不允许出款");
        }
        long userId = number(row, "user_id");
        return new PayoutInstruction(
                withdrawalId,
                String.valueOf(row.get("withdraw_no")),
                number(row, "payout_id"),
                String.valueOf(row.get("gateway")),
                number(row, "amount"),
                number(row, "fee"),
                number(row, "net_amount"),
                accountCipher.decrypt(userId, "alipayAccount", String.valueOf(row.get("account_cipher"))),
                String.valueOf(row.get("real_name")));
    }

    @Transactional
    public SettlementResource settle(long withdrawalId, PayoutResult result) {
        validate(result);
        Map<String, Object> row = payoutRow(withdrawalId, true);
        String currentPayoutStatus = String.valueOf(row.get("payout_status"));
        String target = result.success() ? "SUCCESS" : "FAILED";
        if (!"PROCESSING".equals(currentPayoutStatus)) {
            if (target.equals(currentPayoutStatus)
                    && result.providerOrderNo().equals(String.valueOf(row.get("provider_order_no")))) {
                return resource(row, target, result.failureCode());
            }
            throw conflict("出款结果已经终结且与当前回执不一致");
        }
        if (!"PAYOUT_PROCESSING".equals(row.get("withdrawal_status"))) {
            throw conflict("提现状态已经变化");
        }

        long userId = number(row, "user_id");
        long amount = number(row, "amount");
        if (result.success()) {
            int changed = jdbc.update("""
                    UPDATE hhy.reward_accounts
                    SET withdrawing = withdrawing - ?, withdrawn = withdrawn + ?,
                        version = version + 1, updated_at = now()
                    WHERE user_id = ? AND withdrawing >= ?
                    """, amount, amount, userId, amount);
            if (changed != 1) throw conflict("奖励账户提现中余额不足，拒绝重复结算");
            jdbc.update("""
                    UPDATE hhy.withdrawal_requests
                    SET status = 'SUCCESS', failure_code = NULL, completed_at = now(),
                        version = version + 1, updated_at = now()
                    WHERE id = ? AND status = 'PAYOUT_PROCESSING'
                    """, withdrawalId);
            jdbc.update("""
                    INSERT INTO hhy.reward_ledger(user_id, source_type, amount, status, biz_id, balance_after)
                    SELECT ?, 'WITHDRAWAL_SUCCESS', ?, 'POSTED', ?, available
                    FROM hhy.reward_accounts WHERE user_id = ?
                    ON CONFLICT (user_id, source_type, biz_id) DO NOTHING
                    """, userId, -amount, withdrawalId, userId);
            postAccounting(row, result.providerOrderNo());
        } else {
            int changed = jdbc.update("""
                    UPDATE hhy.reward_accounts
                    SET withdrawing = withdrawing - ?, available = available + ?,
                        version = version + 1, updated_at = now()
                    WHERE user_id = ? AND withdrawing >= ?
                    """, amount, amount, userId, amount);
            if (changed != 1) throw conflict("奖励账户提现中余额不足，拒绝重复回退");
            jdbc.update("""
                    UPDATE hhy.withdrawal_requests
                    SET status = 'FAILED', failure_code = ?, completed_at = now(),
                        version = version + 1, updated_at = now()
                    WHERE id = ? AND status = 'PAYOUT_PROCESSING'
                    """, result.failureCode(), withdrawalId);
        }
        jdbc.update("UPDATE hhy.payout_transactions SET provider_order_no = ?, status = ?, updated_at = now() WHERE id = ? AND status = 'PROCESSING'",
                result.providerOrderNo(), target, number(row, "payout_id"));
        jdbc.update("""
                INSERT INTO hhy.outbox_events(aggregate_id, aggregate_type, event_id, event_type, payload)
                VALUES (?, 'WITHDRAWAL', ?, ?,
                        jsonb_build_object('withdrawalId', ?::text, 'withdrawalNo', ?,
                                           'providerOrderNo', ?, 'failureCode', ?))
                """, row.get("withdraw_no"), UUID.randomUUID().toString(),
                result.success() ? "WithdrawalSucceeded" : "WithdrawalFailed",
                withdrawalId, row.get("withdraw_no"), result.providerOrderNo(), result.failureCode());
        return new SettlementResource(Long.toString(withdrawalId), target, result.providerOrderNo(), result.failureCode());
    }

    private void postAccounting(Map<String, Object> row, String providerOrderNo) {
        long withdrawalId = number(row, "withdrawal_id");
        long amount = number(row, "amount");
        long fee = number(row, "fee");
        long net = number(row, "net_amount");
        String idempotencyKey = "r24:withdrawal:payout:" + withdrawalId;
        jdbc.update("""
                INSERT INTO hhy.accounting_transactions(
                    transaction_no, biz_type, biz_id, description, idempotency_key,
                    occurred_at, status, currency, version)
                VALUES (?, 'WITHDRAWAL_PAYOUT', ?, '支付宝提现成功', ?, now(), 'POSTED', 'CNY', 0)
                ON CONFLICT (biz_type, idempotency_key) DO NOTHING
                """, "WD-ACC-" + withdrawalId, Long.toString(withdrawalId), idempotencyKey);
        Long transactionId = jdbc.queryForObject("""
                SELECT id FROM hhy.accounting_transactions
                WHERE biz_type = 'WITHDRAWAL_PAYOUT' AND idempotency_key = ?
                """, Long.class, idempotencyKey);
        if (transactionId == null) throw new IllegalStateException("Accounting transaction was not created");
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM hhy.accounting_entries WHERE transaction_id = ?", Integer.class, transactionId);
        if (existing != null && existing > 0) return;
        long liability = ledgerAccount("R24-REWARD-LIABILITY");
        long cash = ledgerAccount("R24-PAYOUT-CASH");
        long revenue = ledgerAccount("R24-WITHDRAWAL-FEE-REVENUE");
        jdbc.update("INSERT INTO hhy.accounting_entries(transaction_id, account_id, amount_cent, currency, direction, sequence) VALUES (?, ?, ?, 'CNY', 'DEBIT', 1)", transactionId, liability, amount);
        jdbc.update("INSERT INTO hhy.accounting_entries(transaction_id, account_id, amount_cent, currency, direction, sequence) VALUES (?, ?, ?, 'CNY', 'CREDIT', 2)", transactionId, cash, net);
        if (fee > 0) {
            jdbc.update("INSERT INTO hhy.accounting_entries(transaction_id, account_id, amount_cent, currency, direction, sequence) VALUES (?, ?, ?, 'CNY', 'CREDIT', 3)", transactionId, revenue, fee);
        }
        jdbc.query("SELECT hhy.assert_balanced_transaction(?)", resultSet -> { }, transactionId);
    }

    private long ledgerAccount(String accountNo) {
        Long id = jdbc.queryForObject("SELECT id FROM hhy.ledger_accounts WHERE account_no = ? AND status = 'ACTIVE'", Long.class, accountNo);
        if (id == null) throw new IllegalStateException("Missing R24 ledger account " + accountNo);
        return id;
    }

    private Map<String, Object> payoutRow(long withdrawalId, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT withdrawal.id AS withdrawal_id, withdrawal.withdraw_no, withdrawal.user_id,
                       withdrawal.amount, withdrawal.fee, withdrawal.net_amount,
                       withdrawal.status AS withdrawal_status, payout.id AS payout_id,
                       payout.gateway, payout.provider_order_no, payout.status AS payout_status,
                       account.account_cipher, account.real_name
                FROM hhy.withdrawal_requests withdrawal
                JOIN hhy.payout_transactions payout ON payout.withdrawal_id = withdrawal.id
                JOIN hhy.payout_accounts account ON account.id = withdrawal.payout_account_id
                WHERE withdrawal.id = ?
                """ + (lock ? " FOR UPDATE OF withdrawal, payout" : ""), withdrawalId);
        if (rows.isEmpty()) throw notFound("提现出款交易不存在");
        return rows.get(0);
    }

    private static SettlementResource resource(Map<String, Object> row, String status, String failureCode) {
        Object order = row.get("provider_order_no");
        return new SettlementResource(String.valueOf(row.get("withdrawal_id")), status,
                order == null ? null : order.toString(), failureCode);
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number number)) throw new IllegalStateException("Missing numeric payout field " + key);
        return number.longValue();
    }

    private static void validate(PayoutResult result) {
        if (result == null || result.providerOrderNo() == null
                || !result.providerOrderNo().matches("[A-Za-z0-9._:-]{1,128}")) {
            throw validation("出款平台订单号无效");
        }
        if (!result.success() && (result.failureCode() == null
                || !result.failureCode().matches("[A-Z0-9_-]{1,64}"))) {
            throw validation("出款失败代码无效");
        }
    }

    private static BusinessException validation(String message) {
        return new BusinessException("COMMON-400-VALIDATION", message, 400, false);
    }
    private static BusinessException conflict(String message) {
        return new BusinessException("COMMON-409-VERSION_CONFLICT", message, 409, false);
    }
    private static BusinessException notFound(String message) {
        return new BusinessException("COMMON-404-NOT_FOUND", message, 404, false);
    }
    private static BusinessException businessRule(String message) {
        return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false);
    }

    public record PayoutInstruction(long withdrawalId, String withdrawalNo, long payoutTransactionId,
            String gateway, long amountCent, long feeCent, long netAmountCent,
            String alipayAccount, String accountName) { }
    public record PayoutResult(boolean success, String providerOrderNo, String failureCode) { }
    public record SettlementResource(String withdrawalId, String status,
            String providerOrderNo, String failureCode) { }
}
