package cc.orbexa.hhy.media

import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.MediaCallResult
import cc.orbexa.hhy.network.MediaCompleteUploadSessionRequest
import cc.orbexa.hhy.network.MediaCreateUploadSessionRequest
import cc.orbexa.hhy.network.MediaResource
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaLocalFile(
    val displayName: String,
    val contentType: String,
    val sizeBytes: Long,
    val openInput: () -> InputStream,
)

enum class MediaUploadPhase {
    QUEUED, PREPARING, UPLOADING, VERIFYING, COMPLETED, FAILED, CANCELLED, DELETING,
}

data class MediaUploadItem(
    val localId: String,
    val displayName: String,
    val sizeBytes: Long,
    val uploadedBytes: Long,
    val phase: MediaUploadPhase,
    val message: String? = null,
)

data class MediaUploadSelection(
    val mediaId: String,
    val displayName: String,
    val contentType: String,
    val sizeBytes: Long,
    val readUrl: String?,
)

class MediaUploadManager(
    private val api: ContractMediaApi,
    private val accessToken: String,
    private val purpose: String,
    private val onAuthenticationRequired: () -> Unit = {},
    private val keyFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private data class MutableItem(
        val localId: String,
        val file: MediaLocalFile,
        var createKey: String,
        var completeKey: String,
        var deleteKey: String? = null,
        var resource: MediaResource? = null,
        var lastFailureStage: MediaFailureStage? = null,
        var state: MediaUploadItem,
    )

    private val lock = Any()
    private val records = linkedMapOf<String, MutableItem>()
    private val mutableItems = MutableStateFlow<List<MediaUploadItem>>(emptyList())
    val items: StateFlow<List<MediaUploadItem>> = mutableItems.asStateFlow()

    init {
        require(accessToken.isNotBlank())
        require(purpose.isNotBlank())
    }

    fun add(file: MediaLocalFile): String {
        require(file.displayName.isNotBlank())
        require(file.contentType.isNotBlank())
        require(file.sizeBytes > 0)
        val localId = UUID.randomUUID().toString()
        val state = MediaUploadItem(
            localId = localId,
            displayName = file.displayName,
            sizeBytes = file.sizeBytes,
            uploadedBytes = 0,
            phase = MediaUploadPhase.QUEUED,
        )
        synchronized(lock) {
            records[localId] = MutableItem(
                localId, file, stableKey(keyFactory()), stableKey(keyFactory()), state = state,
            )
            publishLocked()
        }
        return localId
    }

    suspend fun upload(localId: String) {
        val item = synchronized(lock) { records[localId] } ?: return
        if (item.state.phase == MediaUploadPhase.COMPLETED || item.state.phase == MediaUploadPhase.DELETING) return
        try {
            val resource = item.resource ?: createSession(item) ?: return
            setState(item, MediaUploadPhase.UPLOADING, uploadedBytes = 0)
            val direct = api.upload(
                uploadUrl = resource.uploadUrl ?: return fail(item, "上传凭证无效，请重新选择文件"),
                contentType = item.file.contentType,
                sizeBytes = item.file.sizeBytes,
                input = item.file.openInput,
            ) { uploaded ->
                setState(item, MediaUploadPhase.UPLOADING, uploadedBytes = uploaded.coerceAtMost(item.file.sizeBytes))
            }
            val uploaded = when (direct) {
                is MediaCallResult.Success -> direct.data
                is MediaCallResult.Failure -> {
                    handleFailure(item, direct, MediaFailureStage.DIRECT_UPLOAD)
                    return
                }
            }
            setState(item, MediaUploadPhase.VERIFYING, uploadedBytes = item.file.sizeBytes)
            when (val completed = api.completeUploadSession(
                accessToken = accessToken,
                sessionId = resource.id,
                idempotencyKey = item.completeKey,
                request = MediaCompleteUploadSessionRequest(etag = uploaded.etag),
            )) {
                is MediaCallResult.Success -> {
                    item.resource = completed.data
                    item.lastFailureStage = null
                    setState(item, MediaUploadPhase.COMPLETED, uploadedBytes = item.file.sizeBytes)
                }
                is MediaCallResult.Failure -> handleFailure(item, completed, MediaFailureStage.COMPLETE)
            }
        } catch (cancelled: CancellationException) {
            setState(item, MediaUploadPhase.CANCELLED, message = "上传已取消")
            throw cancelled
        } catch (_: Exception) {
            fail(item, "文件暂时无法处理，请重试")
        }
    }

    suspend fun retry(localId: String) {
        val item = synchronized(lock) { records[localId] } ?: return
        if (item.state.phase !in setOf(MediaUploadPhase.FAILED, MediaUploadPhase.CANCELLED)) return
        if (item.lastFailureStage == MediaFailureStage.DELETE) delete(localId) else upload(localId)
    }

    suspend fun delete(localId: String): Boolean {
        val item = synchronized(lock) { records[localId] } ?: return true
        val mediaId = item.resource?.id ?: run {
            synchronized(lock) { records.remove(localId); publishLocked() }
            return true
        }
        setState(item, MediaUploadPhase.DELETING)
        val deleteKey = item.deleteKey ?: stableKey(keyFactory()).also { item.deleteKey = it }
        return when (val result = api.deleteMedia(accessToken, mediaId, deleteKey)) {
            is MediaCallResult.Success -> {
                synchronized(lock) { records.remove(localId); publishLocked() }
                true
            }
            is MediaCallResult.Failure -> {
                handleFailure(item, result, MediaFailureStage.DELETE)
                false
            }
        }
    }

    fun removeLocal(localId: String) {
        synchronized(lock) {
            val item = records[localId] ?: return
            if (item.resource == null) {
                records.remove(localId)
                publishLocked()
            }
        }
    }

    fun completedSelections(): List<MediaUploadSelection> = synchronized(lock) {
        records.values.mapNotNull { item ->
            val media = item.resource?.takeIf { item.state.phase == MediaUploadPhase.COMPLETED } ?: return@mapNotNull null
            MediaUploadSelection(media.id, item.file.displayName, item.file.contentType, item.file.sizeBytes, media.readUrl)
        }
    }

    private suspend fun createSession(item: MutableItem): MediaResource? {
        setState(item, MediaUploadPhase.PREPARING, uploadedBytes = 0)
        val sha256 = item.file.openInput().use { source -> sha256(source, item.file.sizeBytes) }
            ?: return fail(item, "文件读取失败，请重新选择").let { null }
        return when (val created = api.createUploadSession(
            accessToken = accessToken,
            idempotencyKey = item.createKey,
            request = MediaCreateUploadSessionRequest(
                purpose = purpose,
                fileName = item.file.displayName,
                contentType = item.file.contentType,
                sizeBytes = item.file.sizeBytes,
                sha256 = sha256,
            ),
        )) {
            is MediaCallResult.Success -> created.data.also { item.resource = it }
            is MediaCallResult.Failure -> {
                handleFailure(item, created, MediaFailureStage.CREATE)
                null
            }
        }
    }

    private fun handleFailure(item: MutableItem, failure: MediaCallResult.Failure, stage: MediaFailureStage) {
        item.lastFailureStage = stage
        if (failure.statusCode == 401) onAuthenticationRequired()
        if (stage == MediaFailureStage.DIRECT_UPLOAD && failure.statusCode in setOf(403, 404)) {
            item.resource = null
            item.createKey = stableKey(keyFactory())
            item.completeKey = stableKey(keyFactory())
        }
        fail(item, MediaErrorMessages.forFailure(failure, stage))
    }

    private fun fail(item: MutableItem, message: String) {
        setState(item, MediaUploadPhase.FAILED, message = message)
    }

    private fun setState(
        item: MutableItem,
        phase: MediaUploadPhase,
        uploadedBytes: Long = item.state.uploadedBytes,
        message: String? = null,
    ) = synchronized(lock) {
        item.state = item.state.copy(phase = phase, uploadedBytes = uploadedBytes, message = message)
        publishLocked()
    }

    private fun publishLocked() {
        mutableItems.value = records.values.map { it.state }
    }

    private fun stableKey(raw: String): String = raw.takeIf { it.length in 16..128 }
        ?: UUID.randomUUID().toString()

    private fun sha256(source: InputStream, expectedSize: Long): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
            total += count
        }
        if (total != expectedSize) return null
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

