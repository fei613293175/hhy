package cc.orbexa.hhy.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.network.PublisherSummaryResource

@Composable
fun R14BlockDialog(peer: PublisherSummaryResource, unblock: Boolean, submitting: Boolean, failure: String?, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (unblock) "解除拉黑" else "确认拉黑") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    if (unblock) {
                        "解除后，将恢复服务端允许的消息能力，不会自动发送消息。"
                    } else {
                        "拉黑 ${peer.nickname} 后，双方将无法继续互发消息；公开信息仍按平台权限规则显示。"
                    },
                )
                if (!unblock) OutlinedTextField(reason, { if (it.length <= 2_000) reason = it }, Modifier.fillMaxWidth(), label = { Text("拉黑原因（可选）") })
                failure?.let { Text(it, color = HhyColors.Error) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason.trim().takeIf(String::isNotEmpty)) }, enabled = !submitting) { Text(if (submitting) "正在处理" else if (unblock) "解除拉黑" else "确认拉黑") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") } },
    )
}

@Composable
fun R14DeleteConversationDialog(peer: PublisherSummaryResource, submitting: Boolean, failure: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("删除会话") },
        text = { androidx.compose.foundation.layout.Column { Text("将从当前账号的会话列表中删除与 ${peer.nickname} 的会话视图，不会删除对方的消息记录。"); failure?.let { Text(it, color = HhyColors.Error) } } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !submitting) { Text(if (submitting) "正在删除" else "删除会话", color = HhyColors.Error) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") } },
    )
}
