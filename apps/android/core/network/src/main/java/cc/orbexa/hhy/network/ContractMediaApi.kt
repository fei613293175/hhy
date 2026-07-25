package cc.orbexa.hhy.network

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString

interface ContractMediaApi {
    suspend fun createUploadSession(
        accessToken: String,
        idempotencyKey: String,
        request: MediaCreateUploadSessionRequest,
    ): MediaCallResult<MediaResource>

    suspend fun upload(
        uploadUrl: String,
        contentType: String,
        sizeBytes: Long,
        input: () -> InputStream,
        onProgress: (uploadedBytes: Long) -> Unit,
    ): MediaCallResult<DirectUploadResource>

    suspend fun completeUploadSession(
        accessToken: String,
        sessionId: String,
        idempotencyKey: String,
        request: MediaCompleteUploadSessionRequest,
    ): MediaCallResult<MediaResource>

    suspend fun deleteMedia(
        accessToken: String,
        mediaId: String,
        idempotencyKey: String,
    ): MediaCallResult<CommandResultResource>
}

data class DirectUploadResource(val etag: String)

sealed interface MediaCallResult<out T> {
    data class Success<T>(val data: T) : MediaCallResult<T>
    data class Failure(
        val statusCode: Int?,
        val errorCode: String? = null,
        val retryAfterSeconds: Long? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : MediaCallResult<Nothing>
}

class UrlConnectionContractMediaApi(baseUrl: String) : ContractMediaApi {
    private val root = validateRoot(baseUrl)

    override suspend fun createUploadSession(
        accessToken: String,
        idempotencyKey: String,
        request: MediaCreateUploadSessionRequest,
    ) = jsonRequest(
        method = "POST",
        path = "/api/v1/media/upload-sessions",
        accessToken = accessToken,
        idempotencyKey = idempotencyKey,
        body = request,
        bodySerializer = MediaCreateUploadSessionRequest.serializer(),
        responseSerializer = MediaResource.serializer(),
    )

    override suspend fun completeUploadSession(
        accessToken: String,
        sessionId: String,
        idempotencyKey: String,
        request: MediaCompleteUploadSessionRequest,
    ) = jsonRequest(
        method = "POST",
        path = "/api/v1/media/upload-sessions/${safeId(sessionId)}/complete",
        accessToken = accessToken,
        idempotencyKey = idempotencyKey,
        body = request,
        bodySerializer = MediaCompleteUploadSessionRequest.serializer(),
        responseSerializer = MediaResource.serializer(),
    )

    override suspend fun deleteMedia(
        accessToken: String,
        mediaId: String,
        idempotencyKey: String,
    ) = jsonRequest(
        method = "DELETE",
        path = "/api/v1/media/${safeId(mediaId)}",
        accessToken = accessToken,
        idempotencyKey = idempotencyKey,
        body = null,
        bodySerializer = null,
        responseSerializer = CommandResultResource.serializer(),
    )

    override suspend fun upload(
        uploadUrl: String,
        contentType: String,
        sizeBytes: Long,
        input: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): MediaCallResult<DirectUploadResource> = withContext(Dispatchers.IO) {
        try {
            val uri = URI.create(uploadUrl)
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null)
            val connection = uri.toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "PUT"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = UPLOAD_TIMEOUT_MILLIS
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(sizeBytes)
                connection.setRequestProperty("Content-Type", contentType)
                input().use { source ->
                    connection.outputStream.use { sink ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var uploaded = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            sink.write(buffer, 0, count)
                            uploaded += count
                            onProgress(uploaded)
                        }
                    }
                }
                val status = connection.responseCode
                if (status !in 200..299) return@withContext failure(connection, status)
                val etag = connection.getHeaderField("ETag")?.trim()?.trim('"')
                    ?.takeIf(String::isNotBlank)
                    ?: return@withContext MediaCallResult.Failure(422, "COMMON-422-BUSINESS_RULE")
                MediaCallResult.Success(DirectUploadResource(etag))
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MediaCallResult.Failure(null)
        }
    }

    private suspend fun <B, R> jsonRequest(
        method: String,
        path: String,
        accessToken: String,
        idempotencyKey: String,
        body: B?,
        bodySerializer: KSerializer<B>?,
        responseSerializer: KSerializer<R>,
    ): MediaCallResult<R> = withContext(Dispatchers.IO) {
        try {
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                connection.setRequestProperty("X-Idempotency-Key", requireKey(idempotencyKey))
                if (body != null && bodySerializer != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(HhyNetworkJson.value.encodeToString(bodySerializer, body))
                    }
                }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) return@withContext failure(connection, status, text)
                val envelope = HhyNetworkJson.value.decodeFromString(
                    ApiEnvelope.serializer(responseSerializer), text,
                )
                MediaCallResult.Success(envelope.data)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MediaCallResult.Failure(null)
        }
    }

    private fun failure(
        connection: HttpURLConnection,
        status: Int,
        body: String = "",
    ): MediaCallResult.Failure {
        val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()
        return MediaCallResult.Failure(
            statusCode = status,
            errorCode = error?.error?.code,
            retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
            fieldErrors = error?.error?.fieldErrors().orEmpty(),
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val UPLOAD_TIMEOUT_MILLIS = 60_000
        const val BUFFER_SIZE = 64 * 1024
        private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun validateRoot(value: String): String {
            val uri = URI.create(value.trim())
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null)
            return value.trim().trimEnd('/')
        }

        fun safeId(value: String): String = value.also { require(SAFE_ID.matches(it)) }
        fun requireKey(value: String): String = value.also { require(it.length in 16..128) }
    }
}
