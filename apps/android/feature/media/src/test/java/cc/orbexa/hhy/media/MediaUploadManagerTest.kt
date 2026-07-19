package cc.orbexa.hhy.media

import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.DirectUploadResource
import cc.orbexa.hhy.network.MediaCallResult
import cc.orbexa.hhy.network.MediaCompleteUploadSessionRequest
import cc.orbexa.hhy.network.MediaCreateUploadSessionRequest
import cc.orbexa.hhy.network.MediaResource
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUploadManagerTest {
    private val bytes = "contract-media".toByteArray()

    @Test
    fun `upload completes and keeps technical values out of visible state`() = runBlocking {
        val api = FakeMediaApi()
        val manager = manager(api)
        val id = manager.add(file())

        manager.upload(id)

        val item = manager.items.value.single()
        assertEquals(MediaUploadPhase.COMPLETED, item.phase)
        assertEquals(bytes.size.toLong(), item.uploadedBytes)
        assertEquals(1, api.createKeys.size)
        assertEquals(1, api.completeKeys.size)
        assertFalse(item.toString().contains("https://"))
        assertEquals("media-1", manager.completedSelections().single().mediaId)
    }

    @Test
    fun `retry reuses stable create idempotency key`() = runBlocking {
        val api = FakeMediaApi(createFailures = 1)
        val manager = manager(api)
        val id = manager.add(file())

        manager.upload(id)
        assertEquals(MediaUploadPhase.FAILED, manager.items.value.single().phase)
        manager.retry(id)

        assertEquals(MediaUploadPhase.COMPLETED, manager.items.value.single().phase)
        assertEquals(2, api.createKeys.size)
        assertEquals(api.createKeys.first(), api.createKeys.last())
    }

    @Test
    fun `completion retry reuses stable completion idempotency key`() = runBlocking {
        val api = FakeMediaApi(completeFailures = 1)
        val manager = manager(api)
        val id = manager.add(file())

        manager.upload(id)
        assertEquals(MediaUploadPhase.FAILED, manager.items.value.single().phase)
        manager.retry(id)

        assertEquals(MediaUploadPhase.COMPLETED, manager.items.value.single().phase)
        assertEquals(2, api.completeKeys.size)
        assertEquals(api.completeKeys.first(), api.completeKeys.last())
    }

    @Test
    fun `delete is explicit and is not automatically retried`() = runBlocking {
        val api = FakeMediaApi(deleteFailures = 1)
        val manager = manager(api)
        val id = manager.add(file())
        manager.upload(id)

        assertFalse(manager.delete(id))

        assertEquals(1, api.deleteCalls)
        assertEquals(MediaUploadPhase.FAILED, manager.items.value.single().phase)
        assertEquals("文件状态已发生变化，请重试", manager.items.value.single().message)
        manager.retry(id)
        assertEquals(2, api.deleteCalls)
        assertEquals(api.deleteKeys.first(), api.deleteKeys.last())
        assertTrue(manager.items.value.isEmpty())
    }

    @Test
    fun `cancellation becomes recoverable cancelled state`() = runBlocking {
        val api = FakeMediaApi(cancelUpload = true)
        val manager = manager(api)
        val id = manager.add(file())

        runCatching { manager.upload(id) }

        assertEquals(MediaUploadPhase.CANCELLED, manager.items.value.single().phase)
        assertEquals("上传已取消", manager.items.value.single().message)
    }

    @Test
    fun `frozen failures have actionable commercial messages`() {
        assertEquals("网络连接不可用，请检查网络后重试", message(null))
        assertEquals("登录状态已失效，请重新登录", message(401))
        assertEquals("当前账号没有执行此操作的权限", message(403))
        assertEquals("上传任务已失效，请重试", message(404))
        assertEquals("文件状态已发生变化，请重试", message(409))
        assertEquals("文件不符合上传要求，请重新选择", message(422))
        assertEquals(
            "操作过于频繁，请在 9 秒后重试",
            MediaErrorMessages.forFailure(MediaCallResult.Failure(429, retryAfterSeconds = 9), MediaFailureStage.CREATE),
        )
        assertEquals("服务暂时无法处理，请稍后重试", message(500))
    }

    private fun message(status: Int?) = MediaErrorMessages.forFailure(
        MediaCallResult.Failure(status), MediaFailureStage.CREATE,
    )

    private fun manager(api: ContractMediaApi) = MediaUploadManager(
        api = api,
        accessToken = "access-token",
        purpose = "public_media",
        keyFactory = { "12345678-1234-1234-1234-123456789012" },
    )

    private fun file() = MediaLocalFile(
        displayName = "cover.jpg",
        contentType = "image/jpeg",
        sizeBytes = bytes.size.toLong(),
        openInput = { ByteArrayInputStream(bytes) },
    )

    private class FakeMediaApi(
        private var createFailures: Int = 0,
        private var completeFailures: Int = 0,
        private var deleteFailures: Int = 0,
        private val cancelUpload: Boolean = false,
    ) : ContractMediaApi {
        val createKeys = mutableListOf<String>()
        val completeKeys = mutableListOf<String>()
        val deleteKeys = mutableListOf<String>()
        var deleteCalls = 0

        override suspend fun createUploadSession(
            accessToken: String,
            idempotencyKey: String,
            request: MediaCreateUploadSessionRequest,
        ): MediaCallResult<MediaResource> {
            createKeys += idempotencyKey
            if (createFailures-- > 0) return MediaCallResult.Failure(null)
            return MediaCallResult.Success(
                MediaResource(
                    id = "session-1",
                    purpose = request.purpose,
                    contentType = request.contentType,
                    sizeBytes = request.sizeBytes,
                    sha256 = request.sha256,
                    uploadUrl = "https://upload.example.test/signed",
                    status = "CREATED",
                ),
            )
        }

        override suspend fun upload(
            uploadUrl: String,
            contentType: String,
            sizeBytes: Long,
            input: () -> InputStream,
            onProgress: (uploadedBytes: Long) -> Unit,
        ): MediaCallResult<DirectUploadResource> {
            if (cancelUpload) throw CancellationException("cancel test")
            input().use { it.readBytes() }
            onProgress(sizeBytes)
            return MediaCallResult.Success(DirectUploadResource("etag-1"))
        }

        override suspend fun completeUploadSession(
            accessToken: String,
            sessionId: String,
            idempotencyKey: String,
            request: MediaCompleteUploadSessionRequest,
        ): MediaCallResult<MediaResource> {
            completeKeys += idempotencyKey
            if (completeFailures-- > 0) return MediaCallResult.Failure(409, "COMMON-409-VERSION_CONFLICT")
            return MediaCallResult.Success(
                MediaResource(
                    id = "media-1",
                    contentType = "image/jpeg",
                    sizeBytes = 14,
                    status = "READY",
                    readUrl = "https://read.example.test/private",
                ),
            )
        }

        override suspend fun deleteMedia(
            accessToken: String,
            mediaId: String,
            idempotencyKey: String,
        ): MediaCallResult<CommandResultResource> {
            deleteCalls += 1
            deleteKeys += idempotencyKey
            if (deleteFailures-- > 0) return MediaCallResult.Failure(409, "COMMON-409-VERSION_CONFLICT")
            return MediaCallResult.Success(
                CommandResultResource(mediaId, status = "DELETE_PENDING", acceptedAt = "2026-07-19T00:00:00Z"),
            )
        }
    }
}
