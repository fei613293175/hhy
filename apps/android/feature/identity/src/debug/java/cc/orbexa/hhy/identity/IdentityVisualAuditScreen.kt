package cc.orbexa.hhy.identity

import androidx.compose.runtime.Composable
import cc.orbexa.hhy.network.ContractIdentityApi
import cc.orbexa.hhy.network.IdentitySessionResource

/** Debug-only state selector; every surface below is the production R05 composable. */
enum class IdentityVisualAuditMode { HOME, FORM, LIVENESS, RESULT }

@Composable
fun IdentityVisualAuditScreen(mode: IdentityVisualAuditMode, api: ContractIdentityApi) {
    val session = IdentitySessionResource(
        id = "visual-audit-session",
        userId = "visual-audit-user",
        status = if (mode == IdentityVisualAuditMode.RESULT) "VERIFIED" else "SESSION_CREATED",
        livenessUrl = if (mode == IdentityVisualAuditMode.LIVENESS) "https://h5.orbexa.cc/identity/visual-audit" else null,
        version = 1,
    )
    when (mode) {
        IdentityVisualAuditMode.HOME -> IdentityHomeScreen(api, "visual-audit-token", 0, {}, {}, {}, {})
        IdentityVisualAuditMode.FORM -> IdentityFormScreen(api, "visual-audit-token", {}, {}, {})
        IdentityVisualAuditMode.LIVENESS -> IdentityLivenessScreen(
            api,
            "visual-audit-token",
            session,
            "https://h5.orbexa.cc/identity/callback",
            {},
            {},
            {},
            {},
        )
        IdentityVisualAuditMode.RESULT -> IdentityResultScreen(
            api,
            "visual-audit-token",
            session,
            {},
            {},
            {},
            {},
        )
    }
}
