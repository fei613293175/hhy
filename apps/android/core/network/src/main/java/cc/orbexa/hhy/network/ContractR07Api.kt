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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** One-to-one Android transport for the seven frozen R07 client operations. */
interface ContractR07Api {
    suspend fun search(
        accessToken: String,
        query: String,
        contentType: String? = null,
        categoryCode: String? = null,
        regionCode: String? = null,
        cursor: String? = null,
        pageSize: Int = 20,
        sort: String = "relevance:desc",
    ): R07CallResult<SearchResultPageResource>

    suspend fun hot(accessToken: String, pageSize: Int = 20): R07CallResult<SearchTermPageResource>
    suspend fun history(accessToken: String, pageSize: Int = 20): R07CallResult<SearchTermPageResource>
    suspend fun clearHistory(accessToken: String, idempotencyKey: String): R07CallResult<CommandResultResource>
    suspend fun publisher(accessToken: String, publisherId: String): R07CallResult<PublisherSummaryResource>
    suspend fun contents(
        accessToken: String,
        publisherId: String,
        cursor: String? = null,
        pageSize: Int = 20,
    ): R07CallResult<ContentPageResource>

    suspend fun accessContact(
        accessToken: String,
        contentId: String,
        channel: String,
        idempotencyKey: String,
        request: ContactAccessRequest = ContactAccessRequest(),
    ): R07CallResult<ContactAccessResource>
}

sealed interface R07CallResult<out T> {
    data class Success<T>(
        val data: T,
        val requestId: String,
        val timestamp: String? = null,
    ) : R07CallResult<T>
    data class Failure(
        val statusCode: Int?,
        val errorCode: String? = null,
        val requestId: String? = null,
        val retryAfterSeconds: Long? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
        val retryable: Boolean = statusCode == null || statusCode >= 500,
    ) : R07CallResult<Nothing>
}

class UrlConnectionContractR07Api(baseUrl: String) : ContractR07Api {
    private val root = validateR07Root(baseUrl)

    override suspend fun search(
        accessToken: String,
        query: String,
        contentType: String?,
        categoryCode: String?,
        regionCode: String?,
        cursor: String?,
        pageSize: Int,
        sort: String,
    ): R07CallResult<SearchResultPageResource> {
        val q = query.trim().also { require(it.length in 1..100) }
        require(pageSize in 1..100)
        contentType?.let { require(it in CONTENT_TYPES) }
        require(sort in SEARCH_SORTS)
        return call(
            "GET",
            path("/api/v1/search", listOf(
                "q" to q,
                "contentType" to contentType,
                "categoryCode" to categoryCode?.takeIf { it.length <= 64 },
                "regionCode" to regionCode?.takeIf { it.length <= 32 },
                "cursor" to cursor?.takeIf { it.length <= 256 },
                "pageSize" to pageSize.toString(),
                "sort" to sort,
            )),
            accessToken,
            SearchResultPageResource.serializer(),
        )
    }

    override suspend fun hot(accessToken: String, pageSize: Int): R07CallResult<SearchTermPageResource> {
        require(pageSize in 1..100)
        return call("GET", path("/api/v1/search/hot", listOf("pageSize" to pageSize.toString())), accessToken, SearchTermPageResource.serializer())
    }

    override suspend fun history(accessToken: String, pageSize: Int): R07CallResult<SearchTermPageResource> {
        require(pageSize in 1..100)
        return call("GET", path("/api/v1/search/history", listOf("pageSize" to pageSize.toString())), accessToken, SearchTermPageResource.serializer())
    }

    override suspend fun clearHistory(accessToken: String, idempotencyKey: String) = call(
        method = "DELETE",
        path = "/api/v1/search/history",
        accessToken = accessToken,
        serializer = CommandResultResource.serializer(),
        idempotencyKey = requireR07Key(idempotencyKey),
    )

    override suspend fun publisher(accessToken: String, publisherId: String) = call(
        "GET",
        "/api/v1/publishers/${safeR07Id(publisherId)}",
        accessToken,
        PublisherSummaryResource.serializer(),
    )

    override suspend fun contents(
        accessToken: String,
        publisherId: String,
        cursor: String?,
        pageSize: Int,
    ): R07CallResult<ContentPageResource> {
        require(pageSize in 1..100)
        return call(
            "GET",
            path("/api/v1/contents", listOf(
                "publisherId" to safeR07Id(publisherId),
                "status" to "ONLINE",
                "cursor" to cursor?.takeIf { it.length <= 256 },
                "pageSize" to pageSize.toString(),
                "sort" to "createdAt:desc",
            )),
            accessToken,
            ContentPageResource.serializer(),
        )
    }

    override suspend fun accessContact(
        accessToken: String,
        contentId: String,
        channel: String,
        idempotencyKey: String,
        request: ContactAccessRequest,
    ) = call(
        method = "POST",
        path = "/api/v1/contents/${safeR07Id(contentId)}/contacts/${safeR07Channel(channel)}/access",
        accessToken = accessToken,
        serializer = ContactAccessResource.serializer(),
        idempotencyKey = requireR07Key(idempotencyKey),
        body = HhyNetworkJson.value.encodeToString(ContactAccessRequest.serializer(), request),
        requireNoStore = true,
    )

    private suspend fun <T> call(
        method: String,
        path: String,
        accessToken: String,
        serializer: KSerializer<T>,
        idempotencyKey: String? = null,
        body: String? = null,
        requireNoStore: Boolean = false,
    ): R07CallResult<T> = withContext(Dispatchers.IO) {
        try {
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
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
                val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) return@withContext failure(connection, status, responseText)
                if (requireNoStore && !connection.getHeaderField("Cache-Control").orEmpty().contains("no-store", true)) {
                    return@withContext R07CallResult.Failure(status, "CLIENT-SENSITIVE-CACHE_POLICY", retryable = false)
                }
                val envelopeSerializer = ApiEnvelope.serializer(serializer)
                val envelope = HhyNetworkJson.value.decodeFromString(envelopeSerializer, responseText)
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

    private fun path(route: String, values: List<Pair<String, String?>>): String {
        val query = values.mapNotNull { (key, value) ->
            value?.let { "${encode(key)}=${encode(it)}" }
        }.joinToString("&")
        return if (query.isEmpty()) route else "$route?$query"
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 15_000
        val CONTENT_TYPES = setOf("PROJECT", "APP", "GROUP_CHAT", "TEAM_LEADER")
        val SEARCH_SORTS = setOf("relevance:desc", "createdAt:desc", "id:desc")
    }
}

private fun validateR07Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.fragment == null && uri.query == null)
    return uri.toString().trimEnd('/')
}

private fun safeR07Id(value: String): String = value.also {
    require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it))
}

private fun safeR07Channel(value: String): String = value.also {
    require(it in setOf("WECHAT", "PHONE", "QQ", "EMAIL", "LINK", "QR_CODE"))
}

private fun requireR07Key(value: String): String = value.also {
    require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it))
}
