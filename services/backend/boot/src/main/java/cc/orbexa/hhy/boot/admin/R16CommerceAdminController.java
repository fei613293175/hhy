package cc.orbexa.hhy.boot.admin;

import cc.orbexa.hhy.access.admin.AdminIdempotencySnapshotCipher;
import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.commerce.R16CommerceContracts.AdminActorContext;
import cc.orbexa.hhy.commerce.R16CommerceContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderPage;
import cc.orbexa.hhy.commerce.R16CommerceContracts.OrderResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductPage;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductResource;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuCreateRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuPage;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuPatchRequest;
import cc.orbexa.hhy.commerce.R16CommerceContracts.ProductSkuResource;
import cc.orbexa.hhy.commerce.R16CommercePostgresStore;
import cc.orbexa.hhy.commerce.R16CommerceService;
import cc.orbexa.hhy.commerce.R16CommerceStore;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Frozen R16 administrator product, SKU, quotation snapshot, and order surface. */
@Validated
@RestController
@RequestMapping("/admin-api/v1")
public class R16CommerceAdminController {
    private static final String RESOURCE_ID = "^[0-9]{1,19}$";
    private static final String ORDER_NO = "^[A-Za-z0-9_-]{1,128}$";
    private final R16CommerceService service;
    private final Clock clock;
    private final AdminClientIpResolver clientIpResolver;

    public R16CommerceAdminController(
            R16CommerceService service, Clock clock, AdminClientIpResolver clientIpResolver) {
        this.service = service;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('product.read')")
    public ApiResponse<ProductPage> adminProductsGetProducts(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "updatedAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.products(page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('product.write')")
    public ApiResponse<ProductResource> adminProductsPostProducts(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody ProductCreateRequest body,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank @Size(min = 16, max = 128) String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.createProduct(
                actor(principal, "adminProductsPostProducts", request), body, idempotencyKey));
    }

    @PatchMapping("/products/{id}")
    @PreAuthorize("hasAuthority('product.write')")
    public ApiResponse<ProductResource> adminProductsPatchProductsById(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody ProductPatchRequest body,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank @Size(min = 16, max = 128) String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.patchProduct(
                actor(principal, "adminProductsPatchProductsById", request),
                id, body, idempotencyKey));
    }

    @GetMapping("/skus")
    @PreAuthorize("hasAuthority('product.read')")
    public ApiResponse<ProductSkuPage> adminProductsGetSkus(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "updatedAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.skus(page, pageSize, cursor, status, keyword, sort));
    }

    @PostMapping("/skus")
    @PreAuthorize("hasAuthority('product.write')")
    public ApiResponse<ProductSkuResource> adminProductsPostSkus(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody ProductSkuCreateRequest body,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank @Size(min = 16, max = 128) String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.createSku(
                actor(principal, "adminProductsPostSkus", request), body, idempotencyKey));
    }

    @PatchMapping("/skus/{id}")
    @PreAuthorize("hasAuthority('product.write')")
    public ApiResponse<ProductSkuResource> adminProductsPatchSkusById(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable @Pattern(regexp = RESOURCE_ID) String id,
            @Valid @RequestBody ProductSkuPatchRequest body,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank @Size(min = 16, max = 128) String idempotencyKey,
            HttpServletRequest request) {
        return success(request, service.patchSku(
                actor(principal, "adminProductsPatchSkusById", request),
                id, body, idempotencyKey));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('order.read')")
    public ApiResponse<OrderPage> adminOrdersGetOrders(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Size(max = 256) String cursor,
            @RequestParam(required = false) @Size(max = 64) String status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt:desc") @Size(max = 64) String sort,
            HttpServletRequest request) {
        return success(request, service.adminOrders(page, pageSize, cursor, status, keyword, sort));
    }

    @GetMapping("/orders/{orderNo}")
    @PreAuthorize("hasAuthority('order.read')")
    public ApiResponse<OrderResource> adminOrdersGetOrdersByOrderno(
            @PathVariable @Pattern(regexp = ORDER_NO) String orderNo,
            HttpServletRequest request) {
        return success(request, service.adminOrder(orderNo));
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
class R16CommerceConfiguration {
    @Bean
    R16CommerceStore.Codec r16CommerceCodec(
            ObjectMapper objectMapper, AdminIdempotencySnapshotCipher snapshots) {
        ObjectMapper canonical = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return new R16CommerceStore.Codec() {
            @Override
            public byte[] canonicalBytes(Map<String, Object> value) {
                return bytes(canonical, value);
            }

            @Override
            public String json(Object value) {
                return new String(bytes(canonical, value), StandardCharsets.UTF_8);
            }

            @Override
            public List<BenefitResource> benefits(String json) {
                return read(canonical, json, new TypeReference<>() { });
            }

            @Override
            public List<String> strings(String json) {
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
            public ProductResource product(byte[] json) {
                return read(canonical, json, ProductResource.class);
            }

            @Override
            public ProductSkuResource sku(byte[] json) {
                return read(canonical, json, ProductSkuResource.class);
            }
        };
    }

    @Bean
    R16CommerceStore r16CommerceStore(
            DataSource dataSource, R16CommerceStore.Codec codec, Clock clock) {
        return new R16CommercePostgresStore(dataSource, codec, clock);
    }

    @Bean
    R16CommerceService r16CommerceService(
            R16CommerceStore store, R16CommerceStore.Codec codec) {
        return new R16CommerceService(store, codec);
    }

    private static byte[] bytes(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception failure) {
            throw new IllegalStateException("R16 JSON encode failed", failure);
        }
    }

    private static <T> T read(ObjectMapper mapper, byte[] json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception failure) {
            throw new IllegalStateException("R16 JSON decode failed", failure);
        }
    }

    private static <T> T read(ObjectMapper mapper, String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception failure) {
            throw new IllegalStateException("R16 JSON decode failed", failure);
        }
    }
}
