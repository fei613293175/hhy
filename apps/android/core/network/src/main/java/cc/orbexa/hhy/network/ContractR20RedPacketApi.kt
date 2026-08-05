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
data class R20RedPacketCampaignResource(
    val id: String,
    val contentId: String,
    val ownerUserId: String? = null,
    val status: String,
    val totalCount: Long = 0,
    val remainingCount: Long = 0,
    val amountPerClaimCent: Long = 0,
    val principalCent: Long = 0,
    val serviceFeeCent: Long = 0,
    val startAt: String? = null,
    val endAt: String? = null,
    val version: Long = 0,
)

@Serializable
data class R20RedPacketPage(
    val items: List<R20RedPacketCampaignResource> = emptyList(),
    val page: R07PageMeta,
)

@Serializable
data class R20CreateRedPacketRequest(
    val contentId: String,
    val totalCount: Long,
    val amountPerClaimCent: Long,
    val startAt: String,
    val endAt: String,
    val targeting: JsonObject? = null,
)

@Serializable
data class R20PatchRedPacketRequest(
    val totalCount: Long? = null,
    val amountPerClaimCent: Long? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val targeting: JsonObject? = null,
    val expectedVersion: Long,
)

@Serializable
data class R20SubmitReviewRequest(val expectedVersion: Long, val remark: String? = null)

@Serializable
data class R20QuoteRequest(
    val totalCount: Long,
    val amountPerClaimCent: Long,
    val expectedVersion: Long,
)

@Serializable
data class R20OrderRequest(
    val quoteId: String,
    val paymentChannel: String,
    val expectedVersion: Long,
)

@Serializable
data class R20LifecycleRequest(
    val reason: String? = null,
    val expectedVersion: Long,
)

@Serializable
data class R20IncreaseQuoteRequest(
    val newAmountPerClaimCent: Long,
    val expectedVersion: Long,
)

@Serializable
data class R20IncreaseOrderRequest(
    val quoteId: String,
    val paymentChannel: String,
    val expectedVersion: Long,
)

interface ContractR20RedPacketApi {
    suspend fun campaigns(
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
        status: String? = null,
        keyword: String? = null,
        sort: String = "createdAt:desc",
    ): R07CallResult<R20RedPacketPage>

    suspend fun campaign(accessToken: String, id: String): R07CallResult<R20RedPacketCampaignResource>
    suspend fun create(accessToken: String, body: R20CreateRedPacketRequest, idempotencyKey: String): R07CallResult<R20RedPacketCampaignResource>
    suspend fun patch(accessToken: String, id: String, body: R20PatchRedPacketRequest, idempotencyKey: String): R07CallResult<R20RedPacketCampaignResource>
    suspend fun submitReview(accessToken: String, id: String, body: R20SubmitReviewRequest, idempotencyKey: String): R07CallResult<R20RedPacketCampaignResource>
    suspend fun quote(accessToken: String, id: String, body: R20QuoteRequest, idempotencyKey: String): R07CallResult<CommandResultResource>
    suspend fun order(accessToken: String, id: String, body: R20OrderRequest, idempotencyKey: String): R07CallResult<CommandResultResource>
    suspend fun analytics(accessToken: String, id: String): R07CallResult<R20RedPacketPage> =
        R07CallResult.Failure(501, errorCode = "R21_UNSUPPORTED")
    suspend fun pause(accessToken: String, id: String, body: R20LifecycleRequest, idempotencyKey: String): R07CallResult<R20RedPacketCampaignResource> =
        R07CallResult.Failure(501, errorCode = "R21_UNSUPPORTED")
    suspend fun resume(accessToken: String, id: String, body: R20LifecycleRequest, idempotencyKey: String): R07CallResult<R20RedPacketCampaignResource> =
        R07CallResult.Failure(501, errorCode = "R21_UNSUPPORTED")
    suspend fun close(accessToken: String, id: String, body: R20LifecycleRequest, idempotencyKey: String): R07CallResult<R20RedPacketCampaignResource> =
        R07CallResult.Failure(501, errorCode = "R21_UNSUPPORTED")
    suspend fun increaseQuote(accessToken: String, id: String, body: R20IncreaseQuoteRequest, idempotencyKey: String): R07CallResult<CommandResultResource> =
        R07CallResult.Failure(501, errorCode = "R21_UNSUPPORTED")
    suspend fun increaseOrder(accessToken: String, id: String, body: R20IncreaseOrderRequest, idempotencyKey: String): R07CallResult<CommandResultResource> =
        R07CallResult.Failure(501, errorCode = "R21_UNSUPPORTED")
}

