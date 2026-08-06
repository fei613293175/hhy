package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminCommand;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.AdminReviewRequest;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignPage;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.CampaignResource;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.ClaimPage;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.Codec;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.LedgerPage;
import cc.orbexa.hhy.incentive.R20RedPacketContracts.SessionPage;
import cc.orbexa.hhy.incentive.R20RedPacketPostgresStore;
import cc.orbexa.hhy.incentive.R20RedPacketService;
import cc.orbexa.hhy.incentive.R20RedPacketStore;
import cc.orbexa.hhy.shared.api.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class R20RedPacketAdminController {
    private static final String ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R20RedPacketService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public R20RedPacketAdminController(
            R20RedPacketService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/red-packet-campaigns")
    @PreAuthorize("hasAuthority('redpacket.read')")
    public ApiResponse<CampaignPage> adminRedPacketGetRedPacketCampaigns(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminCampaigns(page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/red-packet-campaigns/{id}")
    @PreAuthorize("hasAuthority('redpacket.read')")
    public ApiResponse<CampaignResource> adminRedPacketGetRedPacketCampaignsById(
            @PathVariable @Pattern(regexp = ID) String id,
            HttpServletRequest request) {
        return success(request, service.adminCampaign(id));
    }

    @GetMapping("/red-packet-campaigns/{id}/sessions")
    @PreAuthorize("hasAuthority('redpacket.read')")
    public ApiResponse<SessionPage> adminRedPacketGetRedPacketCampaignsByIdSessions(
            @PathVariable @Pattern(regexp = ID) String id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminSessions(id, page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/red-packet-campaigns/{id}/claims")
    @PreAuthorize("hasAuthority('redpacket.read')")
    public ApiResponse<ClaimPage> adminRedPacketGetRedPacketCampaignsByIdClaims(
            @PathVariable @Pattern(regexp = ID) String id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminClaims(id, page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/red-packet-campaigns/{id}/ledger")
    @PreAuthorize("hasAuthority('redpacket.finance')")
    public ApiResponse<LedgerPage> adminRedPacketGetRedPacketCampaignsByIdLedger(
            @PathVariable @Pattern(regexp = ID) String id, HttpServletRequest request) {
        return success(request, service.adminLedger(id));
    }

    @PostMapping("/red-packet-campaigns/{id}/review")
    @PreAuthorize("hasAuthority('redpacket.review')")
    public ApiResponse<CampaignResource> adminRedPacketPostRedPacketCampaignsByIdReview(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody AdminReviewRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key,
            HttpServletRequest request) {
        return success(request, service.review(actor(principal, key, request), id, body, key));
    }

    @PostMapping("/red-packet-campaigns/{id}/pause")
    @PreAuthorize("hasAuthority('redpacket.manage')")
    public ApiResponse<CampaignResource> adminRedPacketPostRedPacketCampaignsByIdPause(
            @AuthenticationPrincipal AdminPrincipal principal, @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody cc.orbexa.hhy.incentive.R20RedPacketContracts.LifecycleRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key, HttpServletRequest request) {
        return success(request, service.adminPause(actor(principal, key, request), id, body, key));
    }

    @PostMapping("/red-packet-campaigns/{id}/resume")
    @PreAuthorize("hasAuthority('redpacket.manage')")
    public ApiResponse<CampaignResource> adminRedPacketPostRedPacketCampaignsByIdResume(
            @AuthenticationPrincipal AdminPrincipal principal, @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody cc.orbexa.hhy.incentive.R20RedPacketContracts.LifecycleRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key, HttpServletRequest request) {
        return success(request, service.adminResume(actor(principal, key, request), id, body, key));
    }

    @PostMapping("/red-packet-campaigns/{id}/terminate")
    @PreAuthorize("hasAuthority('redpacket.terminate')")
    public ApiResponse<CampaignResource> adminRedPacketPostRedPacketCampaignsByIdTerminate(
            @AuthenticationPrincipal AdminPrincipal principal, @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody cc.orbexa.hhy.incentive.R20RedPacketContracts.LifecycleRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128) String key, HttpServletRequest request) {
        return success(request, service.adminTerminate(actor(principal, key, request), id, body, key));
    }

    private AdminCommand actor(AdminPrincipal principal, String key, HttpServletRequest request) {
        return new AdminCommand(
                principal.adminId(), principal.sessionId(), principal.username(),
                "adminRedPacketPostRedPacketCampaignsByIdReview", requestId(request),
                clientIpResolver.resolve(request), key);
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        return ApiResponse.success(requestId(request), data, Instant.now(clock));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "missing" : value.toString();
    }
}

@Configuration(proxyBeanMethods = false)
class R20RedPacketConfiguration {
    @Bean
    Codec r20RedPacketCodec(ObjectMapper objectMapper) {
        ObjectMapper canonical = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return new Codec() {
            @Override
            public byte[] canonicalBytes(Object value) {
                try { return canonical.writeValueAsBytes(value); }
                catch (Exception failure) { throw new IllegalStateException("R20 JSON encode failed", failure); }
            }

            @Override
            public String json(Object value) {
                return new String(canonicalBytes(value), StandardCharsets.UTF_8);
            }

            @Override
            public Map<String, Object> object(String json) {
                try { return canonical.readValue(json, new TypeReference<>() { }); }
                catch (Exception failure) { throw new IllegalStateException("R20 JSON decode failed", failure); }
            }
        };
    }

    @Bean
    R20RedPacketStore r20RedPacketStore(DataSource dataSource, Codec codec, Clock clock) {
        return new R20RedPacketPostgresStore(dataSource, codec, clock);
    }

    @Bean
    R20RedPacketService r20RedPacketService(R20RedPacketStore store, Codec codec) {
        return new R20RedPacketService(store, codec);
    }
}
