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
data class R10CreateGroupRequest(
    val contentType: String = "GROUP_CHAT",
    val title: String,
    val summary: String? = null,
    val description: String,
    val categoryCode: String,
    val regionCode: String? = null,
    val mediaIds: List<String> = emptyList(),
    val contacts: List<R08ContactInput>,
    val attributes: JsonObject,
)

@Serializable
data class R10PatchGroupRequest(
    val title: String,
    val summary: String? = null,
    val description: String,
    val categoryCode: String,
    val regionCode: String? = null,
    val mediaIds: List<String>,
    val contacts: List<R08ContactInput>,
    val attributes: JsonObject,
    val expectedVersion: Long,
)

interface ContractR10Api {
    suspend fun groups(accessToken: String, cursor: String? = null, pageSize: Int = 20, sort: String = "createdAt:desc"): R07CallResult<ContentPageResource>
    suspend fun group(accessToken: String, id: String): R07CallResult<ContentResource>
    suspend fun create(accessToken: String, key: String, request: R10CreateGroupRequest): R07CallResult<ContentResource>
    suspend fun patch(accessToken: String, id: String, key: String, request: R10PatchGroupRequest): R07CallResult<ContentResource>
    suspend fun favorite(accessToken: String, id: String, key: String, request: R08FavoriteRequest): R07CallResult<ContentResource>
    suspend fun share(accessToken: String, id: String, key: String, request: R08ShareRequest): R07CallResult<R08ShareResource>
    suspend fun direct(accessToken: String, key: String, request: R08DirectConversationRequest): R07CallResult<R08ConversationResource>
    suspend fun accessContact(accessToken: String, id: String, channel: String, key: String): R07CallResult<ContactAccessResource>
}

class UrlConnectionContractR10Api(baseUrl: String) : ContractR10Api {
    private val root = validateR10Root(baseUrl)

    override suspend fun groups(accessToken: String, cursor: String?, pageSize: Int, sort: String): R07CallResult<ContentPageResource> {
        require(pageSize in 1..100)
        require(sort in SORTS)
        return call("GET", query("/api/v1/contents", listOf(
            "contentType" to "GROUP_CHAT", "status" to "ONLINE", "cursor" to cursor?.takeIf { it.length <= 256 },
            "pageSize" to pageSize.toString(), "sort" to sort,
        )), accessToken, ContentPageResource.serializer())
    }

    override suspend fun group(accessToken: String, id: String) =
        call("GET", "/api/v1/contents/${safeR10Id(id)}", accessToken, ContentResource.serializer())

    override suspend fun create(accessToken: String, key: String, request: R10CreateGroupRequest): R07CallResult<ContentResource> {
        validate(request)
        return call("POST", "/api/v1/contents", accessToken, ContentResource.serializer(), requireR10Key(key), encode(request))
    }

    override suspend fun patch(accessToken: String, id: String, key: String, request: R10PatchGroupRequest): R07CallResult<ContentResource> {
        require(request.expectedVersion >= 0)
        validateParts(request.title, request.description, request.categoryCode, request.mediaIds, request.contacts)
        return call("PATCH", "/api/v1/contents/${safeR10Id(id)}", accessToken, ContentResource.serializer(), requireR10Key(key), encode(request))
    }

    override suspend fun favorite(accessToken: String, id: String, key: String, request: R08FavoriteRequest) =
        call("POST", "/api/v1/contents/${safeR10Id(id)}/favorite", accessToken, ContentResource.serializer(), requireR10Key(key), encode(request))

    override suspend fun share(accessToken: String, id: String, key: String, request: R08ShareRequest) =
        call("POST", "/api/v1/contents/${safeR10Id(id)}/share", accessToken, R08ShareResource.serializer(), requireR10Key(key), encode(request))

    override suspend fun direct(accessToken: String, key: String, request: R08DirectConversationRequest) =
        call("POST", "/api/v1/conversations/direct", accessToken, R08ConversationResource.serializer(), requireR10Key(key), encode(request))

    override suspend fun accessContact(accessToken: String, id: String, channel: String, key: String) = call(
        "POST", "/api/v1/contents/${safeR10Id(id)}/contacts/${safeR10Channel(channel)}/access",
        accessToken, ContactAccessResource.serializer(), requireR10Key(key), encode(ContactAccessRequest()), true,
    )

    private suspend fun <T> call(
        method: String,
        route: String,
        accessToken: String,
        serializer: KSerializer<T>,
        idempotencyKey: String? = null,
        body: String? = null,
        requireNoStore: Boolean = false,
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
            fieldErrors = error?.error?.fieldErrors().orEmpty(),
            retryable = error?.error?.retryable ?: (status >= 500),
        )
    }

    private fun query(route: String, values: List<Pair<String, String?>>): String {
        val value = values.mapNotNull { (key, item) -> item?.let { "${encode(key)}=${encode(it)}" } }.joinToString("&")
        return if (value.isEmpty()) route else "$route?$value"
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    private inline fun <reified T> encode(value: T) = HhyNetworkJson.value.encodeToString(value)

    private fun validate(request: R10CreateGroupRequest) {
        require(request.contentType == "GROUP_CHAT")
        validateParts(request.title, request.description, request.categoryCode, request.mediaIds, request.contacts)
    }

    private fun validateParts(title: String, description: String, category: String, mediaIds: List<String>, contacts: List<R08ContactInput>) {
        require(title.isNotBlank() && title.length <= 2000)
        require(description.isNotBlank() && description.length <= 2000)
        require(category.isNotBlank() && category.length <= 2000)
        require(mediaIds.size <= 100 && contacts.size <= 20)
        mediaIds.forEach(::safeR10Id)
        contacts.forEach { require(it.channel in CHANNELS && it.value.isNotBlank() && it.value.length <= 2000) }
        require(contacts.any { it.channel in OWNER_CHANNELS })
        require(contacts.count { it.channel == "JOIN_PASSWORD" } <= 1)
    }

    private companion object {
        val SORTS = setOf("createdAt:desc", "updatedAt:desc", "id:desc")
        val OWNER_CHANNELS = setOf("WECHAT", "PHONE", "QQ", "EMAIL")
        val CHANNELS = OWNER_CHANNELS + "JOIN_PASSWORD"
    }
}

private fun validateR10Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.fragment == null && uri.query == null)
    return uri.toString().trimEnd('/')
}

private fun safeR10Id(value: String): String = value.also { require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it)) }
private fun safeR10Channel(value: String): String = value.also { require(it in setOf("WECHAT", "PHONE", "QQ", "EMAIL", "JOIN_PASSWORD")) }
private fun requireR10Key(value: String): String = value.also { require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it)) }
