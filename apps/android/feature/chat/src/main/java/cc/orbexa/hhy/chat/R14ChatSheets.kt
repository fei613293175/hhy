package cc.orbexa.hhy.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.network.ChatContactField
import cc.orbexa.hhy.network.ChatContactCardPayload
import cc.orbexa.hhy.network.ChatContentCardPayload
import cc.orbexa.hhy.network.ChatImagePayload
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatTextPayload
import cc.orbexa.hhy.network.PublisherSummaryResource

private val contactTypes = listOf("PHONE" to "手机号", "WECHAT" to "微信", "QQ" to "QQ", "EMAIL" to "邮箱", "OTHER" to "其他")

data class R14ReportReasonOption(val code: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R14ContactSheet(
    peer: PublisherSummaryResource,
    submitting: Boolean,
    failure: String?,
    onDismiss: () -> Unit,
    onSubmit: (List<ChatContactField>, String?) -> Unit,
) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val values = remember { mutableStateMapOf<String, String>() }
    var otherLabel by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val fields = contactTypes.mapNotNull { (type, _) ->
        values[type]?.trim()?.takeIf { selected[type] == true && it.isNotEmpty() }?.let {
            ChatContactField(type, otherLabel.trim().takeIf { type == "OTHER" && it.isNotEmpty() }, it)
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!submitting) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Text("发送联系方式", fontSize = HhyType.PageTitleSize, lineHeight = HhyType.PageTitleLineHeight, fontWeight = FontWeight.Bold)
            Text("发送给 ${peer.nickname}。只会发送你主动选择的内容。", color = HhyColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                contactTypes.take(3).forEach { (type, label) -> FilterChip(selected = selected[type] == true, onClick = { selected[type] = selected[type] != true }, label = { Text(label) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                contactTypes.drop(3).forEach { (type, label) -> FilterChip(selected = selected[type] == true, onClick = { selected[type] = selected[type] != true }, label = { Text(label) }) }
            }
            contactTypes.filter { selected[it.first] == true }.forEach { (type, label) ->
                if (type == "OTHER") OutlinedTextField(otherLabel, { if (it.length <= 32) otherLabel = it }, Modifier.fillMaxWidth(), label = { Text("联系方式名称") }, singleLine = true)
                OutlinedTextField(values[type].orEmpty(), { if (it.length <= 256) values[type] = it }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
            }
            OutlinedTextField(note, { if (it.length <= 200) note = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, supportingText = { Text("${note.length}/200") }, minLines = 2)
            failure?.let { Text(it, color = HhyColors.Error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                OutlinedButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { onSubmit(fields, note.trim().takeIf(String::isNotEmpty)) }, enabled = fields.isNotEmpty() && !submitting, modifier = Modifier.weight(1f)) { Text(if (submitting) "正在发送" else "发送联系方式") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R14SafetySheet(peer: PublisherSummaryResource, blocked: Boolean, onDismiss: () -> Unit, onReport: () -> Unit, onBlock: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Text(peer.nickname, fontSize = HhyType.PageTitleSize, lineHeight = HhyType.PageTitleLineHeight, fontWeight = FontWeight.Bold)
            Text("会话安全与管理", color = HhyColors.TextSecondary)
            TextButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text("举报聊天") }
            TextButton(onClick = onBlock, modifier = Modifier.fillMaxWidth()) { Text(if (blocked) "解除拉黑" else "拉黑该用户") }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("删除会话", color = HhyColors.Error) }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R14ReportSheet(
    peer: PublisherSummaryResource,
    reasons: List<R14ReportReasonOption>,
    messages: List<ChatMessageResource>,
    submitting: Boolean,
    failure: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, List<String>) -> Unit,
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val selectedMessages = remember { mutableStateMapOf<String, Boolean>() }
    ModalBottomSheet(onDismissRequest = { if (!submitting) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Text("举报聊天", fontSize = HhyType.PageTitleSize, lineHeight = HhyType.PageTitleLineHeight, fontWeight = FontWeight.Bold)
            Text("举报与 ${peer.nickname} 的聊天。提交内容仅用于核实本次举报。", color = HhyColors.TextSecondary)
            if (reasons.isEmpty()) Text("举报原因配置暂不可用，当前无法提交。", color = HhyColors.Warning)
            reasons.forEach { reason -> Row(Modifier.fillMaxWidth()) { RadioButton(selectedReason == reason.code, { selectedReason = reason.code }); Text(reason.label, Modifier.padding(top = HhySpacing.Md)) } }
            OutlinedTextField(description, { if (it.length <= 2_000) description = it }, Modifier.fillMaxWidth(), enabled = reasons.isNotEmpty(), label = { Text("补充说明（可选）") }, supportingText = { Text("${description.length}/2000") }, minLines = 3)
            if (messages.isNotEmpty()) Text("消息证据", fontWeight = FontWeight.SemiBold)
            messages.take(100).forEach { message ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = selectedMessages[message.id] == true,
                        onCheckedChange = { selectedMessages[message.id] = it },
                        enabled = reasons.isNotEmpty(),
                    )
                    Text(r14EvidencePreview(message), Modifier.padding(top = HhySpacing.Md), maxLines = 1)
                }
            }
            failure?.let { Text(it, color = HhyColors.Error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                OutlinedButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { confirming = true }, enabled = selectedReason != null && !submitting, modifier = Modifier.weight(1f)) { Text(if (submitting) "正在提交" else "提交举报") }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { if (!submitting) confirming = false },
            title = { Text("确认举报") },
            text = { Text("将提交所选原因、补充说明和 ${selectedMessages.count { it.value }} 条消息证据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onSubmit(selectedReason.orEmpty(), description, selectedMessages.filterValues { it }.keys.toList())
                    },
                    enabled = !submitting,
                ) { Text("确认提交") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }, enabled = !submitting) { Text("返回检查") } },
        )
    }
}

internal fun r14EvidencePreview(message: ChatMessageResource): String = when (val payload = message.payload) {
    is ChatTextPayload -> payload.text.take(60)
    is ChatImagePayload -> "图片消息"
    is ChatContentCardPayload -> payload.title.take(60)
    is ChatContactCardPayload -> "联系方式卡片"
}
