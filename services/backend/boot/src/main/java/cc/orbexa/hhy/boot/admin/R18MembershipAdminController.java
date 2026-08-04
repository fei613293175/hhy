package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.commerce.R12MembershipContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import cc.orbexa.hhy.commerce.R18MembershipContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipBenefitsPutRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipGrantRequest;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipPage;
import cc.orbexa.hhy.commerce.R18MembershipContracts.MembershipSkuPatchRequest;
import cc.orbexa.hhy.commerce.R18MembershipPostgresStore;
import cc.orbexa.hhy.commerce.R18MembershipService;
import cc.orbexa.hhy.commerce.R18MembershipStore;
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
import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin-api/v1")
public class R18MembershipAdminController {
    private static final String ID = "^[A-Za-z0-9_-]{1,64}$";
    private final R18MembershipService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public R18MembershipAdminController(
            R18MembershipService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/membership/skus")
    @PreAuthorize("hasAuthority('membership.read')")
    public ApiResponse<MembershipPage> adminMembershipGetMembershipSkus(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "priceCent:asc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminSkus(page, pageSize, cursor, status, keyword, sort));
    }

    @PatchMapping("/membership/skus/{id}")
    @PreAuthorize("hasAuthority('membership.write')")
    public ApiResponse<MembershipResource> adminMembershipPatchMembershipSkusById(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody MembershipSkuPatchRequest body,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank @Size(min = 16, max = 128) String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.patchSku(
                actor(principal, "adminMembershipPatchMembershipSkusById", request),
                id, body, idempotencyKey));
    }

    @PutMapping("/membership/skus/{id}/benefits")
    @PreAuthorize("hasAuthority('membership.write')")
    public ApiResponse<MembershipResource> adminMembershipPutMembershipSkusByIdBenefits(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = ID) String id,
            @Valid @RequestBody MembershipBenefitsPutRequest body,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank @Size(min = 16, max = 128) String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.replaceBenefits(
                actor(principal, "adminMembershipPutMembershipSkusByIdBenefits", request),
                id, body, idempotencyKey));
    }

    @GetMapping("/user-memberships")
    @PreAuthorize("hasAuthority('membership.read')")
    public ApiResponse<MembershipPage> adminMembershipGetUserMemberships(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "updatedAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.userMemberships(
                page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/user-memberships/grants")
    @PreAuthorize("hasAuthority('membership.grant')")
    public ApiResponse<MembershipResource> adminMembershipPostUserMembershipsGrants(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody MembershipGrantRequest body,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank @Size(min = 16, max = 128) String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.grant(
                actor(principal, "adminMembershipPostUserMembershipsGrants", request),
                body, idempotencyKey));
    }

    private AdminActorContext actor(
            AdminPrincipal principal, String operationId, HttpServletRequest request) {
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
class R18MembershipConfiguration {
    @Bean
    R18MembershipStore.Codec r18MembershipCodec(
            ObjectMapper objectMapper, AdminIdempotencySnapshotCipher snapshots) {
        ObjectMapper canonical = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return new R18MembershipStore.Codec() {
            @Override
            public byte[] canonicalBytes(Map<String, Object> value) {
                return bytes(canonical, value);
            }

            @Override
            public String json(Object value) {
                return new String(bytes(canonical, value), StandardCharsets.UTF_8);
            }

            @Override
            public Object value(String json) {
                return read(canonical, json, Object.class);
            }

            @Override
            public List<BenefitResource> benefits(String json) {
                return read(canonical, json, new TypeReference<>() { });
            }

            @Override
            public String encrypt(
                    String scope, String key, String requestHash,
                    String responseType, byte[] plaintext) {
                return snapshots.encrypt(scope, key, requestHash, responseType, plaintext);
            }

            @Override
            public byte[] decrypt(
                    String scope, String key, String requestHash,
                    String responseType, String envelope) {
                return snapshots.decrypt(scope, key, requestHash, responseType, envelope);
            }

            @Override
            public MembershipResource membership(byte[] json) {
                return read(canonical, json, MembershipResource.class);
            }
        };
    }

    @Bean
    R18MembershipStore r18MembershipStore(
            DataSource dataSource, R18MembershipStore.Codec codec, Clock clock) {
        return new R18MembershipPostgresStore(dataSource, codec, clock);
    }

    @Bean
    R18MembershipService r18MembershipService(
            R18MembershipStore store, R18MembershipStore.Codec codec) {
        return new R18MembershipService(store, codec);
    }

    private static byte[] bytes(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception failure) {
            throw new IllegalStateException("R18 JSON encode failed", failure);
        }
    }

    private static <T> T read(ObjectMapper mapper, byte[] json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception failure) {
            throw new IllegalStateException("R18 JSON decode failed", failure);
        }
    }

    private static <T> T read(ObjectMapper mapper, String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception failure) {
            throw new IllegalStateException("R18 JSON decode failed", failure);
        }
    }

    private static <T> T read(ObjectMapper mapper, String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception failure) {
            throw new IllegalStateException("R18 JSON decode failed", failure);
        }
    }
}
