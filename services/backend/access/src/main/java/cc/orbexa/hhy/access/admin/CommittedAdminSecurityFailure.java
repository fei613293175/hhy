package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.shared.api.BusinessException;

/**
 * A rejected administrator-security attempt whose failure fact must commit before
 * the exception is exposed to the HTTP layer.
 */
final class CommittedAdminSecurityFailure extends BusinessException {
    private static final long serialVersionUID = 1L;

    CommittedAdminSecurityFailure(
            String code, String message, int httpStatus, boolean retryable) {
        super(code, message, httpStatus, retryable);
    }
}
