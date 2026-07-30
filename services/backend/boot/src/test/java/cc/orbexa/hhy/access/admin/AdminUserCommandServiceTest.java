package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.admin.AdminUserContracts.RestrictionRequest;
import cc.orbexa.hhy.access.admin.AdminUserContracts.UserControlRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");
    private static final String KEY = "idem-user-command-0001";

    @Mock
    private AdminUserStore users;
    @Mock
    private AdminSecurityStore security;
    private AdminIdempotencySnapshotCipher snapshots;

    private AdminUserCommandService service;

    @BeforeEach
    void setUp() {
        var properties = new AdminSecurityProperties(
                "jwt-secret-12345678901234567890123456789012",
                "mfa-secret-12345678901234567890123456789012",
                "idem-secret-1234567890123456789012345678901",
                "test", Duration.ofHours(1), Duration.ofMinutes(5), Duration.ofMinutes(10),
                8, 72, 5, 900);
        snapshots = new AdminIdempotencySnapshotCipher(
                "v1", "mfa-secret-12345678901234567890123456789012", "");
        service = new AdminUserCommandService(
                users, security, new ObjectMapper().findAndRegisterModules(), snapshots,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(security.claimIdempotency(anyString(), eq(KEY), anyString(), any()))
                .thenReturn(new AdminSecurityStore.IdempotencyClaim(
                        new AdminSecurityStore.IdempotencyRow(9L, "ignored", null, null, null),
                        false));
    }

    @Test
    void restrictionPersistsStateIncrementsVersionAndAudits() {
        var before = user(42L, "ACTIVE", 3L);
        var after = user(42L, "RESTRICTED", 4L);
        when(users.findUserForUpdate(42L)).thenReturn(Optional.of(before), Optional.of(after));
        when(users.applyRestrictionStatus(42L, 3L)).thenReturn(true);

        var result = service.restrict(
                principal(7L), "42",
                new RestrictionRequest("LOGIN", "异常登录处置", NOW.plusSeconds(3600), 3L),
                KEY, "request-1", "127.0.0.1");

        assertEquals("RESTRICTED", result.user().status());
        assertEquals(4L, result.user().version());
        verify(security).claimIdempotency(
                eq("admin.user:7:restrict:42"), eq(KEY), anyString(), any());
        verify(users).upsertRestriction(
                42L, "LOGIN", "异常登录处置", NOW.plusSeconds(3600), 7L);
        verify(security).operationLog(
                eq(7L), eq("USER_RESTRICTION_UPSERTED"), eq("USER"), eq(42L),
                eq(null), anyString(), eq("127.0.0.1"));
        verify(security).completeIdempotencySnapshot(
                eq(9L), eq("42"), eq("r02.admin-user-command.v1"), anyString());
    }

    @Test
    void freezeRequiresASecondDistinctAdministrator() {
        var active = user(42L, "ACTIVE", 3L);
        when(users.findUserForUpdate(42L)).thenReturn(Optional.of(active));
        when(users.pendingFreezeApprovalForUpdate(42L))
                .thenReturn(Optional.of(new AdminUserStore.FreezeApproval(17L, 7L, 0L)));

        var result = service.freeze(
                principal(7L), "42", new UserControlRequest("违规处置", 3L),
                KEY, "request-2", "127.0.0.1");

        assertEquals("PENDING_APPROVAL", result.command().status());
        assertEquals("APR-17", result.command().businessNo());
        verify(users, never()).freezeUser(anyLong(), anyLong());
    }

    @Test
    void secondAdministratorApprovesFreezeAndRevokesSessions() {
        var before = user(42L, "ACTIVE", 3L);
        var after = user(42L, "FROZEN", 4L);
        when(users.findUserForUpdate(42L)).thenReturn(Optional.of(before), Optional.of(after));
        when(users.pendingFreezeApprovalForUpdate(42L))
                .thenReturn(Optional.of(new AdminUserStore.FreezeApproval(17L, 7L, 0L)));
        when(users.approveFreeze(17L, 0L, 8L)).thenReturn(true);
        when(users.freezeUser(42L, 3L)).thenReturn(true);

        var result = service.freeze(
                principal(8L), "42", new UserControlRequest("复核通过", 3L),
                KEY, "request-3", "127.0.0.2");

        assertEquals("FROZEN", result.user().status());
        verify(security).claimIdempotency(
                eq("admin.user:8:freeze:42"), eq(KEY), anyString(), any());
        verify(users).approveFreeze(17L, 0L, 8L);
        verify(users).revokeUserSessions(42L, NOW);
    }

    @Test
    void staleExpectedVersionIsRejectedBeforeDomainMutation() {
        when(users.findUserForUpdate(42L)).thenReturn(Optional.of(user(42L, "ACTIVE", 4L)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.forceLogout(
                        principal(7L), "42", new UserControlRequest("安全下线", 3L),
                        KEY, "request-4", "127.0.0.1"));

        assertEquals(409, exception.httpStatus());
        assertEquals("COMMON-409-VERSION_CONFLICT", exception.code());
        verify(users, never()).revokeUserSessions(anyLong(), any());
    }

    private static AdminPrincipal principal(long adminId) {
        return new AdminPrincipal(adminId, 1L, 1L, "jti", "admin-" + adminId,
                Set.of("user.restrict", "user.freeze", "user.security"));
    }

    private static AdminUserStore.UserRow user(long id, String status, long version) {
        return new AdminUserStore.UserRow(
                id, "13812345678", "用户", null, null, status, null, null,
                NOW.minusSeconds(3600), version);
    }
}
