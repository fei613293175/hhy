package cc.orbexa.hhy.access.identity;

import cc.orbexa.hhy.shared.api.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** One-time public handoff for the provider browser return; no identity data is exposed. */
@Service
public class IdentityCallbackService {
    private static final String STATE_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    private final CallbackConsumer consumer;
    private final Clock clock;

    @Autowired
    public IdentityCallbackService(JdbcTemplate jdbc, Clock clock) {
        this((state, now) -> consumeAtomically(jdbc, state, now), clock);
    }

    IdentityCallbackService(CallbackConsumer consumer, Clock clock) {
        this.consumer = consumer;
        this.clock = clock;
    }

    public CallbackStatusResource consume(CallbackConsumeRequest request) {
        String state = request == null || request.state() == null ? "" : request.state().strip();
        if (!state.matches(STATE_PATTERN)) throw expired();
        String status = consumer.consume(state, Instant.now(clock));
        if (status == null || status.isBlank()) throw expired();
        return new CallbackStatusResource(status);
    }

    private static String consumeAtomically(JdbcTemplate jdbc, String state, Instant now) {
        List<String> statuses = jdbc.query("""
                UPDATE hhy.identity_verification_sessions
                SET callback_consumed_at=?
                WHERE state=? AND callback_consumed_at IS NULL AND expires_at>?
                RETURNING status
                """, (rs, row) -> rs.getString("status"),
                Timestamp.from(now), state, Timestamp.from(now));
        return statuses.stream().findFirst().orElse(null);
    }

    private static BusinessException expired() {
        return new BusinessException(
                "COMMON-422-BUSINESS_RULE", "认证页面已失效，请返回合伙云重新开始", 422, false);
    }

    @FunctionalInterface
    interface CallbackConsumer {
        String consume(String state, Instant now);
    }

    public record CallbackConsumeRequest(
            @NotBlank @Pattern(regexp = STATE_PATTERN) String state) { }

    public record CallbackStatusResource(String status) { }
}
