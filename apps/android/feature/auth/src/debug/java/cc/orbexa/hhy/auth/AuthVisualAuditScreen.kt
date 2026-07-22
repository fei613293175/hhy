package cc.orbexa.hhy.auth

import androidx.compose.runtime.Composable
import cc.orbexa.hhy.network.ContractAuthApi
import cc.orbexa.hhy.network.UserSelfResource

/** Debug-only entrypoints that render production authentication components for visual auditing. */
enum class AuthVisualAuditMode {
    LOGIN,
    ACCOUNT_BLOCKED,
    LOGIN_DEVICES,
    CHANGE_PASSWORD,
    ACCOUNT_CANCELLATION,
}

@Composable
fun AuthVisualAuditScreen(mode: AuthVisualAuditMode, api: ContractAuthApi) {
    val auditUser = UserSelfResource(
        id = "visual-audit-user",
        phoneMasked = "138****0000",
        status = if (mode == AuthVisualAuditMode.ACCOUNT_BLOCKED) "FROZEN" else "ACTIVE",
        identityStatus = "VERIFIED",
        membershipStatus = "ACTIVE",
        version = 1,
    )
    when (mode) {
        AuthVisualAuditMode.LOGIN -> AuthScreen(api) { true }
        AuthVisualAuditMode.ACCOUNT_BLOCKED -> AccountBlockedScreen(api, "visual-audit-token", auditUser) {}
        AuthVisualAuditMode.LOGIN_DEVICES -> LoginDevicesScreen(api, "visual-audit-token") {}
        AuthVisualAuditMode.CHANGE_PASSWORD -> ChangeLoginPasswordScreen(api, "visual-audit-token", {}, {})
        AuthVisualAuditMode.ACCOUNT_CANCELLATION -> AccountCancellationScreen(
            api,
            "visual-audit-token",
            auditUser,
            {},
            {},
        )
    }
}
