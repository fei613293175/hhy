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
import kotlinx.serialization.json.JsonObject

@Serializable
data class R09CreateAppRequest(
    val contentType: String,
    val title: String,
    val summary: String? = null,
    val description: String,
    val categoryCode: String,
    val regionCode: String? = null,
    val mediaIds: List<String> = emptyList(),
    val contacts: List<R08ContactInput> = emptyList(),
    val attributes: JsonObject,
)

@Serializable
data class R09PatchAppRequest(
    val title: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val categoryCode: String? = null,
    val regionCode: String? = null,
    val mediaIds: List<String>? = null,
    val contacts: List<R08ContactInput>? = null,
    val attributes: JsonObject? = null,
    val expectedVersion: Long,
)

/** Exact Android transport for the frozen R09 App promotion operations. */
interface ContractR09Api {
    suspend fun apps(accessToken: String, cursor: String? = null, pageSize: Int = 20, sort: String = "createdAt:desc"): R07CallResult<ContentPageResource>
    suspend fun app(accessToken: String, id: String): R07CallResult<ContentResource>
    suspend fun create(accessToken: String, key: String, request: R09CreateAppRequest): R07CallResult<ContentResource>
    suspend fun patch(accessToken: String, id: String, key: String, request: R09PatchAppRequest): R07CallResult<ContentResource>
    suspend fun favorite(accessToken: String, id: String, key: String, request: R08FavoriteRequest): R07CallResult<ContentResource>
    suspend fun share(accessToken: String, id: String, key: String, request: R08ShareRequest): R07CallResult<R08ShareResource>
    suspend fun direct(accessToken: String, key: String, request: R08DirectConversationRequest): R07CallResult<R08ConversationResource>
    suspend fun accessContact(accessToken: String, id: String, channel: String, key: String): R07CallResult<ContactAccessResource>
}

class UrlConnectionContractR09Api(baseUrl: String) : ContractR09Api {
    private val root = validateR09Root(baseUrl)

    override suspend fun apps(accessToken: String, cursor: String?, pageSize: Int, sort: String): R07CallResult<ContentPageResource> {
        require(pageSize in 1..100)
        require(sort in SORTS)
        return call("GET", query("/api/v1/contents", listOf(
            "contentType" to "APP", "status" to "ONLINE", "cursor" to cursor?.takeIf { it.length <= 256 },
            "pageSize" to pageSize.toString(), "sort" to sort,
        )), accessToken, ContentPageResource.serializer())
    }

    override suspend fun app(accessToken: String, id: String) =
        call("GET", "/api/v1/contents/${safeR09Id(id)}", accessToken, ContentResource.serializer())

    override suspend fun create(accessToken: String, key: String, request: R09CreateAppRequest): R07CallResult<ContentResource> {
        validate(request)
        return call("POST", "/api/v1/contents", accessToken, ContentResource.serializer(), requireR09Key(key), encode(request))
    }

    override suspend fun patch(accessToken: String, id: String, key: String, request: R09PatchAppRequest): R07CallResult<ContentResource> {
        require(request.expectedVersion >= 0)
        return call("PATCH", "/api/v1/contents/${safeR09Id(id)}", accessToken, ContentResource.serializer(), requireR09Key(key), encode(request))
    }

    override suspend fun favorite(accessToken: String, id: String, key: String, request: R08FavoriteRequest): R07CallResult<ContentResource> =
        call("POST", "/api/v1/contents/${safeR09Id(id)}/favorite", accessToken, ContentResource.serializer(), requireR09Key(key), encode(request))

    override suspend fun share(accessToken: String, id: String, key: String, request: R08ShareRequest): R07CallResult<R08ShareResource> =
        call("POST", "/api/v1/contents/${safeR09Id(id)}/share", accessToken, R08ShareResource.serializer(), requireR09Key(key), encode(request))

    override suspend fun direct(accessToken: String, key: String, request: R08DirectConversationRequest): R07CallResult<R08ConversationResource> =
        call("POST", "/api/v1/conversations/direct", accessToken, R08ConversationResource.serializer(), requireR09Key(key), encode(request))

    override suspend fun accessContact(accessToken: String, id: String, channel: String, key: String) = call(
        "POST", "/api/v1/contents/${safeR09Id(id)}/contacts/${safeR09Channel(channel)}/access",
        accessToken, ContactAccessResource.serializer(), requireR09Key(key), encode(ContactAccessRequest()), true,
    )

    private suspend fun <T> call(
        method: String, route: String, accessToken: String, serializer: KSerializer<T>,
        idempotencyKey: String? = null, body: String? = null, requireNoStore: Boolean = false,
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
                if (status !in 200..299) return@withContext failure(connection, status, text)
                if (requireNoStore && !connection.getHeaderField("Cache-Control").orEmpty().contains("no-store", true)) {
                    return@withContext R07CallResult.Failure(status, "CLIENT-SENSITIVE-CACHE_POLICY", retryable = false)
                }
                val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), text)
                R07CallResult.Success(envelope.data, envelope.requestId)
            } finally { connection.disconnect() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            R07CallResult.Failure(null)
        }
    }

    private fun failure(connection: HttpURLConnection, status: Int, body: String): R07CallResult.Failure {
        val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()
        return R07CallResult.Failure(
            statusCode = status, errorCode = error?.error?.code, requestId = error?.requestId,
            retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
            fieldErrors = error?.error?.details.orEmpty().associate { it.field to it.message },
            retryable = error?.error?.retryable ?: (status >= 500),
        )
    }

    private fun query(route: String, values: List<Pair<String, String?>>): String {
        val value = values.mapNotNull { (key, item) -> item?.let { "${encode(key)}=${encode(it)}" } }.joinToString("&")
        return if (value.isEmpty()) route else "$route?$value"
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    private inline fun <reified T> encode(value: T) = HhyNetworkJson.value.encodeToString(value)

    private fun validate(request: R09CreateAppRequest) {
        require(request.contentType == "APP")
        require(request.title.isNotBlank() && request.title.length <= 2000)
        require(request.description.isNotBlank() && request.description.length <= 2000)
        require(request.categoryCode.isNotBlank() && request.categoryCode.length <= 2000)
        require(request.mediaIds.size <= 100 && request.contacts.size <= 20)
        request.mediaIds.forEach(::safeR09Id)
    }

    private companion object { val SORTS = setOf("createdAt:desc", "updatedAt:desc", "id:desc") }
}

private fun validateR09Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.fragment == null && uri.query == null)
    return uri.toString().trimEnd('/')
}

private fun safeR09Id(value: String): String = value.also { require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it)) }
private fun safeR09Channel(value: String): String = value.also { require(it in setOf("WECHAT", "PHONE", "QQ", "EMAIL", "LINK", "QR_CODE")) }
private fun requireR09Key(value: String): String = value.also { require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it)) }
