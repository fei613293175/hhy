package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AdminUserServiceTest {
    @Mock
    private AdminUserStore store;

    private AutoCloseable mocks;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new AdminUserService(store);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void listUsesWhitelistedSortAndReturnsMaskedContractProjection() {
        var row = new AdminUserStore.UserRow(
                42L,
                "13812345678",
                "测试用户",
                "https://cdn.example.test/avatar.png",
                "简介",
                "ACTIVE",
                "VERIFIED",
                "ACTIVE",
                Instant.parse("2026-07-18T00:00:00Z"),
                7L);
        when(store.listUsers(
                1, 20, "ACTIVE", "测试", "u.created_at DESC,u.id DESC"))
                .thenReturn(new AdminUserStore.UserPage(List.of(row), 21L));

        var result = service.listUsers(1, 20, "ACTIVE", "测试", "createdAt:desc");

        assertEquals(1, result.items().size());
        assertEquals("138****5678", result.items().getFirst().phoneMasked());
        assertEquals("21", result.page().total());
        assertEquals("true", result.page().hasMore());
    }

    @Test
    void listRejectsUnregisteredSortBeforeQueryingTheDatabase() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.listUsers(1, 20, null, null, "phone:asc"));

        assertEquals(400, exception.httpStatus());
        assertEquals("COMMON-400-VALIDATION", exception.code());
        verifyNoInteractions(store);
    }

    @Test
    void detailDistinguishesInvalidIdentifiersFromMissingUsers() {
        BusinessException invalid = assertThrows(
                BusinessException.class,
                () -> service.getUser("not-a-number"));
        assertEquals(400, invalid.httpStatus());

        when(store.findUser(99L)).thenReturn(Optional.empty());
        BusinessException missing = assertThrows(
                BusinessException.class,
                () -> service.getUser("99"));
        assertEquals(404, missing.httpStatus());
        assertEquals("COMMON-404-NOT_FOUND", missing.code());
    }

    @Test
    void finalPageDoesNotAdvertiseMoreResults() {
        when(store.listUsers(2, 20, null, null, "u.id ASC"))
                .thenReturn(new AdminUserStore.UserPage(List.of(), 40L));

        var result = service.listUsers(2, 20, null, null, "id:asc");

        assertTrue(result.items().isEmpty());
        assertEquals("false", result.page().hasMore());
        assertFalse(Boolean.parseBoolean(result.page().hasMore()));
    }
}
