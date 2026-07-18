package cc.orbexa.hhy.access.user;

import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserAuthPolicy {
    private final JdbcTemplate jdbc;

    public UserAuthPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Duration challengeTtl() { return Duration.ofSeconds(integer("auth.security_challenge.ttl_seconds")); }
    public int challengeMaxAttempts() { return integer("auth.security_challenge.max_attempts"); }
    public int passwordMinLength() { return integer("auth.password.min_length"); }
    public int passwordMaxLength() { return integer("auth.password.max_length"); }
    public int passwordMaxFailures() { return integer("auth.password.max_failures"); }
    public Duration passwordLockDuration() { return Duration.ofSeconds(integer("auth.password.lock_seconds")); }
    public boolean passwordRequireLetters() { return bool("auth.password.require_letters"); }
    public boolean passwordRequireDigits() { return bool("auth.password.require_digits"); }
    public int smsCodeLength() { return integer("sms.code.length"); }
    public Duration smsTtl() { return Duration.ofSeconds(integer("sms.code.ttl_seconds")); }
    public Duration smsCooldown() { return Duration.ofSeconds(integer("sms.send.cooldown_seconds")); }
    public int smsDailyPhoneLimit() { return integer("sms.send.daily_phone_limit"); }
    public int smsDailyIpLimit() { return integer("sms.send.daily_ip_limit"); }
    public int smsMaxAttempts() { return integer("sms.verify.max_attempts"); }

    private int integer(String key) {
        Integer value = jdbc.queryForObject("""
                SELECT (value_json #>> '{}')::integer
                FROM hhy.system_configs WHERE key=? AND scope='GLOBAL'
                """, Integer.class, key);
        if (value == null || value <= 0) {
            throw new IllegalStateException("Invalid active authentication policy: " + key);
        }
        return value;
    }

    private boolean bool(String key) {
        Boolean value = jdbc.queryForObject("""
                SELECT (value_json #>> '{}')::boolean
                FROM hhy.system_configs WHERE key=? AND scope='GLOBAL'
                """, Boolean.class, key);
        if (value == null) {
            throw new IllegalStateException("Missing active authentication policy: " + key);
        }
        return value;
    }
}
