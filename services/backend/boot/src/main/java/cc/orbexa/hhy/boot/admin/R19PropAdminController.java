package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.commerce.R19PropContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R19PropContracts.CommandResultResource;
import cc.orbexa.hhy.commerce.R19PropContracts.HeadlineSlotRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPage;
import cc.orbexa.hhy.commerce.R19PropContracts.PropPatchRequest;
import cc.orbexa.hhy.commerce.R19PropContracts.PropResource;
import cc.orbexa.hhy.commerce.R19PropService;
import cc.orbexa.hhy.commerce.R19PropStore;
import cc.orbexa.hhy.commerce.R19PropPostgresStore;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
public class R19PropAdminController {
    private static final String ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R19PropService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public R19PropAdminController(
            R19PropService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/props")
    @PreAuthorize("hasAuthority('prop.read')")
    public ApiResponse<PropPage> adminPropsGetProps(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "name:asc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminProps(page, pageSize, cursor, status, keyword, sort));
    }

    @PatchMapping("/props/{id}")
    @PreAuthorize("hasAuthority('prop.write')")
    public ApiResponse<PropResource> adminPropsPatchPropsById(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody PropPatchRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128)
            String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.patch(
                actor(principal, "adminPropsPatchPropsById", id, request), id, body, idempotencyKey));
    }

    @GetMapping("/headline-slots")
    @PreAuthorize("hasAuthority('prop.slot.read')")
    public ApiResponse<PropPage> adminPropsGetHeadlineSlots(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.headlineSlots(page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/headline-slots")
    @PreAuthorize("hasAuthority('prop.slot.write')")
    public ApiResponse<PropResource> adminPropsPostHeadlineSlots(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody HeadlineSlotRequest body,
            @RequestHeader("X-Idempotency-Key") @NotBlank @Size(min = 16, max = 128)
            String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.createHeadlineSlot(
                actor(principal, "adminPropsPostHeadlineSlots", "new-slot", request), body, idempotencyKey));
    }

    @GetMapping("/prop-executions")
    @PreAuthorize("hasAuthority('prop.read')")
    public ApiResponse<PropPage> adminPropsGetPropExecutions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.executions(page, pageSize, cursor, status, keyword, sort));
    }

    private AdminActorContext actor(
            AdminPrincipal principal, String operationId, String resourceKey, HttpServletRequest request) {
        return new AdminActorContext(
                principal.adminId(), principal.sessionId(), principal.username(), operationId,
                requestId(request), clientIpResolver.resolve(request));
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
class R19PropConfiguration {
    @Bean
    R19PropStore.Codec r19PropCodec(ObjectMapper objectMapper) {
        ObjectMapper canonical = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return new R19PropStore.Codec() {
            @Override
            public byte[] canonicalBytes(Object value) {
                try { return canonical.writeValueAsBytes(value); }
                catch (Exception failure) { throw new IllegalStateException("R19 JSON encode failed", failure); }
            }

            @Override
            public String json(Object value) {
                return new String(canonicalBytes(value), StandardCharsets.UTF_8);
            }

            @Override
            public Map<String, Object> object(String json) {
                try { return canonical.readValue(json, new TypeReference<>() { }); }
                catch (Exception failure) { throw new IllegalStateException("R19 JSON decode failed", failure); }
            }
        };
    }

    @Bean
    R19PropStore r19PropStore(DataSource dataSource, R19PropStore.Codec codec, Clock clock) {
        return new R19PropPostgresStore(dataSource, codec, clock);
    }

    @Bean
    R19PropService r19PropService(R19PropStore store, R19PropStore.Codec codec) {
        return new R19PropService(store, codec);
    }
}
