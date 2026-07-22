package cc.orbexa.hhy.startup

import androidx.compose.runtime.Composable
import cc.orbexa.hhy.network.DownloadInfo
import cc.orbexa.hhy.network.PublicPage
import cc.orbexa.hhy.network.StartupGateState
import cc.orbexa.hhy.network.UpdateType
import cc.orbexa.hhy.network.VersionPolicy

enum class StartupVisualAuditMode {
    LOADING,
    MAINTENANCE,
    UPDATE,
}

/**
 * Debug-only selector for deterministic emulator evidence.
 *
 * Every state is rendered by [StartupGateStateContent], the same production composable used by
 * [StartupGateScreen]. The selector is absent from release variants and does not simulate a
 * production service response or alter the startup state machine.
 */
@Composable
fun StartupVisualAuditScreen(mode: StartupVisualAuditMode) {
    val state = when (mode) {
        StartupVisualAuditMode.LOADING -> StartupGateState.Loading
        StartupVisualAuditMode.MAINTENANCE -> StartupGateState.Maintenance(
            "服务正在进行计划维护，请稍后重试",
        )
        StartupVisualAuditMode.UPDATE -> StartupGateState.UpdateRequired(
            policy = VersionPolicy(
                platform = "ANDROID",
                latestVersionCode = 10217,
                latestVersionName = "1.2.3",
                updateType = UpdateType.OPTIONAL,
                downloadUrl = "https://download.orbexa.cc/app/hhy-latest.apk",
                sha256 = "0".repeat(64),
                releaseNotes = "优化页面体验与稳定性",
                minSupportedVersionCode = 10216,
                serverTime = "2026-07-22T00:00:00Z",
            ),
            page = PublicPage(
                code = "APP_LATEST_ANDROID",
                title = "发现新版本 1.2.3",
                description = "优化页面体验与稳定性",
                download = DownloadInfo(
                    platform = "ANDROID",
                    versionName = "1.2.3",
                    versionCode = 10217,
                    downloadUrl = "https://download.orbexa.cc/app/hhy-latest.apk",
                    sha256 = "0".repeat(64),
                ),
                version = 1,
            ),
            forced = false,
        )
    }
    StartupGateStateContent(
        current = state,
        skippedVersionCode = -1,
        onRetry = {},
        onSkip = { _ -> },
        content = {},
    )
}
