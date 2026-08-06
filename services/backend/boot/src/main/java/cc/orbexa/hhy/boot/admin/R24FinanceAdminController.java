package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1")
public final class R24FinanceAdminController {
    private static final String ADMIN_SCOPE = "admin:r24:";
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper json;

    public R24FinanceAdminController(JdbcTemplate jdbc, Clock clock, ObjectMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
    }

    @GetMapping("/reward-accounts")
    @PreAuthorize("hasAuthority('reward.read')")
    public ApiResponse<Map<String, Object>> rewardAccounts(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            HttpServletRequest request) {
        String filter = keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim() + "%";
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT user_id::text AS "userId", pending AS "pendingCent", available AS "availableCent",
                       frozen AS "frozenCent", withdrawing AS "withdrawingCent", withdrawn AS "withdrawnCent",
                       version, updated_at AS "updatedAt"
                FROM hhy.reward_accounts
                WHERE (? = '' OR CAST(user_id AS text) ILIKE ?)
                ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?
                """, filter, filter, pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasNext", items.size() == pageSize)));
    }

    @GetMapping("/reward-accounts/{userId}/ledger")
    @PreAuthorize("hasAuthority('reward.read')")
    public ApiResponse<Map<String, Object>> rewardLedger(@PathVariable String userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id::text AS "id", user_id::text AS "userId", source_type AS "sourceType",
                       amount AS "amountCent", status, biz_id::text AS "bizId",
                       balance_after AS "balanceAfterCent", created_at AS "createdAt"
                FROM hhy.reward_ledger WHERE user_id = ?
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, Long.parseLong(userId), pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasNext", items.size() == pageSize)));
    }

    @GetMapping("/withdrawals")
    @PreAuthorize("hasAuthority('withdrawal.read')")
    public ApiResponse<Map<String, Object>> withdrawals(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            HttpServletRequest request) {
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT withdrawal.id::text AS "id", withdrawal.withdraw_no AS "withdrawalNo",
                       withdrawal.user_id::text AS "userId", withdrawal.status,
                       withdrawal.amount AS "amountCent", withdrawal.fee AS "feeCent",
                       withdrawal.net_amount AS "netAmountCent", account.account_masked AS "payoutAccountMasked",
                       withdrawal.failure_code AS "failureCode", withdrawal.created_at AS "createdAt",
                       withdrawal.completed_at AS "completedAt", withdrawal.version
                FROM hhy.withdrawal_requests withdrawal
                LEFT JOIN hhy.payout_accounts account ON account.id = withdrawal.payout_account_id
                WHERE (? = '' OR withdrawal.status = ?)
                ORDER BY withdrawal.created_at DESC, withdrawal.id DESC LIMIT ? OFFSET ?
                """, status == null ? "" : status, status == null ? "" : status, pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasNext", items.size() == pageSize)));
    }

    @GetMapping("/withdrawals/{id}")
    @PreAuthorize("hasAuthority('withdrawal.read')")
    public ApiResponse<Map<String, Object>> withdrawal(@PathVariable long id, HttpServletRequest request) {
        return success(request, jdbc.queryForMap("""
                SELECT withdrawal.id::text AS "id", withdrawal.withdraw_no AS "withdrawalNo",
                       withdrawal.user_id::text AS "userId", withdrawal.status,
                       withdrawal.amount AS "amountCent", withdrawal.fee AS "feeCent",
                       withdrawal.net_amount AS "netAmountCent", account.account_masked AS "payoutAccountMasked",
                       withdrawal.failure_code AS "failureCode", withdrawal.created_at AS "createdAt",
                       withdrawal.completed_at AS "completedAt", withdrawal.version
                FROM hhy.withdrawal_requests withdrawal
                LEFT JOIN hhy.payout_accounts account ON account.id = withdrawal.payout_account_id
                WHERE withdrawal.id = ?
                """, id));
    }

