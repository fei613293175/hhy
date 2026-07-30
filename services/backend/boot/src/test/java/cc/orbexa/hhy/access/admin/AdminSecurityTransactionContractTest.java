package cc.orbexa.hhy.access.admin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cc.orbexa.hhy.access.admin.AdminSecurityContracts.LoginRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaConfirmRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaDisableRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.MfaVerifyRequest;
import cc.orbexa.hhy.access.admin.AdminSecurityContracts.PasswordChangeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AdminSecurityTransactionContractTest {
    @Test
    void failureFactWriterMustJoinTheAdvisoryLockTransaction() throws Exception {
        Transactional transaction = AdminLoginFactWriter.class
                .getMethod("failure", Long.class, String.class, String.class, String.class,
                        String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transaction.propagation());
    }

    @Test
    void rejectedAttemptsCommitTheirFailureFactBeforeLeavingTheServiceProxy() throws Exception {
        assertCommittedFailure("login", LoginRequest.class, String.class, String.class,
                String.class, String.class);
        assertCommittedFailure("verifyMfa", MfaVerifyRequest.class, String.class, String.class,
                String.class, String.class, String.class);
        assertCommittedFailure("changePassword", AdminPrincipal.class, PasswordChangeRequest.class,
                String.class, String.class, String.class, String.class);
        assertCommittedFailure("confirmMfa", AdminPrincipal.class, MfaConfirmRequest.class,
                String.class, String.class, String.class, String.class);
        assertCommittedFailure("disableMfa", AdminPrincipal.class, MfaDisableRequest.class,
                String.class, String.class, String.class, String.class);
    }

    private static void assertCommittedFailure(String method, Class<?>... parameterTypes)
            throws Exception {
        Transactional transaction = AdminSecurityService.class
                .getMethod(method, parameterTypes)
                .getAnnotation(Transactional.class);
        assertArrayEquals(
                new Class<?>[] {CommittedAdminSecurityFailure.class},
                transaction.noRollbackFor());
    }
}
