package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1")
public final class R24FinanceAdminController {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public R24FinanceAdminController(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @GetMapping("/reward-accounts")
    @PreAuthorize("hasAuthority('reward.read')")
    public ApiResponse<Map<String, Object>> rewardAccounts(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            HttpServletRequest request) {
        String filter = keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim() + "%";
        List<Map<String, Object>> items = jdbc.queryForList("SELECT user_id AS "userId", pending AS "pendingCent", available AS "availableCent", frozen AS "frozenCent", withdrawn AS "withdrawnCent", version, updated_at AS "updatedAt" FROM hhy.reward_accounts WHERE (? = '' OR CAST(user_id AS text) ILIKE ?) ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?", filter, filter, pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasMore", items.size() == pageSize)));
    }

    @GetMapping("/reward-accounts/{userId}/ledger")
    @PreAuthorize("hasAuthority('reward.read')")
    public ApiResponse<Map<String, Object>> rewardLedger(@PathVariable String userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        List<Map<String, Object>> items = jdbc.queryForList("SELECT id, source_type AS "sourceType", amount AS "amountCent", status, biz_id AS "bizId", balance_after AS "balanceAfterCent", created_at AS "createdAt" FROM hhy.reward_ledger WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", Long.parseLong(userId), pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasMore", items.size() == pageSize)));
    }

    @GetMapping("/withdrawals")
    @PreAuthorize("hasAuthority('withdrawal.read')")
    public ApiResponse<Map<String, Object>> withdrawals(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String status,
            HttpServletRequest request) {
        List<Map<String, Object>> items = jdbc.queryForList("SELECT id, withdraw_no AS "withdrawalNo", user_id AS "userId", status, amount AS "amountCent", fee AS "feeCent", net_amount AS "netAmountCent", version, created_at AS "createdAt", updated_at AS "updatedAt" FROM hhy.withdrawal_requests WHERE (? = '' OR status = ?) ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", status == null ? "" : status, status == null ? "" : status, pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasMore", items.size() == pageSize)));
    }

    @GetMapping("/withdrawals/{id}")
    @PreAuthorize("hasAuthority('withdrawal.read')")
    public ApiResponse<Map<String, Object>> withdrawal(@PathVariable long id, HttpServletRequest request) {
        return success(request, jdbc.queryForMap("SELECT id, withdraw_no AS "withdrawalNo", user_id AS "userId", status, amount AS "amountCent", fee AS "feeCent", net_amount AS "netAmountCent", version, created_at AS "createdAt", updated_at AS "updatedAt" FROM hhy.withdrawal_requests WHERE id = ?", id));
    }

    @GetMapping("/accounting/transactions")
    @PreAuthorize("hasAuthority('accounting.read')")
    public ApiResponse<Map<String, Object>> accounting(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize, HttpServletRequest request) {
        List<Map<String, Object>> items = jdbc.queryForList("SELECT id, biz_type AS "bizType", biz_id AS "bizId", status, currency, created_at AS "createdAt" FROM hhy.accounting_transactions ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", pageSize, (page - 1) * pageSize);
        return success(request, Map.of("items", items, "page", Map.of("page", page, "pageSize", pageSize, "hasMore", items.size() == pageSize)));
    }

    private ApiResponse<Map<String, Object>> success(HttpServletRequest request, Map<String, Object> data) {
        Object id = request.getAttribute("requestId"); return ApiResponse.success(id == null ? "missing" : id.toString(), data, Instant.now(clock));
    }
}
