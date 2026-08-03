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

@Serializable data class R16OrderItemResource(val skuId: String? = null, val itemName: String, val quantity: Int, val unitPriceCent: Long, val subtotalAmountCent: Long)
@Serializable data class R16PriceSnapshotResource(val originalAmountCent: Long, val discountAmountCent: Long, val serviceFeeCent: Long, val payableAmountCent: Long, val ruleVersions: List<String> = emptyList())
@Serializable data class R16NoRefundEvidenceResource(val confirmed: Boolean, val agreementVersion: String, val confirmedAt: String? = null)
@Serializable data class R16OrderResource(
    val orderNo: String, val userId: String, val orderType: String, val status: String, val currency: String,
    val items: List<R16OrderItemResource> = emptyList(), val priceSnapshot: R16PriceSnapshotResource,
    val noRefundEvidence: R16NoRefundEvidenceResource, val paidAmountCent: Long? = null,
    val createdAt: String, val paidAt: String? = null, val version: Long,
)
@Serializable data class R16OrderPage(val items: List<R16OrderResource> = emptyList(), val page: R07PageMeta)

interface ContractR16Api {
    suspend fun orders(accessToken: String, status: String? = null): R07CallResult<R16OrderPage>
    suspend fun order(accessToken: String, orderNo: String): R07CallResult<R16OrderResource>
}

class UrlConnectionContractR16Api(baseUrl: String) : ContractR16Api {
    private val root = URI.create(baseUrl.trimEnd('/')).also {
        require(it.scheme == "https" && !it.host.isNullOrBlank() && !it.host.endsWith(".invalid"))
        require(it.userInfo == null && it.fragment == null && it.query == null)
    }.toString().trimEnd('/')

    override suspend fun orders(accessToken: String, status: String?): R07CallResult<R16OrderPage> = call(
        "/api/v1/me/orders?page=1&pageSize=20&sort=createdAt%3Adesc" + (status?.takeIf { it.isNotBlank() }?.let { "&status=${encode(it)}" } ?: ""),
        accessToken, R16OrderPage.serializer(),
    )

    override suspend fun order(accessToken: String, orderNo: String): R07CallResult<R16OrderResource> = call(
        "/api/v1/me/orders/${safe(orderNo)}", accessToken, R16OrderResource.serializer(),
    )

    private suspend fun <T> call(path: String, accessToken: String, serializer: KSerializer<T>): R07CallResult<T> = withContext(Dispatchers.IO) {
        try {
            val c = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                c.requestMethod = "GET"; c.connectTimeout = 8_000; c.readTimeout = 15_000
                c.setRequestProperty("Accept", "application/json")
                c.setRequestProperty("Authorization", "Bearer $accessToken")
                c.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                val status = c.responseCode
                val body = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()
                    return@withContext R07CallResult.Failure(status, error?.error?.code, error?.requestId, retryable = status >= 500)
                }
                val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), body)
                R07CallResult.Success(envelope.data, envelope.requestId, envelope.timestamp)
            } finally { c.disconnect() }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { R07CallResult.Failure(null) }
    }
    private fun safe(value: String) = value.also { require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it)) }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