enum class MediaFailureStage { CREATE, DIRECT_UPLOAD, COMPLETE, DELETE }

object MediaErrorMessages {
    fun forFailure(failure: MediaCallResult.Failure, stage: MediaFailureStage): String = when {
        failure.statusCode == null -> "网络连接不可用，请检查网络后重试"
        failure.statusCode == 401 -> "登录状态已失效，请重新登录"
        failure.statusCode == 403 && stage == MediaFailureStage.DIRECT_UPLOAD -> "上传凭证已失效，请重试"
        failure.statusCode == 403 -> "当前账号没有执行此操作的权限"
        failure.statusCode == 404 -> if (stage == MediaFailureStage.DELETE) "文件已不存在" else "上传任务已失效，请重试"
        failure.statusCode == 409 -> "文件状态已发生变化，请重试"
        failure.statusCode == 422 -> failure.fieldErrors.values.firstOrNull() ?: "文件不符合上传要求，请重新选择"
        failure.statusCode == 429 && failure.retryAfterSeconds != null ->
            "操作过于频繁，请在 ${failure.retryAfterSeconds} 秒后重试"
        failure.statusCode == 429 -> "操作过于频繁，请稍后重试"
        failure.statusCode == 400 -> failure.fieldErrors.values.firstOrNull() ?: "文件信息不符合要求，请重新选择"
        else -> "服务暂时无法处理，请稍后重试"
    }
}