    @GetMapping("/accounting/transactions")
    @PreAuthorize("hasAuthority('accounting.read')")
    public ApiResponse<Map<String, Object>> accounting(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize, HttpServletRequest request) {
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT transaction.id::text AS "id", transaction.transaction_no AS "transactionNo",
                       transaction.biz_type AS "businessType", transaction.biz_id AS "businessId",
                       transaction.idempotency_key AS "idempotencyKey", transaction.status,
                       transaction.currency,
                       COALESCE(sum(entry.amount_cent) FILTER (WHERE entry.direction = 'DEBIT'), 0) AS "debitTotalCent",
                       COALESCE(sum(entry.amount_cent) FILTER (WHERE entry.direction = 'CREDIT'), 0) AS "creditTotalCent",
                       transaction.reversal_of_id::text AS "reversalOfTransactionId",
                       transaction.created_at AS "createdAt", transaction.occurred_at AS "postedAt",
                       transaction.version
                FROM hhy.accounting_transactions transaction
                LEFT JOIN hhy.accounting_entries entry ON entry.transaction_id = transaction.id
                GROUP BY transaction.id
                ORDER BY transaction.created_at DESC, transaction.id DESC LIMIT ? OFFSET ?
                """, pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasNext", items.size() == pageSize)));
    }

    @GetMapping("/accounting/transactions/{id}")
    @PreAuthorize("hasAuthority('accounting.read')")
    public ApiResponse<Map<String, Object>> accountingDetail(@PathVariable long id, HttpServletRequest request) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT transaction.id::text AS "id", transaction.transaction_no AS "transactionNo",
                       transaction.biz_type AS "businessType", transaction.biz_id AS "businessId",
                       transaction.idempotency_key AS "idempotencyKey", transaction.status, transaction.currency,
                       COALESCE(sum(entry.amount_cent) FILTER (WHERE entry.direction = 'DEBIT'), 0) AS "debitTotalCent",
                       COALESCE(sum(entry.amount_cent) FILTER (WHERE entry.direction = 'CREDIT'), 0) AS "creditTotalCent",
                       transaction.reversal_of_id::text AS "reversalOfTransactionId",
                       transaction.created_at AS "createdAt", transaction.occurred_at AS "postedAt", transaction.version
                FROM hhy.accounting_transactions transaction
                LEFT JOIN hhy.accounting_entries entry ON entry.transaction_id = transaction.id
                WHERE transaction.id = ? GROUP BY transaction.id
                """, id);
        row = new HashMap<>(row);
        row.put("entries", jdbc.queryForList("""
                SELECT entry.id::text AS "id", entry.transaction_id::text AS "transactionId",
                       account.account_no AS "accountCode", account.account_type AS "accountName",
                       entry.direction, entry.amount_cent AS "amountCent", entry.currency,
                       transaction.biz_type AS "businessType", transaction.biz_id AS "businessId",
                       entry.created_at AS "createdAt"
                FROM hhy.accounting_entries entry
                JOIN hhy.ledger_accounts account ON account.id = entry.account_id
                JOIN hhy.accounting_transactions transaction ON transaction.id = entry.transaction_id
                WHERE entry.transaction_id = ? ORDER BY entry.sequence
                """, id));
        return success(request, row);
    }

    @GetMapping("/accounting/entries")
    @PreAuthorize("hasAuthority('accounting.read')")
    public ApiResponse<Map<String, Object>> accountingEntries(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String transactionId, @RequestParam(required = false) String direction,
            HttpServletRequest request) {
        String tx = transactionId == null ? "" : transactionId; String dir = direction == null ? "" : direction;
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT entry.id::text AS "id", entry.transaction_id::text AS "transactionId",
                       account.account_no AS "accountCode", account.account_type AS "accountName",
                       entry.direction, entry.amount_cent AS "amountCent", entry.currency,
                       transaction.biz_type AS "businessType", transaction.biz_id AS "businessId",
                       entry.created_at AS "createdAt"
                FROM hhy.accounting_entries entry
                JOIN hhy.ledger_accounts account ON account.id = entry.account_id
                JOIN hhy.accounting_transactions transaction ON transaction.id = entry.transaction_id
                WHERE (? = '' OR entry.transaction_id::text = ?) AND (? = '' OR entry.direction = ?)
                ORDER BY entry.created_at DESC, entry.id DESC LIMIT ? OFFSET ?
                """, tx, tx, dir, dir, pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasNext", items.size() == pageSize)));
    }

    @PostMapping("/reward-adjustments")
    @PreAuthorize("hasAuthority('reward.adjust.request')")
    @Transactional
    public ApiResponse<Map<String, Object>> rewardAdjustment(@AuthenticationPrincipal AdminPrincipal principal,
            @RequestHeader("X-Idempotency-Key") @NotBlank String key, @Valid @RequestBody RewardAdjustmentRequest body,
            HttpServletRequest request) {
        long userId = parseId(body.userId());
        String hash = digest(body.userId() + "\n" + body.direction() + "\n" + body.amountCent() + "\n" + body.reason() + "\n" + body.businessRef());
        IdempotencyClaim claim = claim(ADMIN_SCOPE + "reward-adjustment", key, hash);
        if (claim.replay()) return success(request, Map.of("status", "PENDING_APPROVAL", "approvalRequestId", claim.responseRef()));
        if (jdbc.queryForList("SELECT id FROM hhy.users WHERE id = ?", userId).isEmpty()) throw notFound("目标用户不存在");
        if (!List.of("CREDIT", "DEBIT").contains(body.direction())) throw validation("奖励调整方向无效");
        long approvalId = jdbc.queryForObject("INSERT INTO hhy.admin_approval_requests(type, biz_id, requester, status) VALUES ('REWARD_ADJUSTMENT', ?, ?, 'PENDING') RETURNING id", Long.class, userId, principal.username());
        jdbc.update("INSERT INTO hhy.reward_adjustment_requests(user_id, direction, amount, reason, business_ref, approval_request_id, requested_by) VALUES (?, ?, ?, ?, ?, ?, ?)", userId, body.direction(), body.amountCent(), body.reason(), body.businessRef(), approvalId, principal.adminId());
        jdbc.update("INSERT INTO hhy.admin_operation_logs(admin_id, action, resource, resource_id, before_json, after_json) VALUES (?, 'CREATE', 'reward_adjustment', ?, '{}'::jsonb, jsonb_build_object('approvalRequestId', ?))", principal.adminId(), approvalId, approvalId);
        complete(claim, Long.toString(approvalId));
        return success(request, Map.of("status", "PENDING_APPROVAL", "approvalRequestId", approvalId));
    }

    @PostMapping("/withdrawals/{id}/risk-review")
    @PreAuthorize("hasAuthority('withdrawal.risk')")
    @Transactional
    public ApiResponse<Map<String, Object>> riskReview(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable long id,
            @RequestHeader("X-Idempotency-Key") @NotBlank String key, @Valid @RequestBody ReviewRequest body, HttpServletRequest request) {
        return review(principal, id, "RISK", key, body, request);
    }

    @PostMapping("/withdrawals/{id}/finance-review")
    @PreAuthorize("hasAuthority('withdrawal.finance')")
    @Transactional
    public ApiResponse<Map<String, Object>> financeReview(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable long id,
            @RequestHeader("X-Idempotency-Key") @NotBlank String key, @Valid @RequestBody ReviewRequest body, HttpServletRequest request) {
        return review(principal, id, "FINANCE", key, body, request);
    }

    @PostMapping("/withdrawals/{id}/payout")
    @PreAuthorize("hasAuthority('withdrawal.payout')")
    @Transactional
    public ApiResponse<Map<String, Object>> payout(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable long id,
            @RequestHeader("X-Idempotency-Key") @NotBlank String key, @Valid @RequestBody PayoutRequest body, HttpServletRequest request) {
        if (!List.of("ALIPAY", "ALIPAY_ENTERPRISE").contains(body.payoutChannel())) {
            throw validation("出款通道无效");
        }
        String hash = digest(id + "\n" + body.payoutChannel() + "\n" + body.expectedVersion());
        IdempotencyClaim claim = claim(ADMIN_SCOPE + "payout:" + id, key, hash);
        if (claim.replay()) return success(request, withdrawal(id));
        int changed = jdbc.update("UPDATE hhy.withdrawal_requests SET status = 'PAYOUT_PROCESSING', version = version + 1, updated_at = now() WHERE id = ? AND status = 'APPROVED' AND version = ?", id, body.expectedVersion());
        if (changed != 1) throw conflict("提现状态或版本已变化");
        Long payoutId = jdbc.queryForObject("INSERT INTO hhy.payout_transactions(withdrawal_id, gateway, status) VALUES (?, ?, 'PROCESSING') RETURNING id", Long.class, id, body.payoutChannel());
        jdbc.update("""
                INSERT INTO hhy.outbox_events(aggregate_id, aggregate_type, event_id, event_type, payload)
                SELECT withdraw_no, 'WITHDRAWAL', ?, 'PayoutRequested',
                       jsonb_build_object('withdrawalId', id::text, 'withdrawalNo', withdraw_no,
                                          'payoutTransactionId', ?::text, 'gateway', ?,
                                          'payoutAccountId', payout_account_id::text,
                                          'amountCent', amount, 'feeCent', fee, 'netAmountCent', net_amount)
                FROM hhy.withdrawal_requests WHERE id = ?
                """, UUID.randomUUID().toString(), payoutId, body.payoutChannel(), id);
        jdbc.update("INSERT INTO hhy.admin_operation_logs(admin_id, action, resource, resource_id, before_json, after_json) VALUES (?, 'START_PAYOUT', 'withdrawal', ?, jsonb_build_object('status','APPROVED'), jsonb_build_object('status','PAYOUT_PROCESSING'))", principal.adminId(), id);
        complete(claim, Long.toString(id));
        return success(request, withdrawal(id));
    }

    @PostMapping("/withdrawals/{id}/query")
    @PreAuthorize("hasAuthority('withdrawal.payout')")
    @Transactional
    public ApiResponse<Map<String, Object>> queryPayout(@AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable long id, @RequestHeader("X-Idempotency-Key") @NotBlank String key,
            @RequestBody(required = false) QueryRequest body, HttpServletRequest request) {
        QueryRequest command = body == null ? new QueryRequest(null, null, Map.of()) : body;
        String hash = digest(id + "\n" + command.reason() + "\n" + command.expectedVersion() + "\n" + json(command.payload()));
        IdempotencyClaim claim = claim(ADMIN_SCOPE + "payout-query:" + id, key, hash);
        if (claim.replay()) return success(request, withdrawal(id));
        Map<String, Object> current = jdbc.queryForMap("SELECT status, version, withdraw_no FROM hhy.withdrawal_requests WHERE id = ? FOR UPDATE", id);
        if (!"PAYOUT_PROCESSING".equals(current.get("status"))) throw businessRule("仅出款中的提现单允许主动查单");
        long version = ((Number) current.get("version")).longValue();
        if (command.expectedVersion() != null && command.expectedVersion() != version) throw conflict("提现版本已变化");
        jdbc.update("""
                INSERT INTO hhy.outbox_events(aggregate_id, aggregate_type, event_id, event_type, payload)
                VALUES (?, 'WITHDRAWAL', ?, 'PayoutQueryRequested',
                        jsonb_build_object('withdrawalId', ?::text, 'reason', ?, 'payload', ?::jsonb))
                """, current.get("withdraw_no"), UUID.randomUUID().toString(), id,
                command.reason(), json(command.payload()));
        jdbc.update("INSERT INTO hhy.admin_operation_logs(admin_id, action, resource, resource_id, before_json, after_json) VALUES (?, 'QUERY_PAYOUT', 'withdrawal', ?, jsonb_build_object('status','PAYOUT_PROCESSING','version',?), jsonb_build_object('queryRequested',true))", principal.adminId(), id, version);
        complete(claim, Long.toString(id));
        return success(request, withdrawal(id));
    }

    @PostMapping("/accounting/transactions/{id}/reversals")
    @PreAuthorize("hasAuthority('accounting.reversal.request')")
    @Transactional
    public ApiResponse<Map<String, Object>> reverseAccounting(@AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable long id, @RequestHeader("X-Idempotency-Key") @NotBlank String key,
            @Valid @RequestBody AccountingReversalRequest body, HttpServletRequest request) {
        long approvalId = parseId(body.approvalId());
        String hash = digest(id + "\n" + body.reasonCode() + "\n" + body.reason() + "\n"
                + body.approvalId() + "\n" + body.expectedVersion());
        IdempotencyClaim claim = claim(ADMIN_SCOPE + "accounting-reversal:" + id, key, hash);
        if (claim.replay()) return accountingDetail(Long.parseLong(claim.responseRef()), request);

        List<Map<String, Object>> approvals = jdbc.queryForList("SELECT requester, reviewer, status FROM hhy.admin_approval_requests WHERE id = ?", approvalId);
        if (approvals.isEmpty()) throw notFound("审批单不存在");
        Map<String, Object> approval = approvals.get(0);
        String requester = approval.get("requester") == null ? "" : approval.get("requester").toString();
        String reviewer = approval.get("reviewer") == null ? "" : approval.get("reviewer").toString();
        boolean currentRequester = requester.equals(principal.username())
                || requester.equals(Long.toString(principal.adminId()));
        if (!"APPROVED".equals(approval.get("status")) || !currentRequester || reviewer.isBlank()
                || requester.equals(reviewer)) {
            throw businessRule("会计冲正必须绑定已批准且申请人与审批人分离的审批单");
        }
        List<Map<String, Object>> sources = jdbc.queryForList("SELECT biz_id, biz_type, description, currency, version, status, reversal_of_id FROM hhy.accounting_transactions WHERE id = ? FOR UPDATE", id);
        if (sources.isEmpty()) throw notFound("会计交易不存在");
        Map<String, Object> source = sources.get(0);
        if (!"POSTED".equals(source.get("status")) || source.get("reversal_of_id") != null) {
            throw businessRule("仅允许冲正未被冲正的已过账会计交易");
        }
        long version = ((Number) source.get("version")).longValue();
        if (version != body.expectedVersion()) throw conflict("会计交易版本已变化，请刷新后重试");
        if (!jdbc.queryForList("SELECT id FROM hhy.accounting_transactions WHERE reversal_of_id = ?", id).isEmpty()) {
            throw businessRule("该会计交易已经完成冲正");
        }

        String reversalNo = "REV-" + UUID.randomUUID();
        String reversalBizId = String.valueOf(source.get("biz_id")) + "\nREVERSAL:" + id;
        String reversalKey = "r24:accounting-reversal:" + id + ":" + approvalId;
        Long reversalId = jdbc.queryForObject("INSERT INTO hhy.accounting_transactions(transaction_no, biz_type, biz_id, description, idempotency_key, occurred_at, reversal_of_id, status, currency, version) VALUES (?, 'ACCOUNTING_REVERSAL', ?, ?, ?, now(), ?, 'POSTED', ?, 0) RETURNING id", Long.class, reversalNo, reversalBizId, body.reason(), reversalKey, id, source.get("currency"));
        jdbc.update("INSERT INTO hhy.accounting_entries(transaction_id, account_id, amount_cent, currency, direction, sequence) SELECT ?, account_id, amount_cent, currency, CASE direction WHEN 'DEBIT' THEN 'CREDIT' ELSE 'DEBIT' END, sequence FROM hhy.accounting_entries WHERE transaction_id = ? ORDER BY sequence", reversalId, id);
        jdbc.query("SELECT hhy.assert_balanced_transaction(?)", resultSet -> { }, reversalId);
        jdbc.update("INSERT INTO hhy.accounting_reversal_requests(source_transaction_id, approval_request_id, reason_code, reason, evidence_json, status, reversal_transaction_id, requested_by, version) VALUES (?, ?, ?, ?, ?::jsonb, 'EXECUTED', ?, ?, 0)", id, approvalId, body.reasonCode(), body.reason(), json(body.evidenceIds()), reversalId, principal.adminId());
        jdbc.update("INSERT INTO hhy.admin_operation_logs(admin_id, action, resource, resource_id, before_json, after_json) VALUES (?, 'ACCOUNTING_REVERSAL', 'accounting_transaction', ?, jsonb_build_object('status','POSTED','version',?), jsonb_build_object('status','POSTED','reversalTransactionId',?,'approvalId',?))", principal.adminId(), id, version, reversalId, approvalId);
        complete(claim, Long.toString(reversalId));
        return accountingDetail(reversalId, request);
    }

    private ApiResponse<Map<String, Object>> review(AdminPrincipal principal, long id, String stage, String key, ReviewRequest body, HttpServletRequest request) {
        String hash = digest(id + "\n" + stage + "\n" + body.decision() + "\n" + body.reason() + "\n" + body.expectedVersion());
        IdempotencyClaim claim = claim(ADMIN_SCOPE + stage.toLowerCase() + ":" + id, key, hash);
        if (claim.replay()) return success(request, withdrawal(id));
        Map<String,Object> current = jdbc.queryForMap("SELECT status, version FROM hhy.withdrawal_requests WHERE id = ? FOR UPDATE", id);
        String from = String.valueOf(current.get("status")); long version = ((Number)current.get("version")).longValue();
        if (version != body.expectedVersion()) throw conflict("提现版本已变化");
        boolean risk = "RISK".equals(stage);
        boolean approve = "APPROVE".equals(body.decision());
        boolean reject = "REJECT".equals(body.decision());
        boolean escalate = "ESCALATE".equals(body.decision());
        boolean validFrom = (risk && "RISK_REVIEWING".equals(from)) || (!risk && "FINANCE_REVIEWING".equals(from));
        if (!validFrom || (!approve && !reject && !(risk && escalate))) throw businessRule("提现当前状态不允许此审核动作");
        String to = reject ? "REJECTED" : (risk ? "FINANCE_REVIEWING" : "APPROVED");
        jdbc.update("UPDATE hhy.withdrawal_requests SET status = ?, version = version + 1, updated_at = now() WHERE id = ? AND version = ?", to, id, body.expectedVersion());
        if (reject) {
            jdbc.update("""
                    UPDATE hhy.reward_accounts SET withdrawing = withdrawing - ?, available = available + ?, version = version + 1, updated_at = now()
                    WHERE user_id = (SELECT user_id FROM hhy.withdrawal_requests WHERE id = ?) AND withdrawing >= ?
                    """, amount(id), amount(id), id, amount(id));
            jdbc.update("UPDATE hhy.withdrawal_requests SET failure_code = 'REVIEW_REJECTED', completed_at = now() WHERE id = ?", id);
        }
        jdbc.update("INSERT INTO hhy.withdrawal_review_events(withdrawal_id, stage, decision, reason, evidence_json, from_status, to_status, actor_admin_id, request_id) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)", id, stage, body.decision(), body.reason(), json(body.evidenceIds()), from, to, principal.adminId(), requestId(request));
        jdbc.update("INSERT INTO hhy.admin_operation_logs(admin_id, action, resource, resource_id, before_json, after_json) VALUES (?, ?, 'withdrawal', ?, jsonb_build_object('status', ?), jsonb_build_object('status', ?))", principal.adminId(), stage + "_REVIEW", id, from, to);
        complete(claim, Long.toString(id));
        return success(request, withdrawal(id));
    }

    private Map<String,Object> withdrawal(long id) { return jdbc.queryForMap("""
            SELECT withdrawal.id::text AS "id", withdrawal.withdraw_no AS "withdrawalNo",
                   withdrawal.user_id::text AS "userId", withdrawal.status,
                   withdrawal.amount AS "amountCent", withdrawal.fee AS "feeCent",
                   withdrawal.net_amount AS "netAmountCent", account.account_masked AS "payoutAccountMasked",
                   withdrawal.failure_code AS "failureCode", withdrawal.created_at AS "createdAt",
                   withdrawal.completed_at AS "completedAt", withdrawal.version
            FROM hhy.withdrawal_requests withdrawal
            LEFT JOIN hhy.payout_accounts account ON account.id = withdrawal.payout_account_id
            WHERE withdrawal.id = ?
            """, id); }
    private static long parseId(String value) { try { return Long.parseLong(value); } catch (Exception e) { throw validation("资源标识无效"); } }
    private long amount(long withdrawalId) {
        Long amount = jdbc.queryForObject("SELECT amount FROM hhy.withdrawal_requests WHERE id = ?", Long.class, withdrawalId);
        if (amount == null) throw notFound("提现申请不存在");
        return amount;
    }
    private IdempotencyClaim claim(String scope, String key, String hash) {
        jdbc.update("INSERT INTO hhy.idempotency_records(scope, idem_key, request_hash, expires_at) VALUES (?, ?, ?, now() + interval '30 minutes') ON CONFLICT (scope, idem_key) DO NOTHING", scope, key, hash);
        Map<String,Object> row = jdbc.queryForMap("SELECT id, request_hash, response_ref FROM hhy.idempotency_records WHERE scope = ? AND idem_key = ?", scope, key);
        if (!hash.equals(row.get("request_hash"))) throw new BusinessException("COMMON-409-DUPLICATE", "相同幂等键不能用于不同请求", 409, false);
        return new IdempotencyClaim(((Number) row.get("id")).longValue(), row.get("response_ref") != null, (String) row.get("response_ref"));
    }
    private void complete(IdempotencyClaim claim, String responseRef) {
        jdbc.update("UPDATE hhy.idempotency_records SET response_ref = ? WHERE id = ? AND response_ref IS NULL", responseRef, claim.id());
    }
    private static String digest(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String json(Object value) {
        try { return json.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException e) { throw validation("证据标识格式无效"); }
    }
    private static BusinessException validation(String message) { return new BusinessException("COMMON-400-VALIDATION", message, 400, false); }
    private static BusinessException conflict(String message) { return new BusinessException("COMMON-409-VERSION_CONFLICT", message, 409, false); }
    private static BusinessException notFound(String message) { return new BusinessException("COMMON-404-NOT_FOUND", message, 404, false); }
    private static BusinessException businessRule(String message) { return new BusinessException("COMMON-422-BUSINESS_RULE", message, 422, false); }
    private static String requestId(HttpServletRequest request) { Object id = request.getAttribute("requestId"); return id == null ? "missing" : id.toString(); }

    public record RewardAdjustmentRequest(@NotBlank String userId, @NotBlank String direction, @Min(1) long amountCent, @NotBlank @Size(max=2000) String reason, @Size(max=255) String businessRef) { }
    public record ReviewRequest(@NotBlank String decision, @NotBlank @Size(max=2000) String reason, @NotNull long expectedVersion, List<String> evidenceIds) { }
    public record PayoutRequest(@NotBlank String payoutChannel, @NotNull long expectedVersion) { }
    public record QueryRequest(@Size(max = 2000) String reason, Long expectedVersion, Map<String, Object> payload) {
        public QueryRequest {
            payload = payload == null ? Map.of() : new HashMap<>(payload);
        }
    }
    public record AccountingReversalRequest(@NotBlank @Size(max = 64) String reasonCode,
            @NotBlank @Size(max = 1000) String reason, @Size(max = 20) List<String> evidenceIds,
            @NotBlank @Size(max = 64) String approvalId, @NotNull long expectedVersion) { }
    private record IdempotencyClaim(long id, boolean replay, String responseRef) { }

    private ApiResponse<Map<String, Object>> success(HttpServletRequest request, Map<String, Object> data) {
        Object id = request.getAttribute("requestId"); return ApiResponse.success(id == null ? "missing" : id.toString(), data, Instant.now(clock));
    }
}
