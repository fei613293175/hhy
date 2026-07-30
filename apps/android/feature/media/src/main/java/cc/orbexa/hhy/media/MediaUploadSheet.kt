package cc.orbexa.hhy.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.network.ContractMediaApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaUploadSheet(
    api: ContractMediaApi,
    accessToken: String,
    purpose: String,
    onCompleted: (List<MediaUploadSelection>) -> Unit,
    onDismiss: () -> Unit,
    maxConcurrentUploads: Int,
    maxSelectionCount: Int = Int.MAX_VALUE,
    onAuthenticationRequired: () -> Unit = {},
    onPreview: (MediaUploadSelection) -> Unit = {},
    acceptedTypes: Array<String> = arrayOf("image/*"),
) {
    require(maxConcurrentUploads > 0)
    require(maxSelectionCount > 0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(api, accessToken, purpose) {
        MediaUploadManager(api, accessToken, purpose, onAuthenticationRequired)
    }
    val uploadItems by manager.items.collectAsState()
    val jobs = remember { mutableStateMapOf<String, Job>() }
    val uploadPermits = remember(maxConcurrentUploads) { Semaphore(maxConcurrentUploads) }
    var confirmDismiss by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val acceptedTypeLabel = remember(acceptedTypes.contentHashCode()) { acceptedTypeSummary(acceptedTypes) }

    fun hasActiveUploads() = uploadItems.any {
        it.phase in setOf(
            MediaUploadPhase.QUEUED,
            MediaUploadPhase.PREPARING,
            MediaUploadPhase.UPLOADING,
            MediaUploadPhase.VERIFYING,
            MediaUploadPhase.DELETING,
        )
    }

    fun requestDismiss() {
        if (hasActiveUploads()) confirmDismiss = true else onDismiss()
    }

    fun enqueue(uris: List<Uri>) {
        val remaining = remainingSelectionCapacity(maxSelectionCount, uploadItems.size)
        uris.take(remaining).mapNotNull { uri -> context.contentResolver.toMediaLocalFile(uri) }.forEach { file ->
            val id = manager.add(file)
            jobs[id] = scope.launch {
                try {
                    uploadPermits.withPermit { manager.upload(id) }
                } finally {
                    jobs.remove(id)
                }
            }
        }
    }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        enqueue(uri?.let(::listOf).orEmpty())
    }
    val multiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        enqueue(uris)
    }

    ModalBottomSheet(onDismissRequest = ::requestDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = HhyColors.SoftBlue,
                shape = RoundedCornerShape(HhyRadius.LargeCard),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    Surface(shape = CircleShape, color = HhyColors.Surface) {
                        HhyIcon(
                            HhyIcons.Camera,
                            contentDescription = null,
                            modifier = Modifier.padding(HhySpacing.Md),
                            tint = HhyColors.BrandPrimary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                        Text(
                            "添加文件",
                            fontSize = HhyType.PageTitleSize,
                            lineHeight = HhyType.PageTitleLineHeight,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(acceptedTypeLabel, color = HhyColors.BrandPrimary, fontSize = HhyType.CaptionSize)
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = HhyColors.PageBackground,
                shape = RoundedCornerShape(HhyRadius.Tag),
            ) {
                Text(
                    "选择文件后会自动上传。上传完成前可以取消，失败的文件可以单独重试。",
                    modifier = Modifier.padding(HhySpacing.Md),
                    fontSize = HhyType.BodySize,
                    lineHeight = HhyType.BodyLineHeight,
                    color = HhyColors.TextSecondary,
                )
            }
            if (uploadItems.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(HhyRadius.LargeCard),
                    colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                    ) {
                        Surface(shape = CircleShape, color = HhyColors.SoftBlue) {
                            HhyIcon(
                                HhyIcons.Camera,
                                contentDescription = null,
                                modifier = Modifier.padding(HhySpacing.Md),
                                tint = HhyColors.BrandPrimary,
                            )
                        }
                        Text("还没有选择文件", fontWeight = FontWeight.SemiBold, color = HhyColors.TextPrimary)
                        Text("点击下方按钮，从设备中选择文件", color = HhyColors.TextSecondary)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("已选择 ${uploadItems.size} 个文件", fontWeight = FontWeight.Medium)
                    Text(
                        "已完成 ${manager.completedSelections().size} 个",
                        color = HhyColors.TextSecondary,
                        fontSize = HhyType.CaptionSize,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                ) {
                    items(uploadItems, key = { it.localId }) { item ->
                        UploadItemCard(
                            item = item,
                            onCancel = { jobs.remove(item.localId)?.cancel() },
                            onRetry = {
                                jobs[item.localId] = scope.launch {
                                    try { uploadPermits.withPermit { manager.retry(item.localId) } }
                                    finally { jobs.remove(item.localId) }
                                }
                            },
                            onPreview = manager.completedSelection(item.localId)?.takeIf { it.readUrl != null }?.let { selection ->
                                { onPreview(selection) }
                            },
                            onRemove = {
                                if (item.phase == MediaUploadPhase.COMPLETED) deleteTarget = item.localId
                                else manager.removeLocal(item.localId)
                            },
                        )
                    }
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                enabled = !hasActiveUploads() && remainingSelectionCapacity(maxSelectionCount, uploadItems.size) > 0,
                onClick = {
                    if (usesSingleSelectionContract(maxSelectionCount)) singlePicker.launch(acceptedTypes)
                    else multiplePicker.launch(acceptedTypes)
                },
            ) { Text(selectionButtonLabel(maxSelectionCount, uploadItems.size)) }
            Button(
                modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                enabled = manager.completedSelections().isNotEmpty() && !hasActiveUploads(),
                onClick = {
                    val completed = manager.completedSelections()
                    check(completed.size <= maxSelectionCount)
                    onCompleted(completed)
                },
            ) { Text("完成") }
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = ::requestDismiss) { Text("取消") }
            Spacer(Modifier.height(HhySpacing.Md))
        }
    }

    if (confirmDismiss) {
        AlertDialog(
            onDismissRequest = { confirmDismiss = false },
            title = { Text("退出上传？") },
            text = { Text("尚未完成的上传会被取消，已上传成功的文件会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    jobs.values.toList().forEach(Job::cancel)
                    confirmDismiss = false
                    onDismiss()
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { confirmDismiss = false }) { Text("继续上传") } },
        )
    }

    deleteTarget?.let { localId ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个文件？") },
            text = { Text("删除后需要重新选择并上传。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch { manager.delete(localId) }
                }) { Text("删除", color = HhyColors.Error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("保留") } },
        )
    }
}

