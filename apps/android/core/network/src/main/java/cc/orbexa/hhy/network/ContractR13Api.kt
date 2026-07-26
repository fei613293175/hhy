package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class ContentPostContentsByIdShareRequest(val channel: String)

@Serializable
data class ShareResultResource(
    val contentId: String,
    val channel: String,
    val url: String,
    val acceptedAt: String,
)

@Serializable
data class ContentPostContentsByIdInvalidFeedbackRequest(
    val reasonCode: String,
    val description: String? = null,
)

/** Generated-contract-shaped transport for the five frozen R13 client operations. */
interface ContractR13Api {
    suspend fun favorites(
        accessToken: String,
        cursor: String? = null,
        pageSize: Int = 20,
        status: String? = null,
        keyword: String? = null,
        sort: String = "createdAt:desc",
    ): R07CallResult<ContentPageResource>

    suspend fun history(
        accessToken: String,
        cursor: String? = null,
        pageSize: Int = 20,
        status: String? = null,
        keyword: String? = null,
        sort: String = "createdAt:desc",
    ): R07CallResult<ContentPageResource>

    suspend fun unfavorite(
        accessToken: String,
        id: String,
        idempotencyKey: String,
    ): R07CallResult<CommandResultResource>

    suspend fun share(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: ContentPostContentsByIdShareRequest,
    ): R07CallResult<ShareResultResource>

    suspend fun invalidFeedback(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: ContentPostContentsByIdInvalidFeedbackRequest,
    ): R07CallResult<CommandResultResource>
}

class UrlConnectionContractR13Api(baseUrl: String) : ContractR13Api {
    private val root = validateR13Root(baseUrl)

    override suspend fun favorites(
        accessToken: String,
        cursor: String?,
        pageSize: Int,
        status: String?,
        keyword: String?,
        sort: String,
    ) = activityPage("/api/v1/me/favorites", accessToken, cursor, pageSize, status, keyword, sort)

    override suspend fun history(
        accessToken: String,
        cursor: String?,
        pageSize: Int,
        status: String?,
        keyword: String?,
        sort: String,
    ) = activityPage("/api/v1/me/history", accessToken, cursor, pageSize, status, keyword, sort)

    override suspend fun unfavorite(accessToken: String, id: String, idempotencyKey: String) = call(
        method = "DELETE",
        route = "/api/v1/contents/${safeR13Id(id)}/favorite",
        accessToken = accessToken,
        serializer = CommandResultResource.serializer(),
        idempotencyKey = requireR13Key(idempotencyKey),
    )

    override suspend fun share(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: ContentPostContentsByIdShareRequest,
    ): R07CallResult<ShareResultResource> {
        require(request.channel in SHARE_CHANNELS)
        return call(
            "POST",
            "/api/v1/contents/${safeR13Id(id)}/share",
            accessToken,
            ShareResultResource.serializer(),
            requireR13Key(idempotencyKey),
            encodeBody(request),
        )
    }

    override suspend fun invalidFeedback(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: ContentPostContentsByIdInvalidFeedbackRequest,
    ): R07CallResult<CommandResultResource> {
        require(request.reasonCode.isNotBlank() && request.reasonCode.length <= 2000)
        require(request.description == null || request.description.length <= 2000)
        return call(
            "POST",
            "/api/v1/contents/${safeR13Id(id)}/invalid-feedback",
            accessToken,
            CommandResultResource.serializer(),
            requireR13Key(idempotencyKey),
            encodeBody(request),
        )
    }

    private suspend fun activityPage(
        route: String,
        accessToken: String,
        cursor: String?,
        pageSize: Int,
        status: String?,
        keyword: String?,
        sort: String,
    ): R07CallResult<ContentPageResource> {
        require(pageSize in 1..100)
        require(cursor == null || cursor.length <= 256)
        require(status == null || status.length <= 64)
        require(keyword == null || keyword.length <= 100)
        require(sort in ACTIVITY_SORTS)
        return call(
            "GET",
            query(route, listOf(
                "pageSize" to pageSize.toString(),
                "cursor" to cursor?.takeIf(String::isNotBlank),
                "status" to status?.takeIf(String::isNotBlank),
                "keyword" to keyword?.takeIf(String::isNotBlank),
                "sort" to sort,
            )),
            accessToken,
            ContentPageResource.serializer(),
        )
    }

    private suspend fun <T> call(
        method: String,
        route: String,
        accessToken: String,
        serializer: KSerializer<T>,
        idempotencyKey: String? = null,
        body: String? = null,
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
                val statusCode = connection.responseCode
                val text = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (statusCode !in 200..299) return@withContext failure(connection, statusCode, text)
                val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), text)
                R07CallResult.Success(envelope.data, envelope.requestId)
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
            fieldErrors = error?.error?.fieldErrors().orEmpty(),
            retryable = error?.error?.retryable ?: (status >= 500),
        )
    }

    private fun query(route: String, values: List<Pair<String, String?>>): String {
        val value = values.mapNotNull { (key, item) -> item?.let { "${encode(key)}=${encode(it)}" } }.joinToString("&")
        return if (value.isEmpty()) route else "$route?$value"
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    private inline fun <reified T> encodeBody(value: T) = HhyNetworkJson.value.encodeToString(value)

    private companion object {
        val ACTIVITY_SORTS = setOf("createdAt:desc", "createdAt:asc")
        val SHARE_CHANNELS = setOf("WECHAT", "WECHAT_MOMENTS", "COPY_LINK", "OTHER")
    }
}

private fun validateR13Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.fragment == null && uri.query == null)
    return uri.toString().trimEnd('/')
}

private fun safeR13Id(value: String): String = value.also {
    require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it))
}

private fun requireR13Key(value: String): String = value.also {
    require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it))
}
