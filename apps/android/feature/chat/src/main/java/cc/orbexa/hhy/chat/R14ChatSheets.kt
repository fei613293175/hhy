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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.media.MediaUploadSelection
import cc.orbexa.hhy.network.ChatContactField
import cc.orbexa.hhy.network.ChatContactCardPayload
import cc.orbexa.hhy.network.ChatContentCardPayload
import cc.orbexa.hhy.network.ChatImagePayload
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatTextPayload
import cc.orbexa.hhy.network.PublisherSummaryResource

private val contactTypes = listOf("PHONE" to "手机号", "WECHAT" to "微信", "QQ" to "QQ", "EMAIL" to "邮箱", "OTHER" to "其他")

data class R14ReportReasonOption(val code: String, val label: String)

data class R14ReportDraft(
    val reasonCode: String? = null,
    val description: String = "",
    val messageIds: Set<String> = emptySet(),
    val evidence: List<MediaUploadSelection> = emptyList(),
) {
    init {
        require(description.length <= 2_000)
        require(messageIds.size <= 100 && evidence.size <= 100)
        require(evidence.distinctBy(MediaUploadSelection::mediaId).size == evidence.size)
        require(evidence.all { it.contentType.startsWith("image/") })
    }
}

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
fun R14SafetySheet(peer: PublisherSummaryResource, blocked: Boolean, onDismiss: () -> Unit, onReport: () -> Unit, onBlock: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.sheet.r14.safety"),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Text(peer.nickname, fontSize = HhyType.PageTitleSize, lineHeight = HhyType.PageTitleLineHeight, fontWeight = FontWeight.Bold)
            Text("会话安全与管理", color = HhyColors.TextSecondary)
            TextButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text("举报聊天") }
            TextButton(onClick = onBlock, modifier = Modifier.fillMaxWidth()) { Text(if (blocked) "解除拉黑" else "拉黑该用户") }
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
    draft: R14ReportDraft,
    versionAvailable: Boolean,
    submitting: Boolean,
    failure: String?,
    onDismiss: () -> Unit,
    onDraftChange: (R14ReportDraft) -> Unit,
    onAddEvidence: () -> Unit,
    onSubmit: (R14ReportDraft) -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val canEdit = reasons.isNotEmpty() && versionAvailable && !submitting
    ModalBottomSheet(onDismissRequest = { if (!submitting) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Text("举报聊天", fontSize = HhyType.PageTitleSize, lineHeight = HhyType.PageTitleLineHeight, fontWeight = FontWeight.Bold)
            Text("举报与 ${peer.nickname} 的聊天。提交内容仅用于核实本次举报。", color = HhyColors.TextSecondary)
            if (reasons.isEmpty()) Text("举报原因配置暂不可用，当前无法提交。", color = HhyColors.Warning)
            else if (!versionAvailable) Text("会话状态需要刷新，当前无法提交举报。", color = HhyColors.Warning)
            reasons.forEach { reason ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = draft.reasonCode == reason.code,
                        onClick = { onDraftChange(draft.copy(reasonCode = reason.code)) },
                        enabled = canEdit,
                    )
                    Text(reason.label, Modifier.padding(top = HhySpacing.Md))
                }
            }
            OutlinedTextField(
                value = draft.description,
                onValueChange = { if (it.length <= 2_000) onDraftChange(draft.copy(description = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
                label = { Text("补充说明（可选）") },
                supportingText = { Text("${draft.description.length}/2000") },
                minLines = 3,
            )
            if (messages.isNotEmpty()) Text("消息证据", fontWeight = FontWeight.SemiBold)
            messages.take(100).forEach { message ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = message.id in draft.messageIds,
                        onCheckedChange = { checked ->
                            val selected = if (checked) draft.messageIds + message.id else draft.messageIds - message.id
                            onDraftChange(draft.copy(messageIds = selected.take(100).toSet()))
                        },
                        enabled = canEdit,
                    )
                    Text(r14EvidencePreview(message), Modifier.padding(top = HhySpacing.Md), maxLines = 1)
                }
            }
            Text("图片证据 ${draft.evidence.size}/100", fontWeight = FontWeight.SemiBold)
            draft.evidence.forEachIndexed { index, evidence ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("图片证据 ${index + 1}", modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onDraftChange(draft.copy(evidence = draft.evidence.filterNot { it.mediaId == evidence.mediaId })) },
                        enabled = canEdit,
                    ) { Text("移除") }
                }
            }
            OutlinedButton(
                onClick = onAddEvidence,
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit && draft.evidence.size < 100,
            ) { Text(if (draft.evidence.isEmpty()) "添加图片证据" else "继续添加图片证据") }
            failure?.let { Text(it, color = HhyColors.Error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                OutlinedButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { confirming = true }, enabled = draft.reasonCode != null && canEdit, modifier = Modifier.weight(1f)) { Text(if (submitting) "正在提交" else "提交举报") }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { if (!submitting) confirming = false },
            title = { Text("确认举报") },
            text = { Text("将提交所选原因、补充说明、${draft.messageIds.size} 条消息证据和 ${draft.evidence.size} 张图片证据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onSubmit(draft)
                    },
                    enabled = !submitting,
                ) { Text("确认提交") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }, enabled = !submitting) { Text("返回检查") } },
        )
    }
}

internal fun mergeR14Evidence(
    current: List<MediaUploadSelection>,
    incoming: List<MediaUploadSelection>,
): List<MediaUploadSelection> = (current + incoming)
    .filter { it.contentType.startsWith("image/") }
    .distinctBy(MediaUploadSelection::mediaId)
    .take(100)

internal fun r14EvidencePreview(message: ChatMessageResource): String = when (val payload = message.payload) {
    is ChatTextPayload -> payload.text.take(60)
    is ChatImagePayload -> "图片消息"
    is ChatContentCardPayload -> payload.title.take(60)
    is ChatContactCardPayload -> "联系方式卡片"
}