@Composable
private fun UploadItemCard(
    item: MediaUploadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPreview: (() -> Unit)?,
) {
    val progress = if (item.sizeBytes == 0L) 0f else item.uploadedBytes.toFloat() / item.sizeBytes
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.displayName, modifier = Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Medium)
                Surface(
                    color = phaseColor(item.phase).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(HhyRadius.Tag),
                ) {
                    Text(
                        phaseLabel(item.phase),
                        modifier = Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs),
                        color = phaseColor(item.phase),
                        fontSize = HhyType.CaptionSize,
                    )
                }
            }
            Text(formatBytes(item.sizeBytes), color = HhyColors.TextSecondary, fontSize = HhyType.CaptionSize)
            if (item.phase in setOf(MediaUploadPhase.PREPARING, MediaUploadPhase.UPLOADING, MediaUploadPhase.VERIFYING)) {
                LinearProgressIndicator(
                    progress = { if (item.phase == MediaUploadPhase.UPLOADING) progress.coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item.message?.let { Text(it, color = HhyColors.Error, fontSize = HhyType.SecondaryBodySize) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (item.phase) {
                    MediaUploadPhase.QUEUED, MediaUploadPhase.PREPARING, MediaUploadPhase.UPLOADING,
                    MediaUploadPhase.VERIFYING -> TextButton(onClick = onCancel) { Text("取消") }
                    MediaUploadPhase.FAILED, MediaUploadPhase.CANCELLED -> {
                        TextButton(onClick = onRemove) { Text("移除") }
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                    MediaUploadPhase.COMPLETED -> {
                        onPreview?.let { TextButton(onClick = it) { Text("预览") } }
                        TextButton(onClick = onRemove) { Text("删除") }
                    }
                    MediaUploadPhase.DELETING -> Unit
                }
            }
        }
    }
}

private fun phaseLabel(phase: MediaUploadPhase): String = when (phase) {
    MediaUploadPhase.QUEUED -> "等待上传"
    MediaUploadPhase.PREPARING -> "正在检查"
    MediaUploadPhase.UPLOADING -> "正在上传"
    MediaUploadPhase.VERIFYING -> "正在完成"
    MediaUploadPhase.COMPLETED -> "已完成"
    MediaUploadPhase.FAILED -> "上传失败"
    MediaUploadPhase.CANCELLED -> "已取消"
    MediaUploadPhase.DELETING -> "正在删除"
}

private fun phaseColor(phase: MediaUploadPhase) = when (phase) {
    MediaUploadPhase.COMPLETED -> HhyColors.Success
    MediaUploadPhase.FAILED -> HhyColors.Error
    MediaUploadPhase.CANCELLED -> HhyColors.TextSecondary
    else -> HhyColors.BrandPrimary
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

internal fun acceptedTypeSummary(acceptedTypes: Array<String>): String {
    if (acceptedTypes.any { it == "*/*" }) return "支持当前业务允许的文件类型"
    val labels = buildList {
        if (acceptedTypes.any { it.startsWith("image/") }) add("图片")
        if (acceptedTypes.any { it.startsWith("video/") }) add("视频")
        if (acceptedTypes.any { it.startsWith("audio/") }) add("音频")
    }
    return if (labels.isEmpty()) "支持当前业务允许的文件类型" else "支持${labels.joinToString("、")}文件"
}

internal fun usesSingleSelectionContract(maxSelectionCount: Int): Boolean = maxSelectionCount == 1

internal fun remainingSelectionCapacity(maxSelectionCount: Int, selectedCount: Int): Int {
    require(maxSelectionCount > 0)
    require(selectedCount >= 0)
    return (maxSelectionCount - selectedCount).coerceAtLeast(0)
}

internal fun selectionButtonLabel(maxSelectionCount: Int, selectedCount: Int): String = when {
    usesSingleSelectionContract(maxSelectionCount) && selectedCount == 0 -> "选择文件"
    usesSingleSelectionContract(maxSelectionCount) -> "已选择文件"
    else -> "继续选择文件"
}

private fun ContentResolver.toMediaLocalFile(uri: Uri): MediaLocalFile? {
    var name: String? = null
    var size = -1L
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
        }
    }
    if (size <= 0L) size = openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    val type = getType(uri) ?: "application/octet-stream"
    val safeName = name?.takeIf(String::isNotBlank) ?: return null
    if (size <= 0L) return null
    return MediaLocalFile(safeName, type, size) {
        openInputStream(uri) ?: error("Selected file is no longer readable")
    }
}
