package cc.orbexa.hhy.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.StartupGate
import cc.orbexa.hhy.network.StartupGateRequest
import cc.orbexa.hhy.network.StartupGateState

@Composable
fun StartupGateScreen(
    gate: StartupGate,
    request: StartupGateRequest,
    content: @Composable () -> Unit,
) {
    var retryKey by remember { mutableIntStateOf(0) }
    var skippedVersionCode by rememberSaveable { mutableLongStateOf(-1L) }
    val state by produceState<StartupGateState>(
        initialValue = StartupGateState.Loading,
        gate,
        request,
        retryKey,
    ) {
        value = StartupGateState.Loading
        value = gate.evaluate(request)
    }

    when (val current = state) {
        StartupGateState.Loading -> GateMessage(
            eyebrow = "安全启动",
            title = "正在启动",
            message = "正在检查服务状态与版本信息",
            supportText = "完成后将自动进入合伙云 Pro",
            icon = HhyIcons.Shield,
        ) {
            CircularProgressIndicator()
        }
        is StartupGateState.Maintenance -> GateMessage(
            eyebrow = "服务状态",
            title = "系统维护中",
            message = current.message,
            supportText = "服务端当前返回维护状态，请稍后重新检查",
            icon = HhyIcons.Information,
        ) {
            RetryButton { retryKey += 1 }
        }
        is StartupGateState.Unavailable -> GateMessage(
            eyebrow = "网络连接",
            title = "暂时无法连接",
            message = "无法完成服务与版本检查",
            supportText = "请检查网络连接后重新尝试",
            icon = HhyIcons.Error,
        ) {
            RetryButton { retryKey += 1 }
        }
        is StartupGateState.UpdateRequired -> {
            if (!current.forced && skippedVersionCode == current.policy.latestVersionCode) {
                content()
            } else {
                UpdateScreen(
                    state = current,
                    onSkip = { skippedVersionCode = current.policy.latestVersionCode },
                )
            }
        }
        is StartupGateState.Ready -> content()
    }
}

@Composable
private fun UpdateScreen(
    state: StartupGateState.UpdateRequired,
    onSkip: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val title = state.page?.title ?: "发现新版本 ${state.policy.latestVersionName}"
    val notes = state.page?.description ?: state.policy.releaseNotes
    val downloadUrl = state.page?.download?.downloadUrl ?: state.policy.downloadUrl
    val trustedDownloadUrl = DownloadUrlPolicy.trustedOrNull(downloadUrl)
    GateMessage(
        eyebrow = "版本更新",
        title = title,
        message = if (trustedDownloadUrl == null) {
            "更新地址无效，请联系支持人员。"
        } else {
            notes
        },
        supportText = if (state.forced) {
            "当前版本需要更新后才能继续使用"
        } else {
            "可现在更新，也可稍后从应用内再次检查"
        },
        icon = HhyIcons.Information,
    ) {
        Button(
            modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
            enabled = trustedDownloadUrl != null,
            onClick = { trustedDownloadUrl?.let(uriHandler::openUri) },
        ) {
            Text(if (state.forced) "立即更新" else "下载更新")
        }
        if (!state.forced) {
            Spacer(Modifier.height(HhySpacing.Sm))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                onClick = onSkip,
            ) {
                Text("稍后")
            }
        }
    }
}

@Composable
private fun GateMessage(
    eyebrow: String,
    title: String,
    message: String? = null,
    supportText: String,
    icon: ImageVector,
    actions: @Composable () -> Unit,
) {
    Surface(color = HhyColors.PageBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Xxxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                shape = RoundedCornerShape(HhyRadius.LargeCard),
                colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = HhyColors.SoftBlue,
                        shape = RoundedCornerShape(HhyRadius.NormalCard),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(shape = CircleShape, color = HhyColors.Surface) {
                                HhyIcon(
                                    icon,
                                    contentDescription = null,
                                    modifier = Modifier.padding(HhySpacing.Md),
                                    tint = HhyColors.BrandPrimary,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                Text(
                                    eyebrow,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HhyColors.BrandPrimary,
                                )
                                Text(
                                    "合伙云 Pro",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HhyColors.TextPrimary,
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = HhyColors.TextPrimary,
                        )
                        if (!message.isNullOrBlank()) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = HhyColors.TextSecondary,
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = HhyColors.PageBackground,
                        shape = RoundedCornerShape(HhyRadius.Tag),
                    ) {
                        Text(
                            supportText,
                            modifier = Modifier.padding(HhySpacing.Md),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HhyColors.TextSecondary,
                        )
                    }
                    actions()
                }
            }
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
        onClick = onClick,
    ) {
        Text("重试")
    }
}
