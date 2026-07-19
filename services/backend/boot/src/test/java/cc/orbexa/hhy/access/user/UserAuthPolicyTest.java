package cc.orbexa.hhy.access.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UserAuthPolicyTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void challengeTtlAcceptsTheFrozenDefault() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("auth.security_challenge.ttl_seconds")))
                .thenReturn(120);

        assertEquals(Duration.ofSeconds(120), new UserAuthPolicy(jdbc).challengeTtl());
    }

    @Test
    void challengeTtlRejectsUnsafeShortOrLongConfiguration() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("auth.security_challenge.ttl_seconds")))
                .thenReturn(59, 301);
        UserAuthPolicy policy = new UserAuthPolicy(jdbc);

        assertThrows(IllegalStateException.class, policy::challengeTtl);
        assertThrows(IllegalStateException.class, policy::challengeTtl);
    }
}
