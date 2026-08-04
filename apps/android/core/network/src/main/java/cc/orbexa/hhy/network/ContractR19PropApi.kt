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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

@Serializable
data class R19PropResource(
    val id: String,
    val propType: String,
    val name: String? = null,
    val quantity: Long = 0,
    val status: String,
    val expiresAt: String? = null,
    val configuration: JsonObject? = null,
    val version: Long,
)

@Serializable
data class R19PropPage(val items: List<R19PropResource> = emptyList(), val page: R07PageMeta)

@Serializable
data class R19PropOrderRequest(val skuId: String, val quantity: Long, val paymentChannel: String)

@Serializable
data class R19PropUseRequest(
    val targetContentId: String,
    val scheduledAt: String? = null,
    val expectedVersion: Long,
)

interface ContractR19PropApi {
    suspend fun store(
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
        status: String? = null,
        keyword: String? = null,
        sort: String = "name:asc",
    ): R07CallResult<R19PropPage>

    suspend fun mine(
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
        status: String? = null,
        keyword: String? = null,
        sort: String = "expiresAt:asc",
    ): R07CallResult<R19PropPage>

    suspend fun order(
        accessToken: String,
        request: R19PropOrderRequest,
        idempotencyKey: String,
    ): R07CallResult<CommandResultResource>

    suspend fun use(
        accessToken: String,
        inventoryId: String,
        request: R19PropUseRequest,
        idempotencyKey: String,
    ): R07CallResult<CommandResultResource>
}

class UrlConnectionContractR19PropApi(baseUrl: String) : ContractR19PropApi {
    private val root = validateR19Root(baseUrl)

    override suspend fun store(
        accessToken: String,
        page: Int,
        pageSize: Int,
        status: String?,
        keyword: String?,
        sort: String,
    ) = call(
        "GET",
        buildR19PropPageRoute("/api/v1/props/store", page, pageSize, status, keyword, sort),
        accessToken,
        R19PropPage.serializer(),
    )

    override suspend fun mine(
        accessToken: String,
        page: Int,
        pageSize: Int,
        status: String?,
        keyword: String?,
        sort: String,
    ) = call(
        "GET",
        buildR19PropPageRoute("/api/v1/me/props", page, pageSize, status, keyword, sort),
        accessToken,
        R19PropPage.serializer(),
    )

    override suspend fun order(
        accessToken: String,
        request: R19PropOrderRequest,
        idempotencyKey: String,
    ): R07CallResult<CommandResultResource> {
        requireR19Id(request.skuId)
        require(request.quantity in 1..1_000)
        require(request.paymentChannel in setOf("ALIPAY", "WECHAT_PAY"))
        return call(
            "POST", "/api/v1/props/orders", accessToken, CommandResultResource.serializer(),
            requireR19Key(idempotencyKey), HhyNetworkJson.value.encodeToString(request),
        )
    }

    override suspend fun use(
        accessToken: String,
        inventoryId: String,
        request: R19PropUseRequest,
        idempotencyKey: String,
    ): R07CallResult<CommandResultResource> {
        requireR19Id(inventoryId)
        requireR19Id(request.targetContentId)
        require(request.expectedVersion >= 0)
        require(request.scheduledAt == null || request.scheduledAt.length <= 64)
        return call(
            "POST", "/api/v1/me/props/$inventoryId/use", accessToken,
            CommandResultResource.serializer(), requireR19Key(idempotencyKey),
            HhyNetworkJson.value.encodeToString(request),
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
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) return@withContext failure(connection, status, text)
                val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), text)
                R07CallResult.Success(envelope.data, envelope.requestId, envelope.timestamp)
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

internal fun buildR19PropPageRoute(
    path: String,
    page: Int,
    pageSize: Int,
    status: String?,
    keyword: String?,
    sort: String,
): String {
    require(path in setOf("/api/v1/props/store", "/api/v1/me/props"))
    require(page >= 1)
    require(pageSize in 1..100)
    require(status == null || status.length <= 64)
    require(keyword == null || keyword.length <= 100)
    require(sort.length in 1..64)
    val values = listOfNotNull(
        "page" to page.toString(),
        "pageSize" to pageSize.toString(),
        status?.let { "status" to it },
        keyword?.let { "keyword" to it },
        "sort" to sort,
    )
    return "$path?" + values.joinToString("&") { (name, value) ->
        "$name=${URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")}"
    }
}

private fun validateR19Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.query == null && uri.fragment == null)
    return uri.toString().trimEnd('/')
}

private fun requireR19Id(value: String): String = value.also {
    require(Regex("^[1-9][0-9]{0,18}$").matches(it))
}

private fun requireR19Key(value: String): String = value.also {
    require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it))
}
