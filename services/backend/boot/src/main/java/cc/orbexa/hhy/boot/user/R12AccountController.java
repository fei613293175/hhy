package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.user.R12ProfileContracts.ProfilePatchRequest;
import cc.orbexa.hhy.access.user.R12ProfileService;
import cc.orbexa.hhy.access.user.UserAuthContracts.UserResource;
import cc.orbexa.hhy.access.user.UserPrincipal;
import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import cc.orbexa.hhy.commerce.R12MembershipService;
import cc.orbexa.hhy.commerce.R12MembershipStore;
import cc.orbexa.hhy.incentive.R12RewardContracts.RewardAccountResource;
import cc.orbexa.hhy.incentive.R12RewardService;
import cc.orbexa.hhy.incentive.R12RewardStore;
import cc.orbexa.hhy.shared.api.ApiResponse;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class R12AccountController {
    private final R12ProfileService profiles;
    private final R12MembershipService memberships;
    private final R12RewardService rewards;
    private final Clock clock;

    public R12AccountController(
            R12ProfileService profiles,
            R12MembershipService memberships,
            R12RewardService rewards,
            Clock clock) {
        this.profiles = profiles;
        this.memberships = memberships;
        this.rewards = rewards;
        this.clock = clock;
    }

    @PatchMapping("/api/v1/me/profile")
    public ApiResponse<UserResource> userPatchMeProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfilePatchRequest body,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank @Size(min = 16, max = 128) String idempotencyKey,
            HttpServletRequest request) {
        return success(request, profiles.patch(principal, body, idempotencyKey));
    }

    @GetMapping("/api/v1/me/membership")
    public ApiResponse<MembershipResource> membershipGetMeMembership(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        return success(request, memberships.current(activeUserId(principal)));
    }

    @GetMapping("/api/v1/me/reward-account")
    public ApiResponse<RewardAccountResource> rewardGetMeRewardAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        return success(request, rewards.current(activeUserId(principal)));
    }

    private <T> ApiResponse<T> success(HttpServletRequest request, T data) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(
                requestId == null ? "missing" : requestId.toString(), data, Instant.now(clock));
    }

    private static long activeUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(
                    "COMMON-401-UNAUTHENTICATED", "登录状态已失效", 401, false);
        }
        if (!principal.active()) {
            throw new BusinessException(
                    "COMMON-403-FORBIDDEN", "当前账号状态不允许访问此能力", 403, false);
        }
        return principal.userId();
    }
}

@Configuration(proxyBeanMethods = false)
class R12AccountConfiguration {
    @Bean
    R12MembershipStore r12MembershipStore(DataSource dataSource, ObjectMapper objectMapper) {
        return new R12MembershipStore(dataSource, json -> {
            try {
                return objectMapper.readValue(json, Object.class);
            } catch (Exception failure) {
                throw new IllegalStateException("Membership benefit snapshot is invalid JSON", failure);
            }
        });
    }

    @Bean
    R12MembershipService r12MembershipService(R12MembershipStore store) {
        return new R12MembershipService(store);
    }

    @Bean
    R12RewardStore r12RewardStore(DataSource dataSource) {
        return new R12RewardStore(dataSource);
    }

    @Bean
    R12RewardService r12RewardService(R12RewardStore store) {
        return new R12RewardService(store);
    }
}
