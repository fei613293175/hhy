package cc.orbexa.hhy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ExperienceApi
import cc.orbexa.hhy.network.VersionCheckRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(api: ExperienceApi, accessToken: String, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var policy by remember { mutableStateOf<cc.orbexa.hhy.network.VersionPolicy?>(null) }
    var agreement by remember { mutableStateOf<cc.orbexa.hhy.network.AgreementSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var refreshKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(api, accessToken, refreshKey) {
        loading = true; failed = false
        api.checkVersion(VersionCheckRequest("ANDROID", BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME, BuildConfig.APP_CHANNEL, BuildConfig.APP_ENVIRONMENT)).onSuccess { policy = it }.onFailure { failed = true }
        api.agreement("PRIVACY_POLICY").onSuccess { agreement = it }
        loading = false
    }
    val screenMarker = when {
        loading -> "hhy.screen.r06.about.loading"
        failed -> "hhy.screen.r06.about.error"
        else -> "hhy.screen.r06.about.loaded"
    }
    val currentPublicVersion = publicVersionName(BuildConfig.VERSION_NAME)
    val latestPublicVersion = publicVersionName(policy?.latestVersionName)
    val updateAvailable = policy?.updateType != null && policy?.updateType != cc.orbexa.hhy.network.UpdateType.NONE
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(screenMarker),
        topBar = { TopAppBar(title = { Text("关于与检查更新") }, navigationIcon = { cc.orbexa.hhy.designsystem.HhyBackButton(onBack) }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(HhyRadius.LargeCard),
                    colors = CardDefaults.cardColors(containerColor = HhyColors.SoftBlue),
                    elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                    ) {
                        Surface(shape = CircleShape, color = HhyColors.Surface) {
                            HhyIcon(HhyIcons.Information, contentDescription = null, modifier = Modifier.padding(HhySpacing.Lg), tint = HhyColors.BrandPrimary)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                            Text("合伙云 Pro", style = MaterialTheme.typography.headlineSmall, color = HhyColors.TextPrimary)
                            Text("当前版本 $currentPublicVersion", color = HhyColors.TextSecondary)
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(HhyRadius.NormalCard),
                    colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                ) { Column(modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                    Text("版本状态", style = MaterialTheme.typography.titleMedium, color = HhyColors.TextPrimary)
                    if (loading) { CircularProgressIndicator(); Text("正在检查最新版本", color = HhyColors.TextSecondary) }
                    else if (failed) {
                        Text("版本信息暂时无法获取", color = HhyColors.TextPrimary)
                        Text("请检查网络后重新尝试，当前版本仍可继续使用。", color = HhyColors.TextSecondary)
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { refreshKey += 1 }) { Text("重新检查") }
                    }
                    else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("最新版本", color = HhyColors.TextSecondary)
                            Text(latestPublicVersion ?: currentPublicVersion, color = HhyColors.TextPrimary)
                        }
                        Surface(color = HhyColors.PageBackground, shape = RoundedCornerShape(HhyRadius.Tag)) {
                            Column(Modifier.fillMaxWidth().padding(HhySpacing.Md), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                Text("更新说明", style = MaterialTheme.typography.labelLarge)
                                Text(publicReleaseNotes(policy?.releaseNotes, updateAvailable), color = HhyColors.TextSecondary)
                            }
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth().heightIn(min = HhySize.PrimaryButtonHeight),
                            onClick = { policy?.downloadUrl?.takeIf(::isSafeDownloadUrl)?.let { uriHandler.openUri(it) } },
                            enabled = policy?.updateType != cc.orbexa.hhy.network.UpdateType.NONE && isSafeDownloadUrl(policy?.downloadUrl),
                        ) { Text("立即更新") }
                    }
                } }
            }
            agreement?.let { page ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(HhyRadius.NormalCard),
                        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                            Text(page.title ?: "隐私政策", style = MaterialTheme.typography.titleMedium)
                            page.description?.let { Text(it, color = HhyColors.TextSecondary) }
                            page.content.forEach { text -> Text(text, style = MaterialTheme.typography.bodyMedium, color = HhyColors.TextPrimary) }
                        }
                    }
                }
            }
        }
    }
}

internal fun isSafeDownloadUrl(value: String?): Boolean = runCatching {
    val uri = java.net.URI(value ?: return false)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)

/** Removes build-channel metadata from user-visible version copy only. */
internal fun publicVersionName(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val semantic = candidate.substringBefore('-').substringBefore('+')
    return semantic.takeIf { it.matches(Regex("\\d+(?:\\.\\d+){1,3}")) }
}

/** Internal test/candidate notes must never escape into the customer-facing About page. */
internal fun publicReleaseNotes(value: String?, updateAvailable: Boolean): String {
    val note = value?.trim().orEmpty()
    val internalMarkers = listOf("debug", "p00", "测试包", "验收", "内测", "候选包", "staging")
    val safe = note.isNotEmpty() && internalMarkers.none { marker -> note.contains(marker, ignoreCase = true) }
    if (safe) return note
    return if (updateAvailable) "发现新版本，可在确认后安全更新。" else "当前已是最新版本。"
}
