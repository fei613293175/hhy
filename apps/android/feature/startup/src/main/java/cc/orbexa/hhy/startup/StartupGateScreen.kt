package cc.orbexa.hhy.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import cc.orbexa.hhy.designsystem.HhyColors
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
        StartupGateState.Loading -> GateMessage(title = "正在启动") {
            CircularProgressIndicator()
        }
        is StartupGateState.Maintenance -> GateMessage(
            title = "系统维护中",
            message = current.message,
        ) {
            RetryButton { retryKey += 1 }
        }
        is StartupGateState.Unavailable -> GateMessage(
            title = "暂时无法连接",
            message = current.requestId?.let { "请求编号：$it" },
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
        title = title,
        message = if (trustedDownloadUrl == null) {
            "更新地址无效，请联系支持人员。"
        } else {
            notes
        },
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = trustedDownloadUrl != null,
            onClick = { trustedDownloadUrl?.let(uriHandler::openUri) },
        ) {
            Text(if (state.forced) "立即更新" else "下载更新")
        }
        if (!state.forced) {
            Spacer(Modifier.height(HhySpacing.Sm))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSkip,
            ) {
                Text("稍后")
            }
        }
    }
}

@Composable
private fun GateMessage(
    title: String,
    message: String? = null,
    actions: @Composable () -> Unit,
) {
    Surface(color = HhyColors.PageBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HhySpacing.Xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = HhyColors.TextPrimary,
            )
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(HhySpacing.Md))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HhyColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(HhySpacing.Xxl))
            actions()
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text("重试")
    }
}
