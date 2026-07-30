package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
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

@Serializable
data class R12ContentStatusRequest(
    val expectedVersion: Long,
    val reason: String? = null,
)

@Serializable
data class R12SubmitContentRequest(
    val expectedVersion: Long,
    val reason: String? = null,
)

internal data class R12SubmitCall(
    val route: String,
    val idempotencyKey: String,
    val body: String,
)

sealed interface R12CopyContentResult {
    data class Content(val resource: ContentResource) : R12CopyContentResult
    data class Command(val command: CommandResultResource) : R12CopyContentResult
}

interface ContractR12Api {
    suspend fun content(accessToken: String, id: String): R07CallResult<ContentResource>
    suspend fun contents(
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
        cursor: String? = null,
        status: String? = null,
        keyword: String? = null,
        sort: String? = null,
        contentType: String? = null,
        categoryCode: String? = null,
        regionCode: String? = null,
    ): R07CallResult<ContentPageResource>
    suspend fun drafts(
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
        cursor: String? = null,
        status: String? = null,
        keyword: String? = null,
        sort: String? = null,
    ): R07CallResult<ContentPageResource>
    suspend fun copy(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12CopyContentRequest,
    ): R07CallResult<R12CopyContentResult>
    suspend fun submit(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12SubmitContentRequest,
    ): R07CallResult<R12CopyContentResult>
    suspend fun online(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12ContentStatusRequest,
    ): R07CallResult<R12CopyContentResult>
    suspend fun offline(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12ContentStatusRequest,
    ): R07CallResult<R12CopyContentResult>
    suspend fun delete(
        accessToken: String,
        id: String,
        expectedVersion: Long,
        idempotencyKey: String,
    ): R07CallResult<CommandResultResource>
    suspend fun reviews(
        accessToken: String,
        id: String,
        page: Int = 1,
        pageSize: Int = 20,
        cursor: String? = null,
        status: String? = null,
        keyword: String? = null,
        sort: String? = null,
        contentType: String? = null,
        categoryCode: String? = null,
        regionCode: String? = null,
    ): R07CallResult<ContentPageResource>
    suspend fun analytics(
        accessToken: String,
        id: String,
        page: Int = 1,
        pageSize: Int = 20,
        cursor: String? = null,
        status: String? = null,
        keyword: String? = null,
        sort: String? = null,
        contentType: String? = null,
        categoryCode: String? = null,
        regionCode: String? = null,
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

    override suspend fun contents(
        accessToken: String,
        page: Int,
        pageSize: Int,
        cursor: String?,
        status: String?,
        keyword: String?,
        sort: String?,
        contentType: String?,
        categoryCode: String?,
        regionCode: String?,
    ) = pageCall(
        path = "/api/v1/me/contents",
        accessToken = accessToken,
        page = page,
        pageSize = pageSize,
        cursor = cursor,
        status = status,
        keyword = keyword,
        sort = sort,
        contentType = contentType,
        categoryCode = categoryCode,
        regionCode = regionCode,
    )

    override suspend fun drafts(
        accessToken: String,
        page: Int,
        pageSize: Int,
        cursor: String?,
        status: String?,
        keyword: String?,
        sort: String?,
    ) = pageCall(
        path = "/api/v1/me/drafts",
        accessToken = accessToken,
        page = page,
        pageSize = pageSize,
        cursor = cursor,
        status = status,
        keyword = keyword,
        sort = sort,
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

    override suspend fun submit(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12SubmitContentRequest,
    ): R07CallResult<R12CopyContentResult> {
        val call = buildR12SubmitCall(id, idempotencyKey, request)
        return copyCall(
            route = call.route,
            accessToken = accessToken,
            idempotencyKey = call.idempotencyKey,
            body = call.body,
        )
    }

    override suspend fun online(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12ContentStatusRequest,
    ): R07CallResult<R12CopyContentResult> = statusCall("online", accessToken, id, idempotencyKey, request)

    override suspend fun offline(
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12ContentStatusRequest,
    ): R07CallResult<R12CopyContentResult> = statusCall("offline", accessToken, id, idempotencyKey, request)

    override suspend fun delete(
        accessToken: String,
        id: String,
        expectedVersion: Long,
        idempotencyKey: String,
    ): R07CallResult<CommandResultResource> {
        require(expectedVersion >= 0)
        return call(
            method = "DELETE",
            route = buildR12DeleteRoute(id, expectedVersion),
            accessToken = accessToken,
            serializer = CommandResultResource.serializer(),
            idempotencyKey = requireR12Key(idempotencyKey),
        )
    }

    override suspend fun reviews(
        accessToken: String,
        id: String,
        page: Int,
        pageSize: Int,
        cursor: String?,
        status: String?,
        keyword: String?,
        sort: String?,
        contentType: String?,
        categoryCode: String?,
        regionCode: String?,
    ) = pageCall(
        path = "/api/v1/contents/${safeR12Id(id)}/reviews",
        accessToken = accessToken,
        page = page,
        pageSize = pageSize,
        cursor = cursor,
        status = status,
        keyword = keyword,
        sort = sort,
        contentType = contentType,
        categoryCode = categoryCode,
        regionCode = regionCode,
    )

    override suspend fun analytics(
        accessToken: String,
        id: String,
        page: Int,
        pageSize: Int,
        cursor: String?,
        status: String?,
        keyword: String?,
        sort: String?,
        contentType: String?,
        categoryCode: String?,
        regionCode: String?,
    ) = pageCall(
        path = "/api/v1/contents/${safeR12Id(id)}/analytics",
        accessToken = accessToken,
        page = page,
        pageSize = pageSize,
        cursor = cursor,
        status = status,
        keyword = keyword,
        sort = sort,
        contentType = contentType,
        categoryCode = categoryCode,
        regionCode = regionCode,
    )

    private suspend fun pageCall(
        path: String,
        accessToken: String,
        page: Int,
        pageSize: Int,
        cursor: String? = null,
        status: String? = null,
        keyword: String? = null,
        sort: String? = null,
        contentType: String? = null,
        categoryCode: String? = null,
        regionCode: String? = null,
    ): R07CallResult<ContentPageResource> = call(
        method = "GET",
        route = buildR12PageRoute(
            path,
            page,
            pageSize,
            cursor,
            status,
            keyword,
            sort,
            contentType,
            categoryCode,
            regionCode,
        ),
        accessToken = accessToken,
        serializer = ContentPageResource.serializer(),
    )

    private suspend fun statusCall(
        action: String,
        accessToken: String,
        id: String,
        idempotencyKey: String,
        request: R12ContentStatusRequest,
    ): R07CallResult<R12CopyContentResult> {
        require(action == "online" || action == "offline")
        require(request.expectedVersion >= 0)
        require(request.reason == null || request.reason.length <= 2000)
        return copyCall(
            route = "/api/v1/contents/${safeR12Id(id)}/$action",
            accessToken = accessToken,
            idempotencyKey = requireR12Key(idempotencyKey),
            body = HhyNetworkJson.value.encodeToString(request),
        )
    }

    private suspend fun <T> call(
        method: String,
        route: String,
        accessToken: String,
        serializer: KSerializer<T>,
        idempotencyKey: String? = null,
    ): R07CallResult<T> = execute(method, route, accessToken, idempotencyKey) { _, text ->
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
            fieldErrors = error?.error?.fieldErrors().orEmpty(),
            retryable = error?.error?.retryable ?: (status >= 500),
        )
    }
}

internal fun buildR12PageRoute(
    path: String,
    page: Int,
    pageSize: Int,
    cursor: String? = null,
    status: String? = null,
    keyword: String? = null,
    sort: String? = null,
    contentType: String? = null,
    categoryCode: String? = null,
    regionCode: String? = null,
): String {
    require(path.startsWith("/api/v1/"))
    require(page >= 1)
    require(pageSize in 1..100)
    require(cursor == null || cursor.length <= 256)
    require(status == null || status.length <= 64)
    require(keyword == null || keyword.length <= 100)
    require(sort == null || sort.length <= 64)
    require(contentType == null || contentType in setOf("PROJECT", "APP", "GROUP_CHAT", "TEAM_LEADER"))
    require(categoryCode == null || categoryCode.length <= 64)
    require(regionCode == null || regionCode.length <= 32)
    val values = listOfNotNull(
        "page" to page.toString(),
        "pageSize" to pageSize.toString(),
        cursor?.let { "cursor" to it },
        status?.let { "status" to it },
        keyword?.let { "keyword" to it },
        sort?.let { "sort" to it },
        contentType?.let { "contentType" to it },
        categoryCode?.let { "categoryCode" to it },
        regionCode?.let { "regionCode" to it },
    )
    return "$path?" + values.joinToString("&") { (name, value) -> "$name=${encodeR12Query(value)}" }
}

internal fun buildR12DeleteRoute(id: String, expectedVersion: Long): String {
    require(expectedVersion >= 0)
    return "/api/v1/contents/${safeR12Id(id)}?expectedVersion=$expectedVersion"
}

internal fun buildR12SubmitCall(
    id: String,
    idempotencyKey: String,
    request: R12SubmitContentRequest,
): R12SubmitCall {
    require(request.expectedVersion >= 0)
    require(request.reason == null || request.reason.length <= 2000)
    return R12SubmitCall(
        route = "/api/v1/contents/${safeR12Id(id)}/submit",
        idempotencyKey = requireR12Key(idempotencyKey),
        body = HhyNetworkJson.value.encodeToString(request),
    )
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

private fun encodeR12Query(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
