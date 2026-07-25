package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class R12CopyContentRequest(
    val expectedVersion: Long,
    val reason: String? = null,
)

sealed interface R12CopyContentResult {
    data class Content(val resource: ContentResource) : R12CopyContentResult
    data class Command(val command: CommandResultResource) : R12CopyContentResult
}

interface ContractR12Api {
    suspend fun content(accessToken: String, id: String): R07CallResult<ContentResource>
    suspend fun copy(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12CopyContentRequest,
    ): R07CallResult<R12CopyContentResult>
    suspend fun analytics(
        accessToken: String,
        id: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): R07CallResult<ContentPageResource>
}

class UrlConnectionContractR12Api(baseUrl: String) : ContractR12Api {
    private val root = validateR12Root(baseUrl)

    override suspend fun content(accessToken: String, id: String) = call(
        method = "GET",
        route = "/api/v1/contents/${safeR12Id(id)}",
        accessToken = accessToken,
        serializer = ContentResource.serializer(),
    )

    override suspend fun copy(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12CopyContentRequest,
    ): R07CallResult<R12CopyContentResult> {
        require(request.expectedVersion >= 0)
        require(request.reason == null || request.reason.length <= 2000)
        return copyCall(
            route = "/api/v1/contents/${safeR12Id(id)}/copy",
            accessToken = accessToken,
            idempotencyKey = requireR12Key(idempotencyKey),
            body = HhyNetworkJson.value.encodeToString(request),
        )
    }

    override suspend fun analytics(
        accessToken: String,
        id: String,
        page: Int,
        pageSize: Int,
    ): R07CallResult<ContentPageResource> {
        require(page >= 1)
        require(pageSize in 1..100)
        val route = "/api/v1/contents/${safeR12Id(id)}/analytics?page=$page&pageSize=$pageSize"
        return call("GET", route, accessToken, ContentPageResource.serializer())
    }

    private suspend fun <T> call(
        method: String,
        route: String,
        accessToken: String,
        serializer: KSerializer<T>,
    ): R07CallResult<T> = execute(method, route, accessToken) { connection, text ->
        val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), text)
        R07CallResult.Success(envelope.data, envelope.requestId)
    }

    private suspend fun copyCall(
        route: String,
        accessToken: String,
        idempotencyKey: String,
        body: String,
    ): R07CallResult<R12CopyContentResult> = execute(
        method = "POST",
        route = route,
        accessToken = accessToken,
        idempotencyKey = idempotencyKey,
        body = body,
    ) { _, text ->
        val envelope = HhyNetworkJson.value.decodeFromString(
            ApiEnvelope.serializer(JsonElement.serializer()),
            text,
        )
        val result = decodeR12CopyData(envelope.data)
        R07CallResult.Success(result, envelope.requestId)
    }

    private suspend fun <T> execute(
        method: String,
        route: String,
        accessToken: String,
        idempotencyKey: String? = null,
        body: String? = null,
        decode: (HttpURLConnection, String) -> R07CallResult<T>,
    ): R07CallResult<T> = withContext(Dispatchers.IO) {
        try {
            val connection = URI.create(root + route).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                idempotencyKey?.let { connection.setRequestProperty("X-Idempotency-Key", it) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status in 200..299) decode(connection, text) else failure(connection, status, text)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            R07CallResult.Failure(null)
        }
    }

    private fun failure(connection: HttpURLConnection, status: Int, body: String): R07CallResult.Failure {
        val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()
        return R07CallResult.Failure(
            statusCode = status,
            errorCode = error?.error?.code,
            requestId = error?.requestId,
            retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
            fieldErrors = error?.error?.details.orEmpty().associate { it.field to it.message },
            retryable = error?.error?.retryable ?: (status >= 500),
        )
    }
}

internal fun decodeR12CopyData(value: JsonElement): R12CopyContentResult {
    val data = value.jsonObject
    return if ("contentType" in data) {
        R12CopyContentResult.Content(
            HhyNetworkJson.value.decodeFromJsonElement(ContentResource.serializer(), data),
        )
    } else {
        R12CopyContentResult.Command(
            HhyNetworkJson.value.decodeFromJsonElement(CommandResultResource.serializer(), data),
        )
    }
}

private fun validateR12Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.fragment == null && uri.query == null)
    return uri.toString().trimEnd('/')
}

private fun safeR12Id(value: String): String = value.also {
    require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it))
}

private fun requireR12Key(value: String): String = value.also {
    require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it))
}