class UrlConnectionContractR20RedPacketApi internal constructor(
    baseUrl: String,
    private val connectionFactory: (URI) -> HttpURLConnection,
) : ContractR20RedPacketApi {
    constructor(baseUrl: String) : this(
        baseUrl,
        { uri -> uri.toURL().openConnection() as HttpURLConnection },
    )

    private val root = validateR20Root(baseUrl)

    override suspend fun campaigns(
        accessToken: String,
        page: Int,
        pageSize: Int,
        status: String?,
        keyword: String?,
        sort: String,
    ) = call(
        "GET",
        buildR20PageRoute("/api/v1/me/red-packet-campaigns", page, pageSize, status, keyword, sort),
        accessToken,
        R20RedPacketPage.serializer(),
    )

    override suspend fun campaign(accessToken: String, id: String) = call(
        "GET",
        "/api/v1/red-packet-campaigns/${requireR20Id(id)}",
        accessToken,
        R20RedPacketCampaignResource.serializer(),
    )

    override suspend fun create(accessToken: String, body: R20CreateRedPacketRequest, idempotencyKey: String) = call(
        "POST", "/api/v1/red-packet-campaigns", accessToken,
        R20RedPacketCampaignResource.serializer(), requireR20Key(idempotencyKey),
        HhyNetworkJson.value.encodeToString(body),
    )

    override suspend fun patch(accessToken: String, id: String, body: R20PatchRedPacketRequest, idempotencyKey: String) = call(
        "PATCH", "/api/v1/red-packet-campaigns/${requireR20Id(id)}", accessToken,
        R20RedPacketCampaignResource.serializer(), requireR20Key(idempotencyKey),
        HhyNetworkJson.value.encodeToString(body),
    )

    override suspend fun submitReview(accessToken: String, id: String, body: R20SubmitReviewRequest, idempotencyKey: String) = call(
        "POST", "/api/v1/red-packet-campaigns/${requireR20Id(id)}/submit-review", accessToken,
        R20RedPacketCampaignResource.serializer(), requireR20Key(idempotencyKey),
        HhyNetworkJson.value.encodeToString(body),
    )

    override suspend fun quote(accessToken: String, id: String, body: R20QuoteRequest, idempotencyKey: String) = call(
        "POST", "/api/v1/red-packet-campaigns/${requireR20Id(id)}/quote", accessToken,
        CommandResultResource.serializer(), requireR20Key(idempotencyKey),
        HhyNetworkJson.value.encodeToString(body),
    )

    override suspend fun order(accessToken: String, id: String, body: R20OrderRequest, idempotencyKey: String) = call(
        "POST", "/api/v1/red-packet-campaigns/${requireR20Id(id)}/orders", accessToken,
        CommandResultResource.serializer(), requireR20Key(idempotencyKey),
        HhyNetworkJson.value.encodeToString(body),
    )

    override suspend fun analytics(accessToken: String, id: String) = call(
        "GET", "/api/v1/me/red-packet-campaigns/${requireR20Id(id)}/analytics", accessToken,
        R20RedPacketPage.serializer(),
    )

    override suspend fun pause(accessToken: String, id: String, body: R20LifecycleRequest, idempotencyKey: String) = lifecycle(
        "pause", accessToken, id, body, idempotencyKey,
    )

    override suspend fun resume(accessToken: String, id: String, body: R20LifecycleRequest, idempotencyKey: String) = lifecycle(
        "resume", accessToken, id, body, idempotencyKey,
    )

    override suspend fun close(accessToken: String, id: String, body: R20LifecycleRequest, idempotencyKey: String) = lifecycle(
        "close", accessToken, id, body, idempotencyKey,
    )

    override suspend fun increaseQuote(accessToken: String, id: String, body: R20IncreaseQuoteRequest, idempotencyKey: String) = call(
        "POST", "/api/v1/red-packet-campaigns/${requireR20Id(id)}/increase-quotes", accessToken,
        CommandResultResource.serializer(), requireR20Key(idempotencyKey),
        HhyNetworkJson.value.encodeToString(body),
    )

    override suspend fun increaseOrder(accessToken: String, id: String, body: R20IncreaseOrderRequest, idempotencyKey: String) = call(
        "POST", "/api/v1/red-packet-campaigns/${requireR20Id(id)}/increase-orders", accessToken,
        CommandResultResource.serializer(), requireR20Key(idempotencyKey),
        HhyNetworkJson.value.encodeToString(body),
    )

    private suspend fun lifecycle(
        action: String,
        accessToken: String,
        id: String,
        body: R20LifecycleRequest,
        idempotencyKey: String,
    ) = call(
        "POST", "/api/v1/red-packet-campaigns/${requireR20Id(id)}/$action", accessToken,
        R20RedPacketCampaignResource.serializer(), requireR20Key(idempotencyKey),
        HhyNetworkJson.value.encodeToString(body),
    )

    private suspend fun <T> call(
        method: String,
        route: String,
        token: String,
        serializer: KSerializer<T>,
        key: String? = null,
        body: String? = null,
    ): R07CallResult<T> = withContext(Dispatchers.IO) {
        try {
            val connection = connectionFactory(URI.create(root + route))
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                key?.let { connection.setRequestProperty("X-Idempotency-Key", it) }
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

internal fun buildR20PageRoute(
    path: String,
    page: Int,
    pageSize: Int,
    status: String?,
    keyword: String?,
    sort: String,
): String {
    require(path == "/api/v1/me/red-packet-campaigns")
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
        val encoded = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
        "$name=$encoded"
    }
}

private fun validateR20Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.query == null && uri.fragment == null)
    return uri.toString().trimEnd('/')
}

private fun requireR20Id(value: String): String = value.also {
    require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it))
}

private fun requireR20Key(value: String): String = value.also {
    require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it))
}
